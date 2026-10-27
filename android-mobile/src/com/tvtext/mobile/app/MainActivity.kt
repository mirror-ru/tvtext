@file:Suppress("DEPRECATION")
package com.tvtext.mobile.app

import android.app.Activity
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.animation.LinearInterpolator
import android.widget.*
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var store: ProfileStore
    private lateinit var controller: SenderController
    private lateinit var dialogs: SenderDialogs
    private lateinit var localized: Context
    private val catalog = ConnectionCatalog { connections, names -> store.saveState(connections, names) }
    private var selectedId = ""
    private var language = "ru"
    private var darkMode = false
    private var startupChecked = false
    private var resumeScanAfterRecreate = false
    private var scanAtStop = false
    private val busy get() = controller.busy
    private val scanning get() = controller.scanning
    private val storageHealthy get() = controller.storageHealthy
    private val destroyed get() = controller.destroyed
    private val sending get() = controller.sending
    private var lastRows: List<Tv>? = null
    private data class DeviceMessage(val key: String, val error: Boolean = false, val detail: String? = null)
    private val deviceMessages = mutableMapOf<String, DeviceMessage>()
    private var listMessage: DeviceMessage? = null
    private var refreshAnimation: ObjectAnimator? = null
    private lateinit var page: ScrollView
    private lateinit var input: EditText
    private lateinit var rows: LinearLayout
    private lateinit var rowsScroll: ScrollView
    private lateinit var empty: LinearLayout
    private lateinit var emptyMessage: TextView
    private lateinit var send: TextView
    private lateinit var connect: TextView
    private lateinit var rename: TextView
    private lateinit var refresh: View
    private lateinit var languageButton: TextView
    private lateinit var themeButton: View
    private val palette get() = Palette(darkMode)
    private val ink get() = palette.ink
    private val muted get() = palette.muted
    private val blue get() = palette.accent
    private val canvas get() = palette.canvas
    private val rowHeight get() = maxOf(dp(64), (resources.displayMetrics.scaledDensity * 44 + dp(16)).toInt())

    override fun onCreate(state: Bundle?) {
        darkMode = getPreferences(MODE_PRIVATE).getBoolean("dark", false)
        setTheme(resources.getIdentifier(if(darkMode) "AppTheme.Dark" else "AppTheme", "style", packageName))
        super.onCreate(state)
        language = getPreferences(MODE_PRIVATE).getString("language", "ru") ?: "ru"
        store = ProfileStore(this)
        controller = SenderController(catalog, TvDiscovery(this), Executors.newSingleThreadExecutor(),
            { action -> main.post { action() } }, { devicesChanged ->
                if(this::send.isInitialized && !destroyed) {
                    if(devicesChanged)renderDevices()
                    updateRefreshAnimation();updateControls()
                }
            }, { notice ->
                if(notice.key==null)clearMessage(notice.target)
                else message(notice.key,notice.error,notice.target ?: selectedId,notice.detail)
            })
        dialogs = SenderDialogs(this, { palette }, { text(it) })
        try { store.loadState().let { catalog.restore(it.profiles, it.names) } }
        catch (_: Exception) { controller.storageFailed() }
        restoreDeviceState(state)
        selectedId = state?.getString("selected") ?: getPreferences(MODE_PRIVATE).getString("selected", "").orEmpty()
        startupChecked = state?.getBoolean("startup-checked") ?: false
        resumeScanAfterRecreate = state?.getBoolean("resume-scan") ?: false
        buildUi(state?.getString("draft").orEmpty())
        state?.let { input.setSelection(it.getInt("selection-start",input.length()).coerceIn(0,input.length()),it.getInt("selection-end",input.length()).coerceIn(0,input.length())) }
        if (!storageHealthy) message("storage_error", true)
        else if (state?.getBoolean("sending") == true) message("uncertain", true)
    }
    override fun onStart() {
        super.onStart()
        scanAtStop = false
        if(!startupChecked) { startupChecked = true; if(!catalog.hasPairings()) scan() }
        else if(resumeScanAfterRecreate) { resumeScanAfterRecreate = false; controller.scan(preserveExisting = true, revalidateId = invalidSelection()) }
    }
    override fun onStop() {
        scanAtStop = scanning
        controller.stopDiscovery()
        super.onStop()
    }
    override fun onDestroy() {
        controller.destroy()
        refreshAnimation?.cancel()
        dialogs.dismiss()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("draft", input.text.toString()); out.putString("selected", selectedId); out.putBoolean("sending", sending)
        out.putBoolean("startup-checked",startupChecked); out.putInt("selection-start",input.selectionStart); out.putInt("selection-end",input.selectionEnd)
        out.putBoolean("resume-scan", scanning || scanAtStop)
        out.putStringArrayList("invalid-ids", ArrayList(catalog.invalidIds()))
        out.putParcelableArrayList("discovered", ArrayList(catalog.advertisements().map { tv ->
            Bundle().apply { putString("id", tv.id); putString("name", tv.name); putString("host", tv.endpoint.host); putInt("port", tv.endpoint.port) }
        }))
        super.onSaveInstanceState(out)
    }
    private fun restoreDeviceState(state: Bundle?) {
        if (state == null) return
        catalog.restoreInvalidIds(state.getStringArrayList("invalid-ids").orEmpty())
        val devices = runCatching { state.getParcelableArrayList<Bundle>("discovered") }.getOrNull().orEmpty()
        devices.take(100).forEach { item ->
            val id = item.getString("id").orEmpty(); val name = item.getString("name").orEmpty()
            if (Wire.validId(id) && name.isNotBlank() && name.length <= 160) {
                runCatching { Tv(id, name, Endpoint(item.getString("host").orEmpty(), item.getInt("port"))) }
                    .getOrNull()?.let(catalog::discover)
            }
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun text(key: String, vararg args: Any): String {
        val id = resources.getIdentifier(key, "string", packageName)
        return if (args.isEmpty()) localized.getString(id) else localized.getString(id, *args)
    }
    private fun background(fill: Int, radius: Int = 14, border: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); if (border != Color.TRANSPARENT) setStroke(dp(1), border)
    }
    private fun label(value: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        setText(value); textSize = size; setTextColor(color); if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
    }
    private fun button(value: String, primary: Boolean = false) = label(value, 13f, if(primary) Color.WHITE else palette.buttonInk, true).apply {
        gravity = Gravity.CENTER; isClickable = true; isFocusable = true; isFocusableInTouchMode = false; minHeight = dp(44); setPadding(dp(14),0,dp(14),0)
        background = RippleDrawable(ColorStateList.valueOf(palette.ripple), background(if(primary) palette.primary else palette.button,12), null)
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host,info); info.className = android.widget.Button::class.java.name
            }
        }
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun card() = column().apply { setPadding(dp(16),dp(16),dp(16),dp(16)); background = background(palette.surface,20,palette.border) }
    private fun iconButton(kind: String, description: String) = Artwork(this,kind,palette).apply {
        contentDescription=description; isClickable=true; isFocusable=true; isFocusableInTouchMode=false; minimumWidth=dp(48); minimumHeight=dp(48)
        background=RippleDrawable(ColorStateList.valueOf(palette.ripple),background(palette.button,12),null)
        accessibilityDelegate=object:View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host:View,info:android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host,info); info.className=android.widget.Button::class.java.name
            }
        }
    }
    private fun rebuildUi(focus: String) {
        val draft=input.text.toString();val start=input.selectionStart;val end=input.selectionEnd
        val pageY=page.scrollY;val rowsY=rowsScroll.scrollY
        buildUi(draft);input.setSelection(start.coerceIn(0,input.length()),end.coerceIn(0,input.length()))
        page.post { if(!destroyed) { page.scrollTo(0,pageY);rowsScroll.scrollTo(0,rowsY) } }
        if(focus=="theme") themeButton.requestFocus() else languageButton.requestFocus()
    }
    private fun LinearLayout.add(view: View, width: Int = -1, height: Int = -2, top: Int = 0) {
        addView(view, LinearLayout.LayoutParams(width, height).apply { topMargin = dp(top) })
    }
    private fun buildUi(draft: String) {
        refreshAnimation?.cancel();refreshAnimation=null;lastRows=null
        setTheme(resources.getIdentifier(if(darkMode) "AppTheme.Dark" else "AppTheme", "style", packageName))
        val config = Configuration(resources.configuration).apply { setLocale(Locale(language)) }
        localized = createConfigurationContext(config)
        window.setBackgroundDrawable(ColorDrawable(canvas));window.statusBarColor=canvas;window.navigationBarColor=canvas
        val lightFlags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility=if(darkMode) window.decorView.systemUiVisibility and lightFlags.inv() else window.decorView.systemUiVisibility or lightFlags
        page = ScrollView(this).apply { tag="page";setBackgroundColor(canvas); isFillViewport = true; isVerticalScrollBarEnabled = false }
        val wide = resources.configuration.screenWidthDp >= 840
        val maxWidth = if(wide) 1080 else 680
        val stage = FrameLayout(this)
        val content = column().apply { setPadding(dp(20),dp(18),dp(20),dp(12));isFocusableInTouchMode=true }
        stage.addView(content,FrameLayout.LayoutParams(dp(minOf(resources.configuration.screenWidthDp,maxWidth)),-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        page.addView(stage, FrameLayout.LayoutParams(-1,-2)); setContentView(page);content.requestFocus()
        val header = FrameLayout(this)
        val heading = column()
        heading.add(label("TVText",32f,ink,true))
        val version = packageManager.getPackageInfo(packageName,0).versionName.orEmpty()
        heading.add(label(text("version",version),12f,muted),top=7)
        header.addView(heading, FrameLayout.LayoutParams(-2,-2).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL })
        header.addView(Artwork(this,"link",palette), FrameLayout.LayoutParams(dp(124),dp(68)).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL })
        content.add(header,height=dp(76))

        val workspace=LinearLayout(this).apply { orientation=if(wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL; gravity=Gravity.TOP }
        content.add(workspace,top=18)
        val deviceCard = card()
        if(wide) workspace.addView(deviceCard,LinearLayout.LayoutParams(0,-2,1f)) else workspace.add(deviceCard)
        val title = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        title.addView(label(text("televisions"),11f,muted,true),LinearLayout.LayoutParams(0,-2,1f))
        refresh = iconButton("refresh",text("refresh")).apply { tag="refresh";setOnClickListener { scan() } }
        title.addView(refresh,LinearLayout.LayoutParams(dp(48),dp(48))); deviceCard.add(title)
        rows = column()
        rowsScroll = ScrollView(this).apply { tag="tv-list";isFillViewport = false; isVerticalScrollBarEnabled = true; isScrollbarFadingEnabled = true; addView(rows) }
        val devicesArea=FrameLayout(this).apply { tag="devices-area" }
        devicesArea.addView(rowsScroll,FrameLayout.LayoutParams(-1,-1))
        empty = column().apply {
            gravity = Gravity.CENTER; setPadding(dp(12),dp(12),dp(12),dp(12))
        }
        emptyMessage=label("",13f,muted).apply { tag="list-error";gravity=Gravity.CENTER;setLineSpacing(dp(3).toFloat(),1f) }
        empty.add(emptyMessage);devicesArea.addView(empty,FrameLayout.LayoutParams(-1,-1))
        deviceCard.add(devicesArea,height=rowHeight*3+dp(8),top=12)
        val actions = LinearLayout(this).apply { gravity = Gravity.END }
        rename = button(text("rename")).apply { tag = "rename"; setOnClickListener { renameSelected() } }
        connect = button(text("connect")).apply { tag = "connect"; setOnClickListener { selected()?.let { if(paired(it)) unpair(it) else pair(it) } } }
        actions.addView(rename,LinearLayout.LayoutParams(0,dp(44),1f))
        actions.addView(Space(this),LinearLayout.LayoutParams(dp(8),1))
        actions.addView(connect,LinearLayout.LayoutParams(0,dp(44),1f)); deviceCard.add(actions,top=12)

        val composer=column()
        if(wide) workspace.addView(composer,LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(20) }) else workspace.add(composer,top=14)
        val textCard = card(); composer.add(textCard)
        textCard.add(label(text("text_prompt"),16f,ink,true))
        input = EditText(this).apply {
            tag = "text-input"; contentDescription = text("text_accessible"); textSize = 18f; setTextColor(ink)
            EditorStyle.apply(this,palette)
            background = null; setPadding(0,dp(6),0,dp(4)); gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setLines(3); maxLines = 3; isVerticalScrollBarEnabled = true; isScrollbarFadingEnabled = true
            filters = arrayOf(InputFilter.LengthFilter(32768)); setText(draft)
            setOnEditorActionListener { _, action, event ->
                if (action == EditorInfo.IME_ACTION_SEND || event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN && !event.isShiftPressed) { sendText(); true } else false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if(this@MainActivity::send.isInitialized) updateControls() }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        textCard.add(input,top=10)
        val sendPanel = FrameLayout(this)
        send = button(text("send"),true).apply { textSize = 16f; tag = "send"; setOnClickListener { sendText() } }
        sendPanel.addView(send,FrameLayout.LayoutParams(-1,-1))
        sendPanel.addView(Artwork(this,"send",palette),FrameLayout.LayoutParams(dp(40),dp(40)).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; rightMargin = dp(10) })
        composer.add(sendPanel,height=dp(54),top=16)
        val footer = LinearLayout(this).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        languageButton = button(language.uppercase(Locale.ROOT)).apply {
            textSize = 13f; contentDescription = text("language"); tag = "language"
            setOnClickListener {
                language = if(language == "ru") "en" else "ru"
                getPreferences(MODE_PRIVATE).edit().putString("language",language).apply(); rebuildUi("language")
            }
        }
        themeButton=iconButton(if(darkMode) "sun" else "moon",text(if(darkMode) "theme_light" else "theme_dark")).apply {
            tag="theme";setOnClickListener { darkMode=!darkMode;getPreferences(MODE_PRIVATE).edit().putBoolean("dark",darkMode).apply();rebuildUi("theme") }
        }
        footer.addView(languageButton,LinearLayout.LayoutParams(dp(54),dp(48)))
        footer.addView(themeButton,LinearLayout.LayoutParams(dp(48),dp(48)).apply { leftMargin=dp(8) }); composer.add(footer,top=10)
        renderDevices()
        updateRefreshAnimation()
    }
    private fun choices(): List<Tv> = catalog.choices()
    private fun selected() = choices().firstOrNull { it.id == selectedId }
    private fun paired(tv: Tv) = catalog.isPaired(tv)
    private fun renderDevices() {
        val values = choices()
        if(selectedId.isEmpty() || values.none { it.id == selectedId }) selectedId = values.firstOrNull()?.id.orEmpty()
        if(lastRows != values) { rows.removeAllViews(); lastRows = values
            values.forEach { tv ->
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10),0,dp(7),0); tag = "tv-${tv.id}"; isClickable = true; isFocusable = true; isFocusableInTouchMode = false
                    setOnClickListener { if(!busy) {
                        selectedId = tv.id; getPreferences(MODE_PRIVATE).edit().putString("selected",selectedId).apply(); clearMessage(); renderSelection(); updateControls()
                        input.clearFocus()
                        if (!isInTouchMode) requestFocus()
                        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken,0)
                    } }
                }
                row.addView(Artwork(this,"tv",palette).apply { background = background(palette.surface,11) },LinearLayout.LayoutParams(dp(36),dp(36)))
                val name = column().apply { setPadding(dp(10),0,0,0); tag = "labels" }
                name.add(label(tv.displayName,14f).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END })
                name.add(label(if(paired(tv)) text("paired") else text("not_paired"),11f,muted).apply { tag = "pair-label";maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END },top=4)
                row.addView(name,LinearLayout.LayoutParams(0,-2,1f))
                row.addView(Artwork(this,"check",palette).apply { tag = "check" },LinearLayout.LayoutParams(dp(32),dp(32)))
                rows.add(row,height=rowHeight,top=if(rows.childCount == 0) 0 else 4)
            }
        }
        rowsScroll.visibility = if(values.isEmpty()) View.INVISIBLE else View.VISIBLE
        empty.visibility = if(values.isEmpty()&&listMessage!=null) View.VISIBLE else View.GONE
        emptyMessage.text=listMessage?.let { messageText(it) }.orEmpty()
        emptyMessage.setTextColor(if(listMessage?.error==true) palette.error else muted)
        renderSelection(); updateControls()
    }
    private fun renderSelection() {
        for(i in 0 until rows.childCount) {
            val row = rows.getChildAt(i) as LinearLayout; val selected = row.tag == "tv-$selectedId"
            row.background = RippleDrawable(ColorStateList.valueOf(palette.ripple), background(if(selected) palette.selected else palette.row,12,if(selected) palette.selectedBorder else Color.TRANSPARENT),null)
            row.findViewWithTag<View>("check").visibility = if(selected) View.VISIBLE else View.INVISIBLE
            val tv = choices().getOrNull(i)
            val caption=tv?.let { deviceMessages[it.id] }
            row.findViewWithTag<TextView>("pair-label").apply {
                text=caption?.let { messageText(it) } ?: text(if(tv != null && paired(tv)) "paired" else "not_paired")
                setTextColor(muted)
            }
            row.contentDescription=listOfNotNull(tv?.displayName,caption?.let { messageText(it) } ?: text(if(tv!=null&&paired(tv)) "paired" else "not_paired")).joinToString(", ")
        }
    }
    private fun updateControls() {
        val tv = selected()
        send.isEnabled = !busy && tv != null && paired(tv) && input.text.isNotEmpty(); send.alpha = if(send.isEnabled) 1f else .45f
        send.text = text(if(sending) "sending" else "send")
        connect.text = text(if(tv != null && paired(tv)) "unbind" else "connect")
        connect.isEnabled = !busy && tv != null && storageHealthy; connect.alpha = if(connect.isEnabled) 1f else .45f
        rename.isEnabled = !busy && tv != null && storageHealthy; rename.alpha = if(rename.isEnabled) 1f else .45f
        refresh.isEnabled = !busy && !scanning; refresh.alpha = if(busy) .45f else 1f
        languageButton.isEnabled = !busy
        themeButton.isEnabled = !busy
    }
    private fun messageText(value: DeviceMessage) = value.detail?.let { text(value.key,it) } ?: text(value.key)
    private fun message(key: String, error: Boolean = false, target: String = selectedId, detail: String? = null) {
        val value=DeviceMessage(key,error,detail)
        if(target.isNotEmpty()&&choices().any { it.id==target }) deviceMessages[target]=value else listMessage=value
        renderDevices()
    }
    private fun clearMessage(target: String? = null) {
        if(target==null) { deviceMessages.clear();listMessage=null } else deviceMessages.remove(target)
        renderDevices()
    }
    private fun updateRefreshAnimation() {
        if(!this::refresh.isInitialized)return
        if(scanning) {
            if(refreshAnimation==null)refreshAnimation=ObjectAnimator.ofFloat(refresh,"glyphRotation",0f,360f).apply {
                duration=1000;repeatCount=ValueAnimator.INFINITE;interpolator=LinearInterpolator();start()
            }
        } else { refreshAnimation?.cancel();refreshAnimation=null;(refresh as Artwork).glyphRotation=0f }
    }
    private fun invalidSelection() = selectedId.takeIf { it in catalog.invalidIds() }
    private fun scan(completion: (() -> Unit)? = null) = controller.scan(revalidateId = invalidSelection(), completion = completion)
    private fun pair(tv: Tv) {
        dialogs.pair { pin -> controller.probe(tv) { fingerprint ->
            dialogs.fingerprint(fingerprint, {
                controller.pair(tv,pin,fingerprint) {
                    selectedId=tv.id;lastRows=null;clearMessage(tv.id);input.requestFocus()
                }
            }, { clearMessage(tv.id) })
        } }
    }
    private fun sendText() {
        val tv=selected() ?: return
        val draft=input.text.toString()
        controller.send(tv,draft) { if(input.text.toString()==draft)input.text.clear() }
    }
    private fun renameSelected() {
        val tv=selected() ?: return
        dialogs.rename(tv.displayName) { controller.rename(tv,it) }
    }
    private fun unpair(tv: Tv) {
        dialogs.confirmUnpair { controller.unpair(tv) { dialogs.remoteFailure() } }
    }
}
