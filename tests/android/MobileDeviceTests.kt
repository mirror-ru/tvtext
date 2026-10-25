@file:Suppress("DEPRECATION")
package local.tvtext.mobiletests

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.KeyEvent
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import com.tvtext.mobile.app.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MobileDeviceTests : Instrumentation() {
    private lateinit var activity: MainActivity
    private val registrations=mutableListOf<NsdManager.RegistrationListener>()
    private val initialScans=java.util.IdentityHashMap<MainActivity,Boolean>()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun callActivityOnStart(activity:Activity) {
        super.callActivityOnStart(activity)
        if(activity is MainActivity && !initialScans.containsKey(activity)) {
            val scanning=MainActivity::class.java.getDeclaredField("scanning").apply { isAccessible=true }
            initialScans[activity]=scanning.getBoolean(activity)
        }
    }
    private fun ui(action: ()->Unit) {
        var failure:Throwable?=null
        runOnMainSync { try { action() } catch(error:Throwable) { failure=error } };waitForIdleSync()
        failure?.let { throw it }
    }
    private fun view(tag: String): View = activity.window.decorView.findViewWithTag(tag) ?: error("Missing view: $tag")
    private fun click(tag: String) {
        val point=IntArray(2)
        ui {
            val target=view(tag);check(target.isEnabled)
            target.requestRectangleOnScreen(Rect(0,0,target.width,target.height),true)
        }
        ui { val target=view(tag);target.getLocationOnScreen(point);point[0]+=target.width/2;point[1]+=target.height/2 }
        val start=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,point[0].toFloat(),point[1].toFloat(),0)
            try { sendPointerSync(event) } finally { event.recycle() }
        }
        waitForIdleSync()
    }
    private fun motion(target:View,action:Int,x:Float,y:Float,start:Long) {
        val event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x,y,0)
        try { target.dispatchTouchEvent(event) } finally { event.recycle() }
    }
    private fun field(name: String): Any? = MainActivity::class.java.getDeclaredField(name).apply { isAccessible=true }.get(activity)
    private fun waitUntil(condition:()->Boolean) {
        val deadline=System.currentTimeMillis()+15000
        while(!condition()) { check(System.currentTimeMillis()<deadline) { "Timed out" }; Thread.sleep(30); waitForIdleSync() }
    }
    private fun idle() = waitUntil { field("busy")==false }
    private fun input() = view("text-input") as EditText
    private fun draft(value: String) = ui { input().setText(value) }
    private fun dialog() = field("dialog") as? AlertDialog ?: error("Missing dialog")
    private fun caption(id:String) = view("tv-$id").findViewWithTag<TextView>("pair-label")
    private fun legacyProfiles(values:List<Tv>):ByteArray {
        val clear=java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(clear).use { output ->
            output.writeInt(values.size)
            values.forEach { output.writeUTF(it.id);output.writeUTF(it.name);output.writeUTF(it.endpoint.host);output.writeInt(it.endpoint.port);output.writeUTF(it.alias);output.writeUTF(it.token);output.writeUTF(it.fingerprint) }
        }
        val keys=java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key=keys.getKey("tvtext-mobile-profiles-v1",null) as javax.crypto.SecretKey
        val cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply { init(javax.crypto.Cipher.ENCRYPT_MODE,key) }
        return java.io.ByteArrayOutputStream().also { bytes -> java.io.DataOutputStream(bytes).use { it.writeInt(1);it.writeByte(cipher.iv.size);it.write(cipher.iv);it.write(cipher.doFinal(clear.toByteArray())) } }.toByteArray()
    }
    private fun capture(name:String) {
        ui { activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input().windowToken,0);activity.window.decorView.clearFocus() }
        Thread.sleep(350);waitForIdleSync()
        val image=uiAutomation.takeScreenshot() ?: error("Missing screenshot")
        File(targetContext.getExternalFilesDir(null),name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
    }
    private fun advertise(name: String, fixture: PhoneFixture) {
        val started=CountDownLatch(1)
        val listener=object:NsdManager.RegistrationListener {
            override fun onServiceRegistered(info:NsdServiceInfo) { started.countDown() }
            override fun onRegistrationFailed(info:NsdServiceInfo,code:Int) { started.countDown() }
            override fun onServiceUnregistered(info:NsdServiceInfo)=Unit
            override fun onUnregistrationFailed(info:NsdServiceInfo,code:Int)=Unit
        }
        targetContext.getSystemService(NsdManager::class.java).registerService(NsdServiceInfo().apply {
            serviceName=name; serviceType="_tvtext._tcp."; port=fixture.port; setAttribute("id",fixture.id); setAttribute("v","1"); setAttribute("ipv4-only","1")
        },NsdManager.PROTOCOL_DNS_SD,listener)
        registrations.add(listener); check(started.await(10,TimeUnit.SECONDS))
    }
    override fun onStart() {
        try {
            val ssl=PhoneFixture.context(context.assets.open("fixture.p12"))
            PhoneFixture(ssl,"f7e69b4b-f95f-44ef-b5a0-100fab7221e1").use { first ->
                PhoneFixture(ssl,"d4a8b0d9-f16f-41a8-a372-7fb6981b59aa").use { second ->
                    fun pair(fixture:PhoneFixture,name:String):Tv {
                        val endpoint=Endpoint("127.0.0.1",fixture.port)
                        val candidate=Tv(fixture.id,name,endpoint,fingerprint=SenderClient.probe(endpoint))
                        val reply=SenderClient.exchange(candidate,1,fixture.pin()); check(reply.code==0)
                        return candidate.copy(token=reply.token)
                    }
                    val a=pair(first,"Гостиная · Google TV"); val b=pair(second,"Спальня · Xiaomi TV").copy(alias="Спальня")
                    val store=ProfileStore(targetContext)
                    targetContext.getSharedPreferences("MainActivity",android.content.Context.MODE_PRIVATE).edit().clear().commit()
                    store.save(emptyMap())
                    activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
                    waitForIdleSync();check(initialScans[activity]==true) { "First launch without pairings must discover TVs" }
                    check(activity.window.decorView.findViewWithTag<View>("status")==null)
                    check(view("list-error").textIsEmpty())
                    waitUntil { field("scanning")==false };check(field("refreshAnimation")==null)
                    ui { activity.finish() }
                    store.save(linkedMapOf(a.id to a,b.id to b)); check(store.load().values.toList()==listOf(a,b))
                    check(store.loadState().names[b.id]==b.alias && store.loadState().profiles[b.id]?.alias=="") { "Aliases are stored separately from paired profiles" }
                    val storage=File(targetContext.noBackupFilesDir,"televisions.bin"); val original=storage.readBytes()
                    storage.writeBytes(legacyProfiles(listOf(a,b.copy(token="",fingerprint=""))))
                    val migrated=store.loadState()
                    check(migrated.profiles.keys==setOf(a.id)&&migrated.names[b.id]==b.alias) { "Legacy unpaired aliases migrate without phantom TVs" }
                    storage.writeBytes(original)
                    check(!String(original,Charsets.ISO_8859_1).contains(a.token))
                    storage.writeBytes(original.copyOf().also { it[it.lastIndex]=(it.last().toInt() xor 1).toByte() })
                    check(runCatching { store.load() }.isFailure); storage.writeBytes(original)

                    advertise("TVText mobile fixture one",first); advertise("TVText mobile fixture two",second)
                    val found=mutableListOf<String>(); val scanDone=CountDownLatch(1)
                    lateinit var discovery:TvDiscovery
                    ui { discovery=TvDiscovery(targetContext); discovery.start({ found.add(it.id) },{ scanDone.countDown() }) }
                    check(scanDone.await(10,TimeUnit.SECONDS)); check(found.contains(a.id)&&found.contains(b.id)) { "NSD did not find both local TV services: $found" }
                    activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
                    waitForIdleSync()
                    check(field("scanning")==false&&field("refreshAnimation")==null) { "Saved pairings must skip startup discovery" }
                    check(field("darkMode")==false)
                    // Real touch cancellation must preserve both recipient and draft.
                    click("tv-${a.id}");draft("Черновик для первого ТВ");ui { input().requestFocus() }
                    ui {
                        val row=view("tv-${b.id}");val start=SystemClock.uptimeMillis()
                        motion(row,MotionEvent.ACTION_DOWN,row.width/2f,row.height/2f,start)
                        motion(row,MotionEvent.ACTION_CANCEL,row.width/2f,row.height/2f,start)
                    }
                    check(field("selectedId")==a.id && input().hasFocus() && input().text.toString()=="Черновик для первого ТВ") { "Cancelled touch cannot switch the recipient or editor focus" }
                    click("tv-${b.id}")
                    check(field("selectedId")==b.id && !input().hasFocus()) { "First tap selects a paired TV without focusing the editor" }
                    ui {
                        val refresh=view("refresh");val start=SystemClock.uptimeMillis()
                        motion(refresh,MotionEvent.ACTION_DOWN,refresh.width/2f,refresh.height/2f,start)
                        motion(refresh,MotionEvent.ACTION_MOVE,-200f,refresh.height/2f,start)
                        motion(refresh,MotionEvent.ACTION_UP,-200f,refresh.height/2f,start)
                    }
                    waitForIdleSync();check(field("scanning")==false) { "Dragging outside a button cancels its action" }
                    draft("")
                    click("refresh");check(field("scanning")==true)
                    Thread.sleep(150);ui { check((view("refresh") as Artwork).glyphRotation>0f) { "Refresh glyph must animate" };check(view("refresh").rotation==0f) { "Button background must not rotate" } }
                    // Keep the asynchronous-state check inside the five-second
                    // scan budget; pointer injection may wait for window animations.
                    ui { val theme=view("theme");val start=SystemClock.uptimeMillis();motion(theme,MotionEvent.ACTION_DOWN,theme.width/2f,theme.height/2f,start);motion(theme,MotionEvent.ACTION_UP,theme.width/2f,theme.height/2f,start) }
                    check(field("darkMode")==true) { "A theme tap switches mode once" }
                    check(field("scanning")==true) { "Theme rebuild preserves the active scan" }
                    waitUntil { field("scanning")==false };ui { check((view("refresh") as Artwork).glyphRotation==0f) }
                    click("theme");check(field("darkMode")==false)
                    val list=view("tv-list") as ScrollView
                    ui { check(list.height==3*(view("tv-${a.id}").height)+(8*activity.resources.displayMetrics.density+.5f).toInt()) { "Reserve three TV slots" } }
                    val extra=(1..3).map { Tv(java.util.UUID.randomUUID().toString(),"Новый ТВ $it",a.endpoint) }
                    @Suppress("UNCHECKED_CAST") val discovered=field("discovered") as MutableMap<String,Tv>
                    val render=MainActivity::class.java.getDeclaredMethod("renderDevices").apply { isAccessible=true }
                    ui { extra.forEach { discovered[it.id]=it };render.invoke(activity) }
                    ui { check((view("tv-list") as ScrollView).canScrollVertically(1)) { "More than three TVs must scroll" } }
                    click("tv-${extra[0].id}");check(field("selectedId")==extra[0].id) { "one click selects the TV immediately" };check(caption(extra[0].id).text=="Нажмите на кнопку «Подключить»")
                    check(!input().hasFocus()&&!view("send").isEnabled)
                    ui { extra.forEach { discovered.remove(it.id) };render.invoke(activity) }
                    click("refresh");check(field("scanning")==true)
                    val coveringMonitor=addMonitor(MainActivity::class.java.name,null,false)
                    ui { activity.startActivity(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) }
                    val covering=waitForMonitorWithTimeout(coveringMonitor,10000) as? MainActivity ?: error("Covering activity did not open")
                    removeMonitor(coveringMonitor)
                    waitUntil { field("scanning")==false }
                    check(field("refreshAnimation")==null&&view("refresh").isEnabled) { "Stopping discovery must release the animation and re-enable Refresh" }
                    ui { covering.finish() };waitForIdleSync();check(field("scanning")==false)
                    val unicode="Привет с телефона 😀 中文\nВторая строка"
                    click("tv-${a.id}"); draft(unicode); click("send"); idle()
                    check(first.received==listOf(unicode)&&second.received.isEmpty()); check(input().text.isEmpty())
                    click("tv-${b.id}"); draft("Только второй ТВ"); click("send"); idle()
                    check(second.received==listOf("Только второй ТВ")&&first.received==listOf(unicode))
                    first.ready=false; click("tv-${a.id}"); draft("Не потерять черновик"); click("send"); idle(); check(input().text.toString()=="Не потерять черновик")
                    check(caption(a.id).text.contains("клавиатуру")) { "Sending error belongs under the target TV name" }
                    check(caption(b.id).text=="Сопряжён")
                    first.ready=true; first.delay=350
                    draft("Исходный текст"); click("send"); draft("Новый черновик"); idle(); check(input().text.toString()=="Новый черновик"); first.delay=0
                    click("rename"); ui { (dialog().findViewById<View>(android.R.id.content).rootView.findViewWithTag<EditText>("rename-input")).setText("Гостиная") }
                    ui { dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
                    check(store.load()[a.id]?.alias=="Гостиная" && store.load()[a.id]?.token==a.token)
                    click("tv-${b.id}"); click("connect"); ui { dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick() }; idle()
                    check(store.load()[b.id]?.paired!=true && SenderClient.exchange(b,2).code==1)
                    waitUntil { field("scanning")==false }
                    ui { discovered.remove(b.id);render.invoke(activity) }
                    check(activity.window.decorView.findViewWithTag<View>("tv-${b.id}")==null) { "An absent renamed TV disappears after unpairing" }
                    check(store.loadState().names[b.id]=="Спальня" && !store.loadState().profiles.containsKey(b.id)) { "Unpair keeps only the local name, never an unpaired profile" }
                    click("refresh");waitUntil { field("scanning")==false }
                    check(view("tv-${b.id}").contentDescription.toString().contains("Спальня")) { "A rediscovered TV recovers its local name" }
                    // Pair through the real dialogs, including explicit fingerprint confirmation.
                    click("tv-${b.id}"); click("connect")
                    val pin=second.pin()
                    ui { dialog().window!!.decorView.findViewWithTag<EditText>("pair-code").setText(pin) }
                    ui { dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick() }; idle()
                    check(dialog().getButton(AlertDialog.BUTTON_POSITIVE).text.isNotEmpty())
                    ui { dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick() }; idle(); check(store.load()[b.id]?.paired==true)
                    click("language"); check((view("language") as TextView).text=="EN"); check(input().text.toString()=="Новый черновик")
                    click("language"); check((view("language") as TextView).text=="RU")
                    ui { input().setSelection(2,5) }
                    click("theme");check(field("darkMode")==true)
                    check(input().text.toString()=="Новый черновик"&&input().selectionStart==2&&input().selectionEnd==5)
                    check(input().currentTextColor==Palette(true).ink&&activity.window.statusBarColor==Palette(true).canvas)
                    check(!input().hasFocus()) { "Theme switching does not open the editor" }
                    sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);waitForIdleSync()
                    ui { check(view("theme").requestFocus()&&view("theme").hasFocus()) { "Theme remains keyboard-focusable" } }
                    click("rename")
                    ui {
                        val name=dialog().window!!.decorView.findViewWithTag<EditText>("rename-input")
                        check(name.currentTextColor==Palette(true).ink)
                        check((name.background as GradientDrawable).color!!.defaultColor==Palette(true).input)
                        name.setText("   ");check(!dialog().getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                    }
                    val monitor=addMonitor(MainActivity::class.java.name,null,false)
                    ui { activity.recreate() }
                    activity=waitForMonitorWithTimeout(monitor,10000) as? MainActivity ?: error("Recreation failed")
                    removeMonitor(monitor);waitForIdleSync()
                    check(field("darkMode")==true&&field("scanning")==false)
                    check(input().text.toString()=="Новый черновик"&&input().selectionStart==2&&input().selectionEnd==5)
                    click("tv-${a.id}"); draft("Дюна: Часть вторая")
                    capture("TVText-Mobile-dark.png")
                    click("theme");capture("TVText-Mobile-light.png")
                    ui {
                        activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input().windowToken,0)
                        activity.window.decorView.clearFocus()
                    }
                    Thread.sleep(350); waitForIdleSync()
                    val screenshot=uiAutomation.takeScreenshot() ?: error("No emulator screenshot")
                    check(screenshot.getPixel(screenshot.width/2,screenshot.height/2)!=android.graphics.Color.BLACK) { "Rendering is disabled on this test image" }
                    val preview=File(targetContext.getExternalFilesDir(null),"TVText-Mobile.png")
                    preview.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }; screenshot.recycle()
                    finish(Activity.RESULT_OK,Bundle().apply { putString("stream","PASS: Android 14: NSD, saved-pairing startup policy, refresh animation/cancellation, Keystore, TLS, Unicode, TV-local status, drafts/selection, themed rename, revoke/pairing, RU/EN, day/night persistence and recreation. Screenshot: ${preview.path}\n") })
                }
            }
        } catch(error:Throwable) {
            finish(Activity.RESULT_CANCELED,Bundle().apply { putString("stream","FAIL: ${android.util.Log.getStackTraceString(error)}\n") })
        } finally {
            val manager=targetContext.getSystemService(NsdManager::class.java)
            registrations.forEach { runCatching { manager.unregisterService(it) } }
        }
    }
    private fun View.textIsEmpty() = (this as TextView).text.isEmpty()
}
