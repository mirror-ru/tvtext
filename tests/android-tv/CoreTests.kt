package com.tvtext.app

import java.io.*
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.*
import javax.net.ssl.*

object CoreTests {
    @JvmStatic fun main(args: Array<String>) {
        ReceiverStatusTests.run()
        var stored = ""
        var time = 1000L
        var ready = true
        val inserted = mutableListOf<String>()
        val store = object : BridgeEngine.Store {
            override fun loadToken() = stored
            override fun saveToken(token: String) { stored = token }
            override fun saveTokens(tokens: List<String>) { stored = tokens.lastOrNull() ?: "" }
        }
        val target = object : BridgeEngine.Target {
            override fun status(authorized: () -> Boolean) = Protocol.Response(if(authorized()) 0 else Protocol.AUTH, "status", ready = ready)
            override fun insert(text: String, authorized: () -> Boolean): Protocol.Response {
                if (!authorized()) return Protocol.Response(Protocol.AUTH, "revoked")
                if (!ready) return Protocol.Response(Protocol.NO_FIELD, "no field")
                if (text == "UI → черновик") Thread.sleep(120)
                inserted.add(text); return Protocol.Response(0, "accepted", ready = true)
            }
        }
        val engine = BridgeEngine(store, target, { time })
        fun request(command: Int, credential: String, id: String = "", text: String = "") = engine.handle(Protocol.Request(command, credential, id, text))
        check(request(2, "bad").code == Protocol.AUTH)
        check(request(1, "00000000").code == Protocol.PAIR_CLOSED)
        var pin = engine.openPairing()
        repeat(10) { check(request(1, "wrong").code == Protocol.BAD_PIN) }
        check(request(1, pin).code == Protocol.PAIR_CLOSED)
        pin = engine.openPairing(); time += 300001
        check(request(1, pin).code == Protocol.PAIR_CLOSED)
        pin = engine.openPairing()
        val token = request(1, pin).token
        check(token.length == 64 && token == stored)
        check(request(1, pin).code == Protocol.PAIR_CLOSED)
        check(request(2, token).ready)
        val text = "Привет, ТВ! café 中文 😀 & % + \" \\ \nвторая строка"
        val id = UUID.randomUUID().toString()
        check(request(3, token, id, text).code == 0)
        check(request(3, token, id, text).code == 0)
        check(inserted == listOf(text))
        check(request(3, token, id, "different text").code == Protocol.BAD_REQUEST)
        ready = false
        check(request(3, token, UUID.randomUUID().toString(), "lost").code == Protocol.NO_FIELD)
        ready = true
        check(inserted.size == 1) // Reopening a field never replays a failed request.
        check(request(3, token, UUID.randomUUID().toString(), "x".repeat(32769)).code == Protocol.BAD_REQUEST)
        check(request(3, token, "bad-id", "x").code == Protocol.BAD_REQUEST)
        engine.enable(false); check(request(2, token).code == Protocol.DISABLED)
        engine.enable(true)
        val replacement = request(1, engine.openPairing()).token
        check(request(2, token).code == 0)
        check(request(2, replacement).code == 0)
        engine.revokeToken(token); check(request(2, token).code == Protocol.AUTH)
        check(request(2, replacement).code == 0)
        check(request(Protocol.REVOKE, replacement).code == Protocol.OK)
        check(request(2, replacement).code == Protocol.AUTH)
        engine.revoke(); check(request(2, replacement).code == Protocol.AUTH)
        check(BridgeEngine(store, target, { time }).handle(Protocol.Request(2, replacement)).code == Protocol.AUTH)

        val bytes = ByteArrayOutputStream()
        val original = Protocol.Request(3, "a".repeat(64), id, text)
        Protocol.writeRequest(bytes, original)
        check(Protocol.readRequest(ByteArrayInputStream(bytes.toByteArray())) == original)
        val response = Protocol.Response(0, text, true, "b".repeat(64), "app.test")
        bytes.reset(); Protocol.writeResponse(bytes, response)
        check(Protocol.readResponse(ByteArrayInputStream(bytes.toByteArray())) == response)
        fun rejects(body: () -> Unit) { check(runCatching(body).isFailure) }
        rejects { Protocol.readRequest(ByteArrayInputStream(byteArrayOf(0,1,0,1))) }
        rejects { Protocol.readRequest(ByteArrayInputStream(byteArrayOf(0,0,0,2,1))) }
        rejects { Protocol.readRequest(ByteArrayInputStream(byteArrayOf(0,0,0,6,1,3,-1,-1,-1,-1))) }
        rejects { Protocol.readRequest(ByteArrayInputStream(bytes.toByteArray())) }
        println("PASS: pairing expiry, 10-attempt lockout, token rotation/revocation, pause, Unicode, deduplication, no deferred insertion, size limits, framing")
        ReliabilityTests.authorization()

        if (args.isNotEmpty()) {
            // Integration fixture uses the exact production TLS server and engine,
            // substituting only Android's input field and key store.
            val password = "fixture-only".toCharArray()
            val ks = KeyStore.getInstance("PKCS12").apply { FileInputStream(args[0]).use { load(it, password) } }
            val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, password) }
            val context = SSLContext.getInstance("TLS").apply { init(km.keyManagers, null, null) }
            ReliabilityTests.server(context)
            val live = BridgeEngine(store, target, { System.nanoTime() / 1000000 }, "f7e69b4b-f95f-44ef-b5a0-100fab7221e1")
            val livePin = live.openPairing()
            val certificate = ks.getCertificate("fixture").encoded
            val hash = MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { "%02X".format(it.toInt() and 255) }
            val server = TlsServer(context, 45832, live)
            var secondToken = ""
            val secondText = mutableListOf<String>()
            val secondStore = object : BridgeEngine.Store {
                override fun loadToken() = secondToken
                override fun saveToken(token: String) { secondToken = token }
            }
            val secondTarget = object : BridgeEngine.Target {
                override fun status(authorized: () -> Boolean) = Protocol.Response(if(authorized()) 0 else Protocol.AUTH, "ready", ready = true)
                override fun insert(text: String, authorized: () -> Boolean): Protocol.Response {
                    if (!authorized()) return Protocol.Response(Protocol.AUTH, "revoked")
                    secondText.add(text); return Protocol.Response(0, "accepted", ready = true)
                }
            }
            val second = BridgeEngine(secondStore, secondTarget, { System.nanoTime() / 1000000 }, "d4a8b0d9-f16f-41a8-a372-7fb6981b59aa")
            val secondServer = TlsServer(context, 45833, second)
            File(args[1]).writeText(livePin + "\n" + hash + "\n" + second.openPairing())
            println("READY: TLS fixture on 127.0.0.1:45832")
            val finish = File(args[2])
            val deadline = System.currentTimeMillis() + 90000
            while (!finish.exists() && System.currentTimeMillis() < deadline) Thread.sleep(100)
            server.close(); secondServer.close()
            check(inserted.size == 5 && inserted[1] == text && inserted[2] == "C# → Kotlin: Привет 😀 & % + \" \\ \n第二行" && inserted[3] == "UI → гостиная" && inserted[4] == "UI → черновик")
            check(secondText == listOf("UI → спальня"))
            println("PASS: two independent TVs received only their selected UI messages, without broadcast or replay")
        }
    }
}
