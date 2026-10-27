package local.tvtext.mobiletests

import com.tvtext.mobile.app.*
import java.io.IOException
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

object SenderControllerTests {
    private class Worker : AbstractExecutorService() {
        val queued = ArrayDeque<Runnable>()
        private var stopped = false
        override fun execute(command: Runnable) { check(!stopped); queued.add(command) }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> { stopped = true; return queued.toMutableList().also { queued.clear() } }
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped && queued.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
    }
    private class Discovery : SenderController.Discovery {
        override val lastError = "Fixture discovery error"
        override var resolutionBusy = false
        var starts = 0
        var found: (Tv) -> Unit = {}
        var done: (Boolean) -> Unit = {}
        var onStop: () -> Unit = {}
        override fun start(foundCallback: (Tv) -> Unit, doneCallback: (Boolean) -> Unit) { starts++; found = foundCallback; done = doneCallback }
        override fun stop() { onStop() }
    }
    private class Network : SenderController.Transport {
        var error: Exception? = null
        var code = 0
        var operation: Operation? = null
        val commands = mutableListOf<Int>()
        val destinations = mutableListOf<Tv>()
        val payloads = mutableListOf<String>()
        override fun probe(tv: Tv, operation: Operation) = tv.fingerprint
        override fun exchange(tv: Tv, command: Int, credential: String, id: String, text: String, operation: Operation): Reply {
            this.operation = operation; commands.add(command); destinations.add(tv); payloads.add(text)
            error?.let { throw it }
            return Reply(code, true, "fixture", "c".repeat(64), "fixture.editor", tv.id)
        }
    }
    private class Fixture {
        val tv = Tv(UUID.randomUUID().toString(), "TV", Endpoint("127.0.0.1"), token="a".repeat(64), fingerprint="b".repeat(64))
        var failSave = false
        val catalog = ConnectionCatalog { _,_ -> if(failSave) throw IOException("Fixture disk failure") }.apply { restore(mapOf(tv.id to tv), emptyMap()) }
        val worker = Worker(); val posted = ArrayDeque<() -> Unit>(); val discovery = Discovery(); val network = Network()
        val notices = mutableListOf<SenderController.Notice>(); val updates = mutableListOf<Boolean>()
        val controller = SenderController(catalog, discovery, worker, { posted.add(it) }, { updates.add(it) }, { notices.add(it) }, network)
        fun complete() { worker.queued.remove().run(); flush() }
        fun flush() { while(posted.isNotEmpty())posted.remove().invoke() }
    }
    fun run() {
        for (scenario in listOf("complete", "cancel", "replace", "destroy")) Fixture().apply {
            val second=tv.copy(id=UUID.randomUUID().toString(),name="B",endpoint=Endpoint("127.0.0.2"))
            catalog.pair(second);catalog.invalidate(tv.id)
            controller.scan(revalidateId=tv.id)
            discovery.found(tv.copy(endpoint=Endpoint("127.0.0.3")))
            var sent=false
            controller.send(second,"draft B") { sent=true }
            discovery.done(true)
            check(worker.queued.size==1) { "Verification waits until the current send completes" }
            when(scenario) {
                "cancel" -> controller.stopDiscovery()
                "replace" -> catalog.pair(tv.copy(token="e".repeat(64)))
                "destroy" -> controller.destroy()
            }
            if(scenario!="destroy") {
                complete();check(sent&&network.commands==listOf(3))
                if(scenario=="complete") {
                    check(controller.busy&&worker.queued.size==1)
                    complete()
                    check(catalog.isPaired(catalog.choices().first{it.id==tv.id})&&catalog.profile(tv.id)?.endpoint==Endpoint("127.0.0.3"))
                    check(network.commands==listOf(3,2)&&network.destinations.last().id==tv.id&&network.payloads.last().isEmpty())
                } else check(worker.queued.isEmpty()) { "Cancelled or replaced verification cannot run" }
            } else { flush();check(network.commands.isEmpty()&&!sent) }
            controller.destroy()
        }
        Fixture().apply {
            var current = "original"; val sent = current
            controller.send(tv,sent) { if(current==sent)current="" }
            check(controller.busy&&controller.sending)
            current="new draft"; complete()
            check(current=="new draft"&&!controller.busy&&!controller.sending&&network.commands==listOf(3))
            network.code=4
            controller.send(tv,current) { error("Rejected send cannot clear a draft") }; complete()
            check(notices.last().key=="no_field"&&discovery.starts==0)
            controller.destroy()
        }
        Fixture().apply {
            network.error=IOException("Fixture offline")
            controller.send(tv,"keep") { error("Unconfirmed send cannot clear draft") }; complete()
            check(notices.last().key=="uncertain"&&network.commands==listOf(3)&&controller.scanning)
            discovery.found(tv.copy(endpoint=Endpoint("127.0.0.2"))); discovery.done(true)
            check(controller.busy&&network.commands==listOf(3))
            network.error=null; complete()
            check(notices.last().key=="address_updated"&&network.commands==listOf(3,2)&&network.payloads.last().isEmpty())
            check(catalog.profile(tv.id)?.endpoint==Endpoint("127.0.0.2"))
            catalog.discover(tv.copy(endpoint=Endpoint("127.0.0.3")));failSave=true
            var cleared=false
            controller.send(catalog.choices().single(),"received") { cleared=true }; complete()
            check(cleared&&notices.last().key=="storage_error"&&catalog.profile(tv.id)?.endpoint==Endpoint("127.0.0.2"))
            controller.destroy()
        }
        Fixture().apply {
            network.error=PinMismatch()
            controller.send(tv,"keep") { error("Wrong certificate cannot confirm delivery") }; complete()
            check(!catalog.isPaired(tv)&&discovery.starts==1&&notices.last().key=="need_pairing")
            discovery.found(tv.copy(endpoint=Endpoint("127.0.0.2"),token="e".repeat(64),fingerprint="f".repeat(64)))
            discovery.done(true); network.error=null;complete()
            check(catalog.isPaired(catalog.choices().single())&&catalog.profile(tv.id)?.endpoint==Endpoint("127.0.0.2"))
            check(network.commands==listOf(3,2)&&network.payloads.last().isEmpty()) { "Recovery must only send STATUS, never repeat INSERT" }
            check(network.destinations.last().token==tv.token&&network.destinations.last().fingerprint==tv.fingerprint) { "Discovery cannot replace saved credentials" }
            check(notices.last().key=="address_updated")
            controller.destroy()
        }
        Fixture().apply {
            network.error=PinMismatch()
            controller.send(tv,"keep") { error("Wrong certificate cannot confirm delivery") };complete()
            discovery.done(true) // TV is still offline; a later manual refresh finds it.
            check(!catalog.isPaired(tv)&&!controller.busy)
            controller.scan(revalidateId=tv.id)
            discovery.found(tv.copy(endpoint=Endpoint("127.0.0.3")));discovery.done(true)
            network.error=null;complete()
            check(catalog.isPaired(catalog.choices().single())&&network.commands==listOf(3,2))
            controller.destroy()
        }
        Fixture().apply {
            network.error=PinMismatch();controller.send(tv,"keep") {};complete()
            discovery.found(tv.copy(endpoint=Endpoint("127.0.0.2")));discovery.done(true);complete()
            check(!catalog.isPaired(tv)&&catalog.profile(tv.id)?.endpoint==tv.endpoint) { "An untrusted discovered endpoint cannot restore pairing" }
            network.error=null;network.code=1
            controller.scan(revalidateId=tv.id);discovery.found(tv.copy(endpoint=Endpoint("127.0.0.2")));discovery.done(true);complete()
            check(!catalog.isPaired(tv)&&catalog.profile(tv.id)?.endpoint==tv.endpoint) { "A revoked token cannot restore pairing" }
            controller.destroy()
        }
        Fixture().apply {
            catalog.invalidate(tv.id)
            controller.scan(revalidateId=tv.id);discovery.found(tv.copy(endpoint=Endpoint("127.0.0.2")));discovery.done(true)
            controller.stopDiscovery();complete()
            check(!catalog.isPaired(tv)&&catalog.profile(tv.id)?.endpoint==tv.endpoint) { "Cancelled verification cannot change device state" }
            controller.destroy()
        }
        Fixture().apply {
            controller.scan();val oldDone=discovery.done
            network.error=PinMismatch();controller.send(tv,"keep") {};complete()
            check(discovery.starts==2&&controller.scanning)
            oldDone(true);check(worker.queued.isEmpty()) { "Old scan cannot launch recovery verification" }
            discovery.found(tv.copy(endpoint=Endpoint("127.0.0.2")));discovery.done(true)
            controller.destroy();flush()
            check(network.commands==listOf(3)) { "Destruction cancels queued recovery verification" }
        }
        Fixture().apply {
            controller.scan(); val oldFound=discovery.found; val oldDone=discovery.done
            controller.stopDiscovery(); controller.scan()
            val stranger=tv.copy(id=UUID.randomUUID().toString(),token="",fingerprint="")
            oldFound(stranger); oldDone(false)
            check(catalog.choices().size==1&&controller.scanning&&notices.isEmpty())
            discovery.found(stranger); discovery.done(true)
            check(catalog.choices().size==2&&!controller.scanning)
            controller.destroy()
        }
        Fixture().apply {
            controller.scan(); discovery.resolutionBusy=true; discovery.done(false)
            check(notices.last().key=="search_busy"&&notices.last().detail==null)
            controller.destroy()
        }
        Fixture().apply {
            val unpaired=tv.copy(id=UUID.randomUUID().toString(),token="",fingerprint="")
            catalog.discover(unpaired)
            controller.scan(preserveExisting=true)
            check(catalog.choices().any { it.id == unpaired.id }) { "Resuming an interrupted scan must retain already discovered TVs" }
            discovery.done(true)
            controller.scan()
            check(catalog.choices().none { it.id == unpaired.id }) { "A fresh manual scan must still clear stale discoveries" }
            controller.destroy()
        }
        Fixture().apply {
            var confirmations=0
            controller.send(tv,"pending") { confirmations++ }
            worker.queued.remove().run() // A response is queued for UI dispatch.
            controller.destroy(); val count=updates.size; flush()
            check(network.operation?.cancelled==true&&confirmations==0&&updates.size==count&&notices.isEmpty())
        }
        Fixture().apply {
            controller.send(tv,"not started") { error("Destroyed request reached UI") }
            controller.destroy(); check(worker.queued.isEmpty()&&network.commands.isEmpty())
        }
        Fixture().apply {
            failSave=true; var shown=false
            controller.unpair(tv) { shown=true }; complete()
            check(catalog.isPaired(tv)&&notices.last().key=="storage_error"&&!shown&&discovery.starts==0)
            failSave=false; network.error=IOException("Fixture remote offline")
            controller.unpair(tv) { shown=true }; complete()
            check(catalog.profile(tv.id)==null&&shown&&controller.scanning)
            controller.destroy()
        }
        Fixture().apply {
            var observed="";var paired=false
            controller.probe(tv) { observed=it }; complete()
            check(observed==tv.fingerprint&&network.commands.isEmpty())
            controller.pair(tv,"12345678",observed) { paired=true }; complete()
            check(paired&&catalog.profile(tv.id)?.token=="c".repeat(64)&&network.commands==listOf(1))
            controller.destroy()
        }
        Fixture().apply {
            controller.scan()
            var confirmations=0;var stops=0
            controller.send(tv,"dequeued but not started") { confirmations++ }
            val waiting=worker.queued.remove()
            discovery.onStop = {
                stops++
                // A worker resumes while the UI waits for discovery shutdown.
                // The task has left the executor queue, so shutdownNow alone
                // cannot prevent it from starting: Operation must be cancelled.
                val background=Thread { waiting.run() }
                background.start();background.join(3000)
                check(!background.isAlive) { "Shutdown regression worker timed out" }
            }
            controller.destroy();flush()
            check(network.commands.isEmpty()&&confirmations==0&&notices.isEmpty()) {
                "Queued transport started while stopping discovery: ${network.commands}"
            }
            check(controller.destroyed&&!controller.busy&&!controller.sending&&worker.isShutdown)
            controller.destroy();check(stops==1) { "Destruction must be idempotent" }
        }
        Fixture().apply {
            controller.send(tv,"pending during stop failure") { error("Destroyed request reached UI") }
            val waiting=worker.queued.remove()
            discovery.onStop = { throw IOException("Fixture discovery-stop failure") }
            check(runCatching { controller.destroy() }.isFailure)
            waiting.run();flush()
            check(worker.isShutdown&&network.commands.isEmpty()&&notices.isEmpty()) {
                "Discovery-stop failure left the worker or request active"
            }
        }
        println("PASS: independent mobile scenarios: draft preservation, no replay, address/save errors, pin rejection, stale discovery, destroyed callbacks, cancellation before delayed/failed discovery shutdown, idempotent destruction, unpair and confirmed pairing")
    }
}
