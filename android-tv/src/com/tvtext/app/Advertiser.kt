package com.tvtext.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper

class Advertiser(context: Context, private val deviceId: String) {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val serviceName = run {
        val model = Build.MODEL.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(28).ifEmpty { "Google TV" }
        val androidMajor = Build.VERSION.RELEASE.takeWhile { it.isDigit() }.ifEmpty { Build.VERSION.SDK_INT.toString() }
        "$model Android $androidMajor ${deviceId.take(6)}"
    }
    private var active: NsdManager.RegistrationListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private var desiredPort = 0
    private var retryMillis = 1000L
    private val retry = Runnable { synchronized(this) { if (desiredPort > 0 && active == null) register(desiredPort) } }
    @Volatile var name = ""; private set
    @Volatile var warning = ""; private set
    @Synchronized fun start(portNumber: Int) {
        stop()
        desiredPort = portNumber; retryMillis = 1000
        register(portNumber)
    }
    private fun retryRegistration() {
        name = ""; active = null
        warning = "Повторяем автоматическое обнаружение…"
        if (desiredPort > 0) { handler.removeCallbacks(retry); handler.postDelayed(retry, retryMillis); retryMillis = minOf(retryMillis * 2, 30000) }
    }
    private fun register(portNumber: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = this@Advertiser.serviceName
            serviceType = "_tvtext._tcp."
            port = portNumber
            setAttribute("id", deviceId)
            setAttribute("v", "1")
            setAttribute("ipv4-only", "1")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(service: NsdServiceInfo) {
                synchronized(this@Advertiser) { if (active === this) { name = service.serviceName; warning = ""; retryMillis = 1000 } }
            }
            override fun onRegistrationFailed(service: NsdServiceInfo, code: Int) {
                synchronized(this@Advertiser) { if (active === this) retryRegistration() }
            }
            override fun onServiceUnregistered(service: NsdServiceInfo) { }
            override fun onUnregistrationFailed(service: NsdServiceInfo, code: Int) { }
        }
        active = listener
        try { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
        catch (_: Exception) { retryRegistration() }
    }
    @Synchronized fun stop() {
        desiredPort = 0; handler.removeCallbacks(retry)
        val previous = active; active = null; name = ""; warning = ""
        if (previous != null) try { manager.unregisterService(previous) } catch (_: Exception) { }
    }
}
