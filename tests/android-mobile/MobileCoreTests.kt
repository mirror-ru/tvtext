package local.tvtext.mobiletests

import com.tvtext.app.Protocol
import com.tvtext.mobile.app.*
import java.io.*
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object MobileCoreTests {
    @JvmStatic fun main(args: Array<String>) {
        ConnectionCatalogTests.run()
        SenderControllerTests.run()
        val sample="Привет с телефона 😀 中文 & %\nВторая строка"
        val id=UUID.randomUUID().toString()
        check(Protocol.readRequest(ByteArrayInputStream(Wire.request(3,"a".repeat(64),id,sample))) == Protocol.Request(3,"a".repeat(64),id,sample))
        val response=ByteArrayOutputStream()
        Protocol.writeResponse(response,Protocol.Response(0,sample,true,"b".repeat(64),"app.test",id))
        check(Wire.reply(ByteArrayInputStream(response.toByteArray())) == Reply(0,true,sample,"b".repeat(64),"app.test",id))
        fun rejects(block:()->Unit) { check(runCatching(block).isFailure) }
        rejects { Wire.request(3,"a","b","Ж".repeat(16385)) }
        rejects { Wire.request(3,"a","b","\ud800") }
        rejects { Wire.reply(ByteArrayInputStream(byteArrayOf(0,1,0,1))) }
        val invalid=response.toByteArray().also { it[6]=2 }; rejects { Wire.reply(ByteArrayInputStream(invalid)) }
        rejects { Endpoint("example.org") }; rejects { Endpoint("127.0.0.1",0) }
        check(Endpoint("::1").host=="::1")
        check(Wire.validName("Гостиная 😀") && !Wire.validName("one\ntwo") && !Wire.validName(" "))
        val context=PhoneFixture.context(FileInputStream(args[0]))
        PhoneFixture(context,UUID.randomUUID().toString()).use { first ->
            PhoneFixture(context,UUID.randomUUID().toString()).use { second ->
                fun pair(fixture: PhoneFixture): Tv {
                    val endpoint=Endpoint("127.0.0.1",fixture.port)
                    val hash=SenderClient.probe(endpoint)
                    val tv=Tv(fixture.id,"Test TV",endpoint,fingerprint=hash)
                    val reply=SenderClient.exchange(tv,1,fixture.pin())
                    check(reply.code==0 && reply.deviceId==tv.id)
                    return tv.copy(token=reply.token)
                }
                val a=pair(first); val b=pair(second)
                check(SenderClient.exchange(a,3,id=id,text=sample).code==0)
                check(SenderClient.exchange(b,3,id=UUID.randomUUID().toString(),text="second").code==0)
                check(first.received==listOf(sample) && second.received==listOf("second"))
                rejects { SenderClient.exchange(a.copy(fingerprint="0".repeat(64)),3,id=UUID.randomUUID().toString(),text="never delivered") }
                check(first.received==listOf(sample))
                rejects { SenderClient.exchange(a.copy(id=UUID.randomUUID().toString()),2) }
                first.ready=false; check(SenderClient.exchange(a,3,id=UUID.randomUUID().toString(),text="no replay").code==4)
                first.ready=true; check(first.received==listOf(sample))
                check(SenderClient.exchange(a,4).code==0 && SenderClient.exchange(a,2).code==1)
                check(SenderClient.exchange(b,2).code==0)
            }
        }
        ServerSocket(0).use { stalled ->
            val operation=Operation(); val executor=Executors.newSingleThreadExecutor()
            try {
                val work=executor.submit<Boolean> { runCatching { SenderClient.probe(Endpoint("127.0.0.1",stalled.localPort),operation) }.isFailure }
                Thread.sleep(200); operation.close(); check(work.get(3,TimeUnit.SECONDS))
            } finally { executor.shutdownNow() }
        }
        println("PASS: mobile wire ↔ production TV protocol; strict UTF-8/frame limits; TLS pinning; pairing; independent TVs; identity checks; no replay; revoke; cancellation")
    }
}
