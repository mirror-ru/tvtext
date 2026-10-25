package com.tvtext.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.view.accessibility.AccessibilityEvent
import java.math.BigInteger
import java.net.Socket
import java.security.*
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.*
import javax.net.ssl.*
import javax.security.auth.x500.X500Principal

class Identity {
    val context: SSLContext
    val fingerprint: String
    init {
        // Authorizations of an existing AndroidKeyStore key cannot be changed.
        // Keep the legacy alias intact and create a TLS-capable identity once.
        val alias = "tvtext-tls-v2"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            generator.initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE, KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setCertificateSubject(X500Principal("CN=TVText Local"))
                .setCertificateSerialNumber(BigInteger(128, SecureRandom()))
                .setCertificateNotBefore(Date(1577836800000L)).setCertificateNotAfter(Date(2524608000000L)).build())
            generator.generateKeyPair()
        }
        val key = store.getKey(alias, null) as PrivateKey
        val cert = store.getCertificate(alias) as X509Certificate
        val manager = object : X509ExtendedKeyManager() {
            override fun getClientAliases(type: String?, issuers: Array<out Principal>?) = null
            override fun chooseClientAlias(types: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = null
            override fun getServerAliases(type: String?, issuers: Array<out Principal>?) = if (type == "RSA") arrayOf("tv") else null
            override fun chooseServerAlias(type: String?, issuers: Array<out Principal>?, socket: Socket?) = if (type == "RSA") "tv" else null
            override fun getCertificateChain(alias: String?) = arrayOf(cert)
            override fun getPrivateKey(alias: String?) = key
        }
        context = SSLContext.getInstance("TLS").apply { init(arrayOf(manager), null, SecureRandom()) }
        fingerprint = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { String.format(Locale.ROOT, "%02X", it.toInt() and 255) }
    }
    companion object {
        fun shortFingerprint(full: String) = if (full.length < 16) "Подготовка…" else full.take(16).chunked(4).joinToString(" ")
    }
}

class BridgeRuntime private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE)
    val deviceId = prefs.getString("device-id", null) ?: UUID.randomUUID().toString().also {
        check(prefs.edit().putString("device-id", it).commit()) { "Cannot save device identity" }
    }
    val advertiser = Advertiser(context, deviceId)
    private val executor = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    private var restartJob: ScheduledFuture<*>? = null
    @Volatile private var restartGeneration = 0L
    private var identity: Identity? = null // Keystore/SSL setup is reused across receiver restarts.
    private var retryMillis = 1000L
    private val main = Handler(Looper.getMainLooper())
    @Volatile var service: TextService? = null; private set
    @Volatile var fingerprint = ""; private set
    @Volatile var error = ""; private set
    // TVText deliberately exposes one transport mode: IPv4. This keeps discovery
    // and the TLS listener aligned and avoids a stale preference re-enabling IPv6.
    val ipv4Only = true
    @Volatile private var server: TlsServer? = null
    val engine = BridgeEngine(object : BridgeEngine.Store {
        override fun loadToken() = prefs.getString("token", "") ?: ""
        override fun saveToken(token: String) { check(prefs.edit().putString("token", token).commit()) { "Cannot save connection" } }
        override fun loadTokens(): List<String> {
            val saved = prefs.getStringSet("tokens", null)
            if (saved != null) return saved.toList()
            val legacy = loadToken()
            return if (legacy.isEmpty()) emptyList() else listOf(legacy)
        }
        override fun saveTokens(tokens: List<String>) {
            val edit = prefs.edit().remove("token")
            if (tokens.isEmpty()) edit.remove("tokens") else edit.putStringSet("tokens", HashSet(tokens))
            check(edit.commit()) { "Cannot save connections" }
        }
    }, object : BridgeEngine.Target {
        override fun status(authorized: () -> Boolean) = onMain(null, authorized)
        override fun insert(text: String, authorized: () -> Boolean) = onMain(text, authorized)
    }, { SystemClock.elapsedRealtime() }, deviceId)
    private fun onMain(text: String?, authorized: () -> Boolean = { true }): Protocol.Response {
        val expectedService = service ?: return Protocol.Response(Protocol.DISABLED, "Включите службу TVText в специальных возможностях ТВ.")
        val expectedGeneration = restartGeneration
        val deadline = SystemClock.elapsedRealtime() + 2000
        val work = FutureTask(Callable {
            if (SystemClock.elapsedRealtime() > deadline || service !== expectedService || restartGeneration != expectedGeneration)
                Protocol.Response(Protocol.DISABLED, "Приём выключен или запрос устарел.")
            else if (!authorized()) Protocol.Response(Protocol.AUTH, "Подключение отозвано на ТВ.")
            else if (SystemClock.elapsedRealtime() > deadline) Protocol.Response(Protocol.DISABLED, "Запрос устарел.")
            else if (text == null) expectedService.status()
            else expectedService.insert(text) {
                SystemClock.elapsedRealtime() <= deadline && service === expectedService &&
                    restartGeneration == expectedGeneration && authorized()
            }
        })
        main.post(work)
        return try { work.get(2500, TimeUnit.MILLISECONDS) } catch (_: Exception) {
            work.cancel(false); main.removeCallbacks(work)
            Protocol.Response(Protocol.UNCERTAIN, "Нет подтверждения от поля. Проверьте текст на ТВ перед повтором.")
        }
    }
    fun attach(value: TextService) { if (service !== value) { service = value; restart() } }
    fun detach(value: TextService) { if (service === value) { service = null; restart() } }
    @Synchronized private fun restart(delayMillis: Long = 0) {
        val generation = ++restartGeneration
        restartJob?.cancel(false)
        restartJob = executor.schedule({ reconcile(generation) }, delayMillis, TimeUnit.MILLISECONDS)
    }
    private fun reconcile(generation: Long) {
        if (generation != restartGeneration) return
        advertiser.stop(); server?.close(); server = null
        error = ""
        if (service == null) return
        try {
            val identity = this.identity ?: Identity().also { this.identity = it }
            fingerprint = identity.fingerprint
            if (generation != restartGeneration) return
            val created = TlsServer(identity.context, Protocol.PORT, engine, ipv4Only = ipv4Only, onFailure = {
                if (generation == restartGeneration) restart(1000)
            })
            if (generation != restartGeneration) { created.close(); return }
            server = created; retryMillis = 1000
            advertiser.start(Protocol.PORT)
        } catch (e: Exception) {
            if (generation != restartGeneration) return
            error = "Не удалось запустить приём (${e.javaClass.simpleName}). Повторяем автоматически…"
            retryMillis = minOf(retryMillis * 2, 30000)
            restart(retryMillis)
        }
    }
    fun listening() = server?.isRunning() == true
    companion object {
        private var instance: BridgeRuntime? = null
        @Synchronized fun get(context: Context): BridgeRuntime = instance ?: BridgeRuntime(context.applicationContext).also { instance = it }
    }
}

class TextService : AccessibilityService() {
    override fun onServiceConnected() { BridgeRuntime.get(this).attach(this) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* No screen contents are read. */ }
    override fun onInterrupt() { }
    override fun onUnbind(intent: android.content.Intent?): Boolean { BridgeRuntime.get(this).detach(this); return super.onUnbind(intent) }
    override fun onDestroy() { BridgeRuntime.get(this).detach(this); super.onDestroy() }
    fun status(): Protocol.Response {
        return if (Build.VERSION.SDK_INT >= 33) Api33InputBridge.status(this) else LegacyInputBridge.status(this)
    }
    fun insert(text: String, allowed: () -> Boolean): Protocol.Response =
        if (Build.VERSION.SDK_INT >= 33) Api33InputBridge.insert(this, text, allowed) else LegacyInputBridge.insert(this, text, allowed)
}
