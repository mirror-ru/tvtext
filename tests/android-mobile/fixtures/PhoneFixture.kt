package local.tvtext.mobiletests

import com.tvtext.app.BridgeEngine
import com.tvtext.app.Protocol
import com.tvtext.app.TlsServer
import java.io.InputStream
import java.security.KeyStore
import java.util.Collections
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

class PhoneFixture(context: SSLContext, val id: String) : AutoCloseable {
    val received = Collections.synchronizedList(mutableListOf<String>())
    @Volatile var ready = true
    @Volatile var delay = 0L
    private val engine = BridgeEngine(object : BridgeEngine.Store {
        private var tokens = listOf<String>()
        override fun loadToken() = tokens.firstOrNull().orEmpty()
        override fun saveToken(token: String) { tokens = listOf(token) }
        override fun loadTokens() = tokens
        override fun saveTokens(tokens: List<String>) { this.tokens = tokens }
    }, object : BridgeEngine.Target {
        override fun status(authorized: () -> Boolean) = Protocol.Response(if(authorized()) 0 else 1,"fixture",ready=ready)
        override fun insert(text: String, authorized: () -> Boolean): Protocol.Response {
            if(delay>0) Thread.sleep(delay)
            if(!authorized()) return Protocol.Response(1,"revoked")
            if(!ready) return Protocol.Response(4,"no field")
            received.add(text); return Protocol.Response(0,"accepted",ready=true)
        }
    }, { System.nanoTime()/1000000 },id)
    val server = TlsServer(context,0,engine)
    val port get() = server.localPort
    fun pin() = engine.openPairing()
    override fun close() = server.close()
    companion object {
        fun context(input: InputStream): SSLContext {
            val password="fixture-only".toCharArray()
            val keys=KeyStore.getInstance("PKCS12").apply { input.use { load(it,password) } }
            val manager=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys,password) }
            return SSLContext.getInstance("TLS").apply { init(manager.keyManagers,null,null) }
        }
    }
}
