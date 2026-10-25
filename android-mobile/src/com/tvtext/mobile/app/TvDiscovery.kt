@file:Suppress("DEPRECATION")
package com.tvtext.mobile.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.util.ArrayDeque

/** A bounded scan, with serialized legacy resolves for Android 9 compatibility. */
class TvDiscovery(context: Context) : SenderController.Discovery {
    override var lastError: String = ""
        private set
    private val manager = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var listener: NsdManager.DiscoveryListener? = null
    private var generation = 0
    private var resolving = false
    private val queue = ArrayDeque<Pair<Int, NsdServiceInfo>>()
    private val seen = hashSetOf<String>()
    private val found = linkedMapOf<String, Tv>()
    private var onFound: (Tv) -> Unit = {}
    private var onDone: (Boolean) -> Unit = {}
    private var expiry: Runnable? = null
    override fun start(foundCallback: (Tv) -> Unit, doneCallback: (Boolean) -> Unit) {
        stop(); generation++; val current = generation
        found.clear(); seen.clear(); lastError="";onFound = foundCallback; onDone = doneCallback
        val active = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { main.post { if (current == generation) { lastError="Android NSD ($code)";finish(false) } } }
            override fun onServiceFound(info: NsdServiceInfo) { main.post {
                if (current == generation && info.serviceType.trimEnd('.') == "_tvtext._tcp" && seen.add(info.serviceName) && seen.size <= 100) {
                    queue.add(current to info); pump()
                }
            } }
        }
        listener = active
        expiry = Runnable { if (current == generation) finish(true) }.also { main.postDelayed(it, 5000) }
        try { manager.discoverServices("_tvtext._tcp.", NsdManager.PROTOCOL_DNS_SD, active) }
        catch (error: Exception) { lastError=error.localizedMessage ?: error.javaClass.simpleName;finish(false) }
    }
    private fun pump() {
        if (resolving || queue.isEmpty()) return
        val (current, info) = queue.removeFirst()
        if (current != generation) { pump(); return }
        resolving = true
        val resolve = object : NsdManager.ResolveListener {
            override fun onResolveFailed(service: NsdServiceInfo, code: Int) { main.post { if(current==generation) { resolving = false;pump() } } }
            override fun onServiceResolved(service: NsdServiceInfo) { main.post {
                if(current!=generation)return@post
                resolving = false
                if (current == generation) runCatching {
                    fun attribute(name: String) = service.attributes[name]?.toString(Charsets.UTF_8).orEmpty()
                    val id = attribute("id").lowercase(java.util.Locale.ROOT)
                    if (!Wire.validId(id) || attribute("v") != "1") return@runCatching
                    val addresses = if (Build.VERSION.SDK_INT >= 34) service.hostAddresses else listOfNotNull(service.host)
                    val address = addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull().takeIf { attribute("ipv4-only") != "1" } ?: return@runCatching
                    val name = service.serviceName.take(160).ifBlank { "Google TV" }
                    val tv = Tv(id, name, Endpoint(address.hostAddress ?: return@runCatching, service.port))
                    found[id] = tv; onFound(tv)
                }
                pump()
            } }
        }
        try { manager.resolveService(info, resolve) } catch (_: Exception) { resolving = false; pump() }
    }
    private fun finish(success: Boolean) { val callback = onDone; stop(); callback(success) }
    override fun stop() {
        generation++; expiry?.let { main.removeCallbacks(it) }; expiry = null; queue.clear();resolving=false
        val previous = listener; listener = null
        if (previous != null) runCatching { manager.stopServiceDiscovery(previous) }
        onFound = {}; onDone = {}
    }
}
