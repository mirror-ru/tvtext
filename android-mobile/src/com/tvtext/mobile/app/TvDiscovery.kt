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

interface NsdBackend {
    fun discover(listener: NsdManager.DiscoveryListener)
    fun stop(listener: NsdManager.DiscoveryListener)
    fun resolve(info: NsdServiceInfo, listener: NsdManager.ResolveListener)
}

private class PlatformNsdBackend(context: Context) : NsdBackend {
    private val manager = context.getSystemService(NsdManager::class.java)
    override fun discover(listener: NsdManager.DiscoveryListener) =
        manager.discoverServices("_tvtext._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
    override fun stop(listener: NsdManager.DiscoveryListener) = manager.stopServiceDiscovery(listener)
    override fun resolve(info: NsdServiceInfo, listener: NsdManager.ResolveListener) = manager.resolveService(info, listener)
}

/** A bounded scan, with serialized legacy resolves for Android 9 compatibility. */
class TvDiscovery(
    context: Context,
    private val backend: NsdBackend = PlatformNsdBackend(context.applicationContext),
    private val scanMillis: Long = 5000
) : SenderController.Discovery {
    override var lastError: String = ""
        private set
    override var resolutionBusy: Boolean = false
        private set
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
    private var retry: Runnable? = null
    private var blockedByActiveResolve = false
    override fun start(foundCallback: (Tv) -> Unit, doneCallback: (Boolean) -> Unit) {
        stop(); generation++; val current = generation
        found.clear(); seen.clear(); lastError="";resolutionBusy=false;onFound = foundCallback; onDone = doneCallback
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
        expiry = Runnable {
            if (current == generation) {
                resolutionBusy = blockedByActiveResolve
                finish(!blockedByActiveResolve)
            }
        }.also { main.postDelayed(it, scanMillis) }
        try { backend.discover(active) }
        catch (error: Exception) { lastError=error.localizedMessage ?: error.javaClass.simpleName;finish(false) }
    }
    private fun pump() {
        if (resolving || queue.isEmpty()) return
        val (current, info) = queue.removeFirst()
        if (current != generation) { pump(); return }
        resolving = true
        resolve(current, info)
    }
    private fun resolve(current: Int, info: NsdServiceInfo) {
        if (current != generation) return
        val resolve = object : NsdManager.ResolveListener {
            override fun onResolveFailed(service: NsdServiceInfo, code: Int) { main.post {
                if (current != generation) return@post
                if (code == NsdManager.FAILURE_ALREADY_ACTIVE) {
                    // Android 9–12 can keep the previous scan's resolve active
                    // after discovery stops. Retry without discarding this TV.
                    blockedByActiveResolve = true
                    retry?.let(main::removeCallbacks)
                    retry = Runnable { retry = null; if (current == generation) resolve(current, info) }
                        .also { main.postDelayed(it, 200) }
                } else {
                    blockedByActiveResolve = false; resolving = false; pump()
                }
            } }
            override fun onServiceResolved(service: NsdServiceInfo) { main.post {
                if(current!=generation)return@post
                blockedByActiveResolve = false; resolving = false
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
        try { backend.resolve(info, resolve) } catch (_: Exception) {
            if (current == generation) { blockedByActiveResolve = false; resolving = false; pump() }
        }
    }
    private fun finish(success: Boolean) { val callback = onDone; stop(); callback(success) }
    override fun stop() {
        generation++; expiry?.let { main.removeCallbacks(it) }; expiry = null
        retry?.let(main::removeCallbacks); retry = null
        queue.clear();resolving=false;blockedByActiveResolve=false
        val previous = listener; listener = null
        if (previous != null) runCatching { backend.stop(previous) }
        onFound = {}; onDone = {}
    }
}
