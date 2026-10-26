package com.tvtext.mobile.app

import java.io.IOException
import java.io.InterruptedIOException
import java.util.UUID
import java.util.concurrent.ExecutorService

/** Sender scenarios and lifetime, independent of Android activities and dialogs.
 * Entry points/notifications run on the host UI dispatcher; only transport work uses the worker. */
class SenderController(
    val catalog: ConnectionCatalog,
    private val discovery: Discovery,
    private val worker: ExecutorService,
    private val post: (() -> Unit) -> Unit,
    private val changed: (Boolean) -> Unit,
    private val notify: (Notice) -> Unit,
    private val transport: Transport = NetworkTransport
) {
    interface Discovery {
        val lastError: String
        val resolutionBusy: Boolean get() = false
        fun start(foundCallback: (Tv) -> Unit, doneCallback: (Boolean) -> Unit)
        fun stop()
    }
    interface Transport {
        fun probe(tv: Tv, operation: Operation): String
        fun exchange(tv: Tv, command: Int, credential: String, id: String, text: String, operation: Operation): Reply
    }
    private object NetworkTransport : Transport {
        override fun probe(tv: Tv, operation: Operation) = SenderClient.probe(tv.endpoint, operation)
        override fun exchange(tv: Tv, command: Int, credential: String, id: String, text: String, operation: Operation) =
            SenderClient.exchange(tv, command, credential, id, text, operation)
    }
    data class Notice(val key: String?, val error: Boolean = false, val target: String? = null, val detail: String? = null)
    var busy = false; private set
    var scanning = false; private set
    var sending = false; private set
    var destroyed = false; private set
    var storageHealthy = true; private set
    private var operation: Operation? = null
    private var scanGeneration = 0

    fun storageFailed() { storageHealthy = false }
    private fun message(key: String?, error: Boolean = false, target: String? = null, detail: String? = null) =
        notify(Notice(key, error, target, detail))
    private fun persist(target: String, action: () -> Unit): Boolean = try {
        check(storageHealthy); action(); true
    } catch (_: Exception) { message("storage_error", true, target); false }

    fun scan(preserveExisting: Boolean = false, completion: (() -> Unit)? = null) {
        if (scanning || busy || destroyed) return
        val generation = ++scanGeneration
        if (!preserveExisting) catalog.beginDiscovery()
        scanning = true; changed(true)
        discovery.start({ tv ->
            if (!destroyed && scanning && generation == scanGeneration) { catalog.discover(tv); changed(true) }
        }, { success ->
            if (!destroyed && scanning && generation == scanGeneration) {
                scanning = false; changed(false)
                if (!success) {
                    if (discovery.resolutionBusy) message("search_busy", true)
                    else message("search_failed", true, detail = discovery.lastError)
                }
                completion?.invoke()
            }
        })
    }
    fun stopDiscovery() {
        ++scanGeneration; discovery.stop(); scanning = false
        if (!destroyed) changed(false)
    }
    fun destroy() {
        if (destroyed) return
        destroyed = true
        // Cancel even a task already taken from the queue before any discovery
        // adapter can block. Late callbacks are rejected by destroyed/operation.
        operation?.close(); operation = null
        busy = false; sending = false
        try { worker.shutdownNow() } finally { stopDiscovery() }
    }
    private fun <T> work(action: (Operation) -> T, done: (T) -> Unit, failed: (Exception) -> Unit) {
        if (busy || destroyed) return
        busy = true
        val task = Operation(); operation = task; changed(false)
        worker.execute {
            val result = runCatching { if (task.cancelled) throw InterruptedIOException("Cancelled"); action(task) }
            post {
                if (!destroyed && operation === task) {
                    busy = false; sending = false; operation = null; changed(false)
                    result.fold(done) { failed(it as? Exception ?: IOException(it)) }
                }
            }
        }
    }
    private fun exchange(tv: Tv, command: Int, operation: Operation, credential: String = tv.token, id: String = "", text: String = "") =
        transport.exchange(tv, command, credential, id, text, operation)
    private fun showError(error: Exception, target: String) = message(when (error) {
        is PinMismatch -> "need_pairing"; is InvalidReply -> "protocol_error"; is IllegalArgumentException -> "too_long"; else -> "offline"
    }, true, target)
    private fun reply(code: Int, target: String) = if (code == 0) message(null, target = target) else message(when (code) {
        1 -> "need_pairing"; 2 -> "pair_closed"; 3 -> "bad_pin"; 4 -> "no_field"; 5 -> "disabled"; 9 -> "uncertain"; else -> "protocol_error"
    }, true, target)

    fun probe(tv: Tv, showFingerprint: (String) -> Unit) {
        if (busy || destroyed) return
        message("pairing", target = tv.id)
        work({ transport.probe(tv, it) }, showFingerprint, { showError(it, tv.id) })
    }
    fun pair(tv: Tv, pin: String, fingerprint: String, paired: () -> Unit) {
        work({ exchange(tv.copy(fingerprint = fingerprint), 1, it, credential = pin) }, { response ->
            if (response.code == 0) {
                if (persist(tv.id) { catalog.pair(tv.copy(token = response.token, fingerprint = fingerprint)) }) {
                    changed(true); message(null, target = tv.id); paired()
                }
            } else reply(response.code, tv.id)
        }, { showError(it, tv.id) })
    }
    fun send(tv: Tv, draft: String, confirmed: () -> Unit) {
        if (busy || destroyed || !catalog.isPaired(tv) || draft.isEmpty()) return
        if (!runCatching { Wire.utf8(draft).size <= Wire.MAX_TEXT }.getOrDefault(false)) { message("too_long", true, tv.id); return }
        sending = true
        work({ exchange(tv, 3, it, id = UUID.randomUUID().toString(), text = draft) }, { response ->
            if (response.code == 1) { catalog.invalidate(tv.id); changed(true) }
            if (response.code == 0) {
                val remembered = persist(tv.id) { catalog.rememberEndpoint(tv) }
                confirmed()
                if (!remembered) return@work
            }
            reply(response.code, tv.id)
        }, { error ->
            when (error) {
                is PinMismatch -> { catalog.invalidate(tv.id); changed(true); showError(error, tv.id) }
                is InvalidReply -> showError(error, tv.id)
                else -> {
                    message("uncertain", true, tv.id)
                    if (!scanning) scan {
                        if (catalog.advertisement(tv.id)?.endpoint?.let { it != tv.endpoint } == true)
                            message("address_updated", true, tv.id)
                    }
                }
            }
        })
    }
    fun rename(tv: Tv, name: String): Boolean {
        if (busy || destroyed) return false
        return persist(tv.id) { catalog.rename(tv.id, name) }.also { if (it) changed(true) }
    }
    fun unpair(tv: Tv, remoteFailure: () -> Unit) {
        work({ runCatching { exchange(tv, 4, it).code in 0..1 }.getOrDefault(false) }, { notified ->
            if (persist(tv.id) { catalog.forget(tv.id) }) {
                changed(true); message(null); scan()
                if (!notified) remoteFailure()
            }
        }, { showError(it, tv.id) })
    }
}
