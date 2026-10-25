package com.tvtext.app

import java.io.*
import java.nio.ByteBuffer
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.*
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

object Protocol {
    const val PORT = 45831
    const val MAX_FRAME = 65536
    const val MAX_TEXT = 32768
    const val PAIR = 1
    const val STATUS = 2
    const val INSERT = 3
    const val REVOKE = 4
    const val OK = 0
    const val AUTH = 1
    const val PAIR_CLOSED = 2
    const val BAD_PIN = 3
    const val NO_FIELD = 4
    const val DISABLED = 5
    const val BAD_REQUEST = 6
    const val UNCERTAIN = 9

    data class Request(val command: Int, val credential: String, val id: String = "", val text: String = "")
    data class Response(val code: Int, val message: String, val ready: Boolean = false, val token: String = "", val target: String = "", val deviceId: String = "")

    fun readRequest(stream: InputStream): Request {
        val input = frame(stream)
        require(input.readUnsignedByte() == 1) { "version" }
        val request = Request(input.readUnsignedByte(), readString(input, 256), readString(input, 128), readString(input, MAX_TEXT))
        require(input.available() == 0) { "trailing bytes" }
        return request
    }
    fun writeRequest(stream: OutputStream, r: Request) = writeFrame(stream) { output ->
        output.writeByte(1); output.writeByte(r.command)
        writeString(output, r.credential); writeString(output, r.id); writeString(output, r.text)
    }
    fun writeResponse(stream: OutputStream, r: Response) = writeFrame(stream) { output ->
        output.writeByte(1); output.writeByte(r.code); output.writeBoolean(r.ready)
        writeString(output, r.message); writeString(output, r.token); writeString(output, r.target); writeString(output, r.deviceId)
    }
    fun readResponse(stream: InputStream): Response {
        val input = frame(stream)
        require(input.readUnsignedByte() == 1) { "version" }
        val code = input.readUnsignedByte()
        val ready = input.readBoolean()
        val result = Response(code, readString(input, 4096), ready, readString(input, 256), readString(input, 1024), readString(input, 128))
        require(input.available() == 0) { "trailing bytes" }
        return result
    }
    private fun frame(stream: InputStream): DataInputStream {
        val input = DataInputStream(stream)
        val size = input.readInt()
        require(size in 2..MAX_FRAME) { "frame size" }
        return DataInputStream(ByteArrayInputStream(ByteArray(size).also { input.readFully(it) }))
    }
    private fun writeFrame(stream: OutputStream, body: (DataOutputStream) -> Unit) {
        val buffer = ByteArrayOutputStream()
        body(DataOutputStream(buffer))
        val bytes = buffer.toByteArray()
        require(bytes.size <= MAX_FRAME) { "frame size" }
        DataOutputStream(stream).apply { writeInt(bytes.size); write(bytes); flush() }
    }
    private fun readString(input: DataInputStream, limit: Int): String {
        val size = input.readInt()
        require(size in 0..limit && size <= input.available()) { "string size" }
        val bytes = ByteArray(size).also { input.readFully(it) }
        return Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
    }
    private fun writeString(output: DataOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        output.writeInt(bytes.size); output.write(bytes)
    }
}

class BridgeEngine(private val store: Store, private val target: Target, private val clock: () -> Long, private val deviceId: String = "") {
    companion object { const val MAX_TOKENS = 100 }
    interface Store {
        fun loadToken(): String
        fun saveToken(token: String)
        fun loadTokens(): List<String> = loadToken().takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()
        fun saveTokens(tokens: List<String>) { saveToken(tokens.firstOrNull() ?: "") }
    }
    interface Target {
        fun status(authorized: () -> Boolean): Protocol.Response
        // Android checks this again on its main thread immediately before dispatching input.
        fun insert(text: String, authorized: () -> Boolean): Protocol.Response
    }
    private val random = SecureRandom()
    private val tokens = linkedSetOf<String>().apply {
        addAll(store.loadTokens().asSequence().filter { it.length == 64 }.take(MAX_TOKENS).toList())
    }
    private var pin = ""
    private var pairUntil = 0L
    private var attempts = 0
    private var enabled = true
    private var generation = 0L
    private val requestId = Regex("[a-zA-Z0-9-]{16,64}")
    private data class Recent(val textHash: ByteArray, val response: Protocol.Response)
    private val recent = linkedMapOf<String, Recent>()

    @Synchronized fun openPairing(): String {
        pin = String.format(Locale.ROOT, "%08d", random.nextInt(100000000))
        attempts = 0; pairUntil = clock() + 300000
        return pin
    }
    @Synchronized fun pairingPin() = if (clock() < pairUntil) pin else ""
    @Synchronized fun pairingSeconds() = maxOf(0, (pairUntil - clock() + 999) / 1000)
    @Synchronized fun paired() = tokens.isNotEmpty()
    @Synchronized fun pairedTokens() = tokens.toList()
    @Synchronized fun enabled() = enabled
    @Synchronized fun enable(value: Boolean) { if (enabled != value) generation++; enabled = value; if (!value) closePairing() }
    @Synchronized fun closePairing() { pin = ""; pairUntil = 0 }
    @Synchronized fun revoke() { store.saveTokens(emptyList()); tokens.clear(); generation++; closePairing() }
    @Synchronized fun revokeToken(value: String) {
        val next = tokens.filterNot { equal(it, value) }
        if (next.size != tokens.size) {
            store.saveTokens(next)
            tokens.clear(); tokens.addAll(next); generation++
        }
    }

    fun handle(r: Protocol.Request, requestAlive: () -> Boolean = { true }): Protocol.Response {
        val acceptedGeneration: Long
        val acceptedCredential: String
        synchronized(this) {
            if (!requestAlive()) return Protocol.Response(Protocol.UNCERTAIN, "Запрос устарел.")
            if (!enabled) return Protocol.Response(Protocol.DISABLED, "Приём на ТВ выключен.")
            if (r.command == Protocol.PAIR) return pair(r.credential)
            if (!tokens.any { equal(it, r.credential) })
                return Protocol.Response(Protocol.AUTH, "Подключите устройство заново: ключ не принят телевизором.", deviceId = deviceId)
            acceptedGeneration = generation
            acceptedCredential = r.credential
        }
        val authorized = { synchronized(this) { requestAlive() && enabled && generation == acceptedGeneration && tokens.any { equal(it, acceptedCredential) } } }
        if (r.command == Protocol.REVOKE) {
            revokeToken(r.credential)
            return Protocol.Response(Protocol.OK, "Устройство отвязано от ТВ.", deviceId = deviceId)
        }
        if (r.command == Protocol.STATUS) return target.status(authorized).copy(deviceId = deviceId)
        if (r.command != Protocol.INSERT || !requestId.matches(r.id) || r.text.isEmpty())
            return Protocol.Response(Protocol.BAD_REQUEST, "Пустой текст, неверная команда или слишком длинная строка.")
        val textBytes = r.text.toByteArray(Charsets.UTF_8)
        if (textBytes.size > Protocol.MAX_TEXT)
            return Protocol.Response(Protocol.BAD_REQUEST, "Пустой текст, неверная команда или слишком длинная строка.")
        // Never hold the engine monitor while waiting for Android's main thread.
        synchronized(recent) {
            val key = r.credential + ":" + r.id
            val textHash = MessageDigest.getInstance("SHA-256").digest(textBytes)
            recent[key]?.let {
                return if (MessageDigest.isEqual(textHash, it.textHash)) it.response
                else Protocol.Response(Protocol.BAD_REQUEST, "Идентификатор запроса уже использован для другого текста.")
            }
            synchronized(this) {
                if (!enabled || generation != acceptedGeneration || !tokens.any { equal(it, acceptedCredential) })
                    return Protocol.Response(Protocol.AUTH, "Подключение отозвано на ТВ.")
            }
            val result = target.insert(r.text, authorized).copy(deviceId = deviceId)
            recent[key] = Recent(textHash, result)
            if (recent.size > 128) recent.remove(recent.keys.first())
            return result
        }
    }
    private fun pair(provided: String): Protocol.Response {
        if (pin.isEmpty() || clock() >= pairUntil || attempts >= 10)
            return Protocol.Response(Protocol.PAIR_CLOSED, "На ТВ нажмите «Подключить новое устройство», затем введите новый код.")
        if (tokens.size >= MAX_TOKENS)
            return Protocol.Response(Protocol.BAD_REQUEST, "На ТВ достигнут лимит подключённых устройств.")
        attempts++
        if (!equal(pin, provided)) {
            if (attempts >= 10) closePairing()
            return Protocol.Response(Protocol.BAD_PIN, "Неверный код подключения.")
        }
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        val replacement = hex(bytes)
        val next = tokens.toMutableList().apply { add(replacement) }
        store.saveTokens(next); tokens.add(replacement); closePairing()
        return Protocol.Response(Protocol.OK, "Устройство подключено. Откройте поле ввода на ТВ.", token = replacement, deviceId = deviceId)
    }
    private fun equal(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    private fun hex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        return buildString(bytes.size * 2) {
            bytes.forEach {
                val value = it.toInt() and 255
                append(digits[value ushr 4]); append(digits[value and 15])
            }
        }
    }
}

class TlsServer(context: SSLContext, port: Int, private val engine: BridgeEngine,
    private val requestTimeoutMillis: Long = 10000, private val onFailure: () -> Unit = {},
    private val ipv4Only: Boolean = false) : AutoCloseable {
    private val socket = (context.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
        reuseAddress = true
        bind(if(ipv4Only) InetSocketAddress(InetAddress.getByAddress(byteArrayOf(0,0,0,0)), port) else InetSocketAddress(port), 8)
        enabledProtocols = arrayOf("TLSv1.2")
        enabledCipherSuites = supportedCipherSuites.filter { it.startsWith("TLS_ECDHE_RSA_WITH_AES_") && it.contains("_GCM_") }.toTypedArray()
    }
    private val clients = ConcurrentHashMap.newKeySet<SSLSocket>()
    private val deadlines = ScheduledThreadPoolExecutor(1, ThreadFactory { task -> Thread(task, "TVText-deadline").apply { isDaemon = true } })
        .apply { removeOnCancelPolicy = true }
    private val workers = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(4),
        ThreadFactory { task -> Thread(task, "TVText-client").apply { isDaemon = true } })
    @Volatile private var closed = false
    val localPort: Int get() = socket.localPort
    internal fun activeClients() = clients.size
    init {
        Thread({
            while (!closed) {
                var accepted: SSLSocket? = null
                try {
                    accepted = socket.accept() as SSLSocket
                    // Some Java providers use a dual-stack socket even for the IPv4 wildcard.
                    if(ipv4Only && accepted.inetAddress !is Inet4Address) { drop(accepted); continue }
                    accepted.soTimeout = 5000; accepted.tcpNoDelay = true; clients.add(accepted)
                    val client = accepted
                    val deadline = deadlines.schedule({ drop(client) }, requestTimeoutMillis, TimeUnit.MILLISECONDS)
                    try { workers.execute { try { serve(client) } finally { deadline.cancel(false) } } }
                    catch (e: RejectedExecutionException) { deadline.cancel(false); throw e }
                } catch (_: RejectedExecutionException) { drop(accepted) }
                catch (_: IOException) { drop(accepted); if (!closed) { close(); onFailure() } }
            }
        }, "TVText-listener").apply { isDaemon = true; start() }
    }
    private fun serve(client: SSLSocket) {
        try {
            client.startHandshake()
            val request = Protocol.readRequest(client.inputStream)
            Protocol.writeResponse(client.outputStream, engine.handle(request) { !closed && !client.isClosed })
        } catch (_: Exception) { /* Never log credentials or input text. */ }
        finally { drop(client) }
    }
    private fun drop(client: SSLSocket?) {
        if (client == null) return
        clients.remove(client); try { client.close() } catch (_: IOException) { }
    }
    fun isRunning() = !closed
    override fun close() {
        closed = true
        try { socket.close() } catch (_: IOException) { }
        clients.forEach { drop(it) }; workers.shutdownNow(); deadlines.shutdownNow()
    }
}
