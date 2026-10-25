package com.tvtext.app

import java.net.Socket
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

object ReliabilityTests {
    private class Store : BridgeEngine.Store {
        var token = "a".repeat(64)
        var fail = false
        override fun loadToken() = token
        override fun saveToken(token: String) { check(!fail) { "simulated storage failure" }; this.token = token }
        override fun saveTokens(tokens: List<String>) { check(!fail) { "simulated storage failure" }; token = tokens.lastOrNull() ?: "" }
    }
    fun authorization() {
        // The target pauses where Android would queue work to its main thread.
        for (change in listOf("revoke", "pause-resume", "replace-key")) {
            val store = Store()
            val queued = CountDownLatch(1); val release = CountDownLatch(1)
            val inserted = AtomicInteger()
            val target = object : BridgeEngine.Target {
                override fun status(authorized: () -> Boolean) = Protocol.Response(0, "ready")
                override fun insert(text: String, authorized: () -> Boolean): Protocol.Response {
                    queued.countDown(); check(release.await(3, TimeUnit.SECONDS))
                    if (!authorized()) return Protocol.Response(Protocol.AUTH, "revoked")
                    inserted.incrementAndGet(); return Protocol.Response(0, "inserted")
                }
            }
            val engine = BridgeEngine(store, target, { 1000L })
            val worker = Executors.newSingleThreadExecutor()
            try {
                val pending = worker.submit<Protocol.Response> { engine.handle(Protocol.Request(3, store.token, UUID.randomUUID().toString(), "queued")) }
                check(queued.await(3, TimeUnit.SECONDS))
                when(change) {
                    "revoke" -> engine.revoke()
                    "pause-resume" -> { engine.enable(false); engine.enable(true) }
                    else -> check(engine.handle(Protocol.Request(1, engine.openPairing())).code == 0)
                }
                release.countDown()
                val result = pending.get(3, TimeUnit.SECONDS)
                if (change == "replace-key") check(result.code == 0 && inserted.get() == 1) { "existing PC must remain valid after another PC pairs" }
                else check(result.code == Protocol.AUTH && inserted.get() == 0) { "stale insertion after $change" }
            } finally { release.countDown(); worker.shutdownNow() }
        }
        val store = Store()
        val inserted = AtomicInteger()
        val target = object : BridgeEngine.Target {
            override fun status(authorized: () -> Boolean) = Protocol.Response(if(authorized()) 0 else Protocol.AUTH, "ready")
            override fun insert(text: String, authorized: () -> Boolean): Protocol.Response {
                if (!authorized()) return Protocol.Response(Protocol.AUTH, "revoked")
                Thread.sleep(20); inserted.incrementAndGet(); return Protocol.Response(0, "inserted")
            }
        }
        val engine = BridgeEngine(store, target, { 1000L })
        val original = store.token; val pin = engine.openPairing(); store.fail = true
        check(runCatching { engine.handle(Protocol.Request(1, pin)) }.isFailure)
        check(engine.handle(Protocol.Request(2, original)).code == 0 && engine.pairingPin() == pin)
        check(runCatching { engine.revoke() }.isFailure)
        check(engine.handle(Protocol.Request(2, original)).code == 0)
        store.fail = false
        val replacement = engine.handle(Protocol.Request(1, pin)).token
        check(replacement != original && replacement == store.token)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val request = Protocol.Request(3, replacement, UUID.randomUUID().toString(), "only once")
            val results = (1..8).map { pool.submit<Protocol.Response> { engine.handle(request) } }
            results.forEach { check(it.get(3, TimeUnit.SECONDS).code == 0) }
            check(inserted.get() == 1)
        } finally { pool.shutdownNow() }
        println("PASS: revoke/pause-resume/key replacement cancel queued input; failed persistence preserves old key; 8 concurrent duplicates insert once")
    }
    fun server(context: SSLContext) {
        val engine = BridgeEngine(Store(), object : BridgeEngine.Target {
            override fun status(authorized: () -> Boolean) = Protocol.Response(0, "ready")
            override fun insert(text: String, authorized: () -> Boolean) = Protocol.Response(0, "inserted")
        }, { 1000L })
        val server = TlsServer(context, 0, engine, requestTimeoutMillis = 500)
        val sockets = mutableListOf<Socket>()
        try {
            repeat(8) { sockets.add(Socket("127.0.0.1", server.localPort)) }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            // Wait until accept has run, then until absolute deadlines free every worker/queue slot.
            Thread.sleep(150)
            while(server.activeClients() > 0 && System.nanoTime() < deadline) Thread.sleep(20)
            check(server.activeClients() == 0 && server.isRunning()) { "stalled TLS clients did not expire" }
        } finally { sockets.forEach { it.close() }; server.close() }
        check(!server.isRunning() && server.activeClients() == 0)
        TlsServer(context, server.localPort, engine).use { check(it.isRunning()) }
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val finished = CountDownLatch(1)
        val inserted = AtomicInteger(); val store = Store()
        val delayed = BridgeEngine(store, object : BridgeEngine.Target {
            override fun status(authorized: () -> Boolean) = Protocol.Response(0, "ready")
            override fun insert(text: String, authorized: () -> Boolean): Protocol.Response {
                entered.countDown(); check(release.await(3, TimeUnit.SECONDS))
                val allowed = authorized(); if(allowed) inserted.incrementAndGet(); finished.countDown()
                return Protocol.Response(if(allowed) 0 else Protocol.AUTH, "expired")
            }
        }, { 1000L })
        val trustedFixture = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(object : X509TrustManager {
                override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
                override fun checkClientTrusted(chain: Array<X509Certificate>, auth: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, auth: String) {}
            }), null)
        }
        TlsServer(context, 0, delayed, requestTimeoutMillis = 1000).use { timed ->
            (trustedFixture.socketFactory.createSocket("127.0.0.1", timed.localPort) as SSLSocket).use { client ->
                client.enabledProtocols = arrayOf("TLSv1.2");client.soTimeout=2000;client.startHandshake()
                Protocol.writeRequest(client.outputStream, Protocol.Request(3, store.token, UUID.randomUUID().toString(), "expired"))
                check(entered.await(2, TimeUnit.SECONDS));Thread.sleep(1100);release.countDown()
                check(finished.await(2, TimeUnit.SECONDS) && inserted.get() == 0) { "expired TLS request inserted later" }
            }
        }
        fun statusFrom(host: String, port: Int): Protocol.Response {
            (trustedFixture.socketFactory.createSocket(host, port) as SSLSocket).use { client ->
                client.enabledProtocols = arrayOf("TLSv1.2"); client.soTimeout = 2000; client.startHandshake()
                Protocol.writeRequest(client.outputStream, Protocol.Request(2, store.token))
                return Protocol.readResponse(client.inputStream)
            }
        }
        TlsServer(context, 0, engine).use { dual -> check(statusFrom("::1", dual.localPort).code == 0) }
        TlsServer(context, 0, engine, ipv4Only = true).use { only ->
            check(statusFrom("127.0.0.1", only.localPort).code == 0)
            check(runCatching { statusFrom("::1", only.localPort) }.isFailure) { "IPv6 accepted in IPv4-only mode" }
        }
        TlsServer(context, 0, engine).use { dual -> check(statusFrom("::1", dual.localPort).code == 0) }
        println("PASS: IPv4-only TLS mode accepts IPv4, rejects IPv6; disabling it restores IPv6 with the same pairing key")
        println("PASS: 8 stalled TLS clients expire by absolute deadline, listener survives, sockets close and port can be rebound")
        println("PASS: an insertion waiting at the target is cancelled when its TLS request deadline expires")
    }
}
