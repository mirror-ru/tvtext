package com.tvtext.mobile.app

import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

data class Endpoint(val host: String, val port: Int = 45831) {
    init {
        require(port in 1..65535 && host.length in 1..128)
        val literal = host.substringBefore('%')
        require(if (':' in literal) literal.matches(Regex("[0-9a-fA-F:]+")) else literal.matches(Regex("[0-9.]+")))
        require(!host.contains('%') || host.substringAfter('%').matches(Regex("[a-zA-Z0-9_.-]+")))
        val address = InetAddress.getByName(host)
        require(address is Inet4Address || address is Inet6Address)
    }
}

data class Tv(val id: String, val name: String, val endpoint: Endpoint, val alias: String = "", val token: String = "", val fingerprint: String = "") {
    val displayName get() = alias.ifEmpty { name }
    val paired get() = token.matches(Regex("[a-fA-F0-9]{64}")) && fingerprint.matches(Regex("[a-fA-F0-9]{64}"))
}

class PinMismatch : SSLException("Certificate or television identity changed")
class InvalidReply : IOException("Invalid TVText response")
data class Reply(val code: Int, val ready: Boolean, val message: String, val token: String, val target: String, val deviceId: String)

object Wire {
    const val MAX_TEXT = 32768
    fun utf8(value: String): ByteArray {
        val buffer = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value))
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }
    fun request(command: Int, credential: String, id: String = "", text: String = ""): ByteArray {
        require(command in 1..4)
        val values = listOf(utf8(credential), utf8(id), utf8(text))
        require(values[0].size <= 256 && values[1].size <= 128 && values[2].size <= MAX_TEXT)
        val body = ByteArrayOutputStream()
        DataOutputStream(body).apply {
            writeByte(1); writeByte(command)
            values.forEach { writeInt(it.size); write(it) }
        }
        return ByteArrayOutputStream().also { DataOutputStream(it).apply { writeInt(body.size()); write(body.toByteArray()) } }.toByteArray()
    }
    fun reply(stream: InputStream): Reply {
        try {
            val length = DataInputStream(stream).readInt()
            if (length !in 3..65536) throw InvalidReply()
            val bytes = ByteArray(length)
            DataInputStream(stream).readFully(bytes)
            val input = DataInputStream(ByteArrayInputStream(bytes))
            if (input.readUnsignedByte() != 1) throw InvalidReply()
            val code = input.readUnsignedByte(); val ready = input.readUnsignedByte()
            if (code !in 0..6 && code != 9 || ready !in 0..1) throw InvalidReply()
            fun string(limit: Int): String {
                val size = input.readInt()
                if (size !in 0..limit || size > input.available()) throw InvalidReply()
                return Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(ByteArray(size).also { input.readFully(it) })).toString()
            }
            val result = Reply(code, ready == 1, string(4096), string(256), string(1024), string(128))
            if (input.available() != 0) throw InvalidReply()
            return result
        } catch (e: InvalidReply) { throw e }
        catch (e: java.nio.charset.CharacterCodingException) { throw InvalidReply() }
    }
    fun hash(certificate: ByteArray) = MessageDigest.getInstance("SHA-256").digest(certificate)
        .joinToString("") { String.format(Locale.ROOT, "%02X", it.toInt() and 255) }
    fun shortFingerprint(value: String) = value.take(16).chunked(4).joinToString(" ")
    fun validName(value: String) = value.trim().length in 1..80 && value.none { it.isISOControl() } && runCatching { utf8(value); true }.getOrDefault(false)
    fun validId(value: String) = runCatching { UUID.fromString(value).toString() == value.lowercase(Locale.ROOT) }.getOrDefault(false)
}

/** One cancellable operation. A watchdog also bounds stalled TLS writes. */
class Operation : AutoCloseable {
    @Volatile private var socket: Socket? = null
    @Volatile var cancelled = false; private set
    @Synchronized fun attach(value: Socket) { if (cancelled) { value.close(); throw InterruptedIOException("Cancelled") }; socket = value }
    @Synchronized override fun close() { cancelled = true; runCatching { socket?.close() }; socket = null }
}

object SenderClient {
    private val deadlines = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "TVText deadline").apply { isDaemon = true } }
    private fun <T> session(endpoint: Endpoint, pin: String?, operation: Operation, block: (SSLSocket, String) -> T): T {
        var observed = ""
        val manager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) { throw java.security.cert.CertificateException() }
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val certificate = chain?.firstOrNull() ?: throw java.security.cert.CertificateException()
                observed = Wire.hash(certificate.encoded)
                if (pin != null && !MessageDigest.isEqual(observed.toByteArray(Charsets.US_ASCII), pin.uppercase(Locale.ROOT).toByteArray(Charsets.US_ASCII)))
                    throw java.security.cert.CertificateException("TVText certificate changed")
            }
        }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), SecureRandom()) }
        val socket = context.socketFactory.createSocket() as SSLSocket
        operation.attach(socket)
        val deadline = deadlines.schedule({ operation.close() }, 12, TimeUnit.SECONDS)
        try {
            socket.use {
                it.tcpNoDelay = true; it.soTimeout = 6000; it.enabledProtocols = arrayOf("TLSv1.2")
                it.connect(InetSocketAddress(endpoint.host, endpoint.port), 4000)
                try { it.startHandshake() } catch (error: SSLException) {
                    if (pin != null && observed.isNotEmpty() && !observed.equals(pin, true)) throw PinMismatch()
                    throw error
                }
                return block(it, observed)
            }
        } finally { deadline.cancel(false) }
    }
    fun probe(endpoint: Endpoint, operation: Operation = Operation()): String = session(endpoint, null, operation) { _, hash -> hash }
    fun exchange(tv: Tv, command: Int, credential: String = tv.token, id: String = "", text: String = "", operation: Operation = Operation()): Reply {
        require(tv.fingerprint.matches(Regex("[a-fA-F0-9]{64}")))
        val bytes = Wire.request(command, credential, id, text)
        return session(tv.endpoint, tv.fingerprint, operation) { socket, _ ->
            socket.outputStream.apply { write(bytes); flush() }
            Wire.reply(socket.inputStream).also {
                if (it.code in 0..1 && it.deviceId != tv.id) throw PinMismatch()
                if (command == 1 && it.code == 0 && !it.token.matches(Regex("[a-fA-F0-9]{64}"))) throw InvalidReply()
            }
        }
    }
}
