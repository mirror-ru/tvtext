package com.tvtext.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Layout
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        @Volatile var keyboardTestOpen = false
            private set
    }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runtime: BridgeRuntime
    private lateinit var statusRow: LinearLayout
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var infoIcon: TextView
    private lateinit var deviceCard: LinearLayout
    private lateinit var deviceCaption: TextView
    private lateinit var device: TextView
    private lateinit var pairingCard: LinearLayout
    private lateinit var pairCode: TextView
    private lateinit var pairTime: TextView
    private lateinit var fingerprint: TextView
    private lateinit var pairButton: Button
    private lateinit var languageButton: Button
    private lateinit var themeButton: ImageButton
    private lateinit var computersButton: Button
    private lateinit var keyboardButton: Button
    private val widgets by lazy { TvWidgets(this) }
    private lateinit var dialogs: ReceiverDialogs
    private var initialFocus: View? = null
    private var statusColor = 0
    private var connectionBadgeCount = -1
    private val ink get() = TvTheme.ink
    private val muted get() = TvTheme.muted
    private val accent get() = TvTheme.accent
    private val green get() = TvTheme.green
    private val amber get() = TvTheme.amber
    private val red get() = TvTheme.alert
    private val refresh = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, if (runtime.engine.pairingPin().isNotEmpty()) 1000L else 5000L)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val language = newBase.getSharedPreferences("ui", Context.MODE_PRIVATE)
            .getString("language", "ru") ?: "ru"
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val preferences=getSharedPreferences("ui",MODE_PRIVATE)
        TvTheme.select(preferences.getBoolean("dark-mode",true))
        setTheme(resources.getIdentifier(if(TvTheme.dark)"AppTheme" else "AppTheme.Light","style",packageName))
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(TvTheme.canvas))
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility=if(TvTheme.dark) window.decorView.systemUiVisibility and
            (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR).inv()
            else window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val restoreLanguageFocus = getSharedPreferences("ui", MODE_PRIVATE).getBoolean("language-focus", false)
        val restoreThemeFocus=preferences.getBoolean("theme-focus",false)
        preferences.edit().remove("language-focus").remove("theme-focus").apply()
        runtime = BridgeRuntime.get(this)
        dialogs = ReceiverDialogs(this,runtime,{ update() },{ keyboardTestOpen=it },{ keyboardButton.requestFocus() })
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            tag="tv-page";setBackgroundColor(TvTheme.canvas)
            clipToPadding = false
        }
        val root = FrameLayout(this).apply { setPadding(dp(24), dp(24), dp(24), dp(24)) }
        scroll.addView(root, FrameLayout.LayoutParams(-1, -2))
        val cardWidth = minOf(520, resources.configuration.screenWidthDp - 48)
        val panel = card()
        root.addView(panel, FrameLayout.LayoutParams(dp(cardWidth), -2, Gravity.CENTER))

        val header = row().apply { gravity = Gravity.CENTER }
        header.addView(label("T", 24, accent).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            background = shape(TvTheme.icon, 13)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(32), dp(32)))
        header.addView(label("TVText", 24, ink).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        header.addView(label(tr("version_format", version ?: ""), 12, muted), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(16) })
        panel.addView(header, full(dp(34)))

        statusRow = row().apply {
            tag="information-card";gravity = Gravity.CENTER_VERTICAL
            background=shape(TvTheme.inset,16).apply { setStroke(dp(1),TvTheme.border) }
            setPadding(dp(12),dp(10),dp(14),dp(10))
        }
        infoIcon = label("i", 18, accent).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        statusRow.addView(infoIcon,LinearLayout.LayoutParams(dp(34),dp(34)).apply { marginEnd=dp(11) })
        val informationText=column()
        status = label("", 18, ink).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            breakStrategy=Layout.BREAK_STRATEGY_SIMPLE
        }
        informationText.addView(status,full())
        detail = label("", 14, muted).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setLineSpacing(dp(3).toFloat(), 1f)
            breakStrategy=Layout.BREAK_STRATEGY_SIMPLE
        }
        informationText.addView(detail,full())
        statusRow.addView(informationText,LinearLayout.LayoutParams(0,-2,1f))
        panel.addView(statusRow, full().apply { topMargin = dp(16) })
        deviceCard = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = shape(TvTheme.inset, 16).apply {
                setStroke(dp(1), TvTheme.border)
            }
            setPadding(dp(12), dp(10), dp(14), dp(10))
        }
        deviceCard.addView(label("TV", 12, accent).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            background = shape(TvTheme.icon, 10)
        }, LinearLayout.LayoutParams(dp(34), dp(34)))
        val deviceText = column()
        deviceCaption = label("", 11, muted).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        deviceText.addView(deviceCaption, full())
        device = label("", 15, ink).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        deviceText.addView(device, full().apply { topMargin = dp(3) })
        deviceCard.addView(deviceText, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(11) })
        panel.addView(deviceCard, full().apply { topMargin = dp(14) })

        pairingCard=column().apply {
            tag="pairing-card";setPadding(dp(14),dp(10),dp(14),dp(10))
            background=shape(TvTheme.inset,16).apply { setStroke(dp(1),TvTheme.border) }
        }
        pairCode = label("", 40, ink).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            letterSpacing = .08f
        }
        pairingCard.addView(pairCode, full())
        pairTime = label("", 12, accent).apply { gravity = Gravity.CENTER }
        pairingCard.addView(pairTime, full().apply { topMargin = dp(3) })
        fingerprint = label("", 12, muted).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE }
        pairingCard.addView(fingerprint, full().apply { topMargin = dp(6) })
        panel.addView(pairingCard,full().apply { topMargin=dp(12) })

        val actions = row().apply { isBaselineAligned = false }
        computersButton = button(tr("connected_pcs")) {
            if (accessibilityEnabled()) dialogs.showComputers()
            else open(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        actions.addView(computersButton, LinearLayout.LayoutParams(0, dp(46), 1f))
        pairButton = button(tr("connect_pc"), true) {
            if (runtime.engine.pairingPin().isNotEmpty()) runtime.engine.closePairing()
            else if (runtime.service != null && runtime.listening()) runtime.engine.openPairing()
            update()
            scheduleRefresh()
        }
        actions.addView(pairButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(12) })
        panel.addView(actions, full().apply { topMargin = dp(14) })

        panel.addView(View(this).apply { setBackgroundColor(TvTheme.border) }, full(dp(1)).apply { topMargin = dp(16) })
        val footer = row().apply { gravity = Gravity.CENTER_VERTICAL }
        keyboardButton=button(tr("keyboard_test")) { dialogs.showKeyboardTest() }.apply { tag="keyboard-test" }
        footer.addView(keyboardButton,LinearLayout.LayoutParams(-2,dp(40)))
        footer.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        languageButton = button(languageCode()) { toggleLanguage() }
        footer.addView(languageButton, LinearLayout.LayoutParams(dp(58), dp(40)))
        themeButton=ImageButton(this).apply {
            tag="theme";contentDescription=tr(if(TvTheme.dark)"theme_light" else "theme_dark");tooltipText=contentDescription
            val icon=resources.getIdentifier(if(TvTheme.dark)"ic_sun" else "ic_moon","drawable",packageName)
            setImageResource(icon);imageTintList=ColorStateList.valueOf(accent);scaleType=ImageView.ScaleType.CENTER
            setPadding(dp(8),dp(8),dp(8),dp(8));minimumWidth=0;minimumHeight=0
            background=focusBackground(TvTheme.button);stateListAnimator=null
            setOnClickListener {
                preferences.edit().putBoolean("dark-mode",!TvTheme.dark).putBoolean("theme-focus",true).apply()
                recreate()
            }
        }
        footer.addView(themeButton,LinearLayout.LayoutParams(dp(44),dp(40)).apply { marginStart=dp(10) })
        panel.addView(footer, full().apply { topMargin = dp(10) })
        setContentView(scroll)
        scroll.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.hide(WindowInsets.Type.statusBars())
            } else {
                @Suppress("DEPRECATION")
                window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
        update()
        initialFocus=when {
            restoreThemeFocus -> themeButton
            restoreLanguageFocus -> languageButton
            !accessibilityEnabled() -> computersButton
            pairButton.visibility==View.VISIBLE&&pairButton.isEnabled -> pairButton
            else -> computersButton
        }
        initialFocus?.requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus) {
            val target=initialFocus ?: return
            initialFocus=null
            window.decorView.post { if(!isFinishing&&!isDestroyed&&target.visibility==View.VISIBLE&&target.isEnabled)target.requestFocus() }
        }
    }

    private fun tr(name: String, vararg args: Any): String {
        val id = resources.getIdentifier(name, "string", packageName)
        return if (args.isEmpty()) getString(id) else getString(id, *args)
    }
    private fun languageCode() = if (currentLanguage() == "en") "EN" else "RU"
    private fun currentLanguage() = getSharedPreferences("ui", MODE_PRIVATE).getString("language", "ru") ?: "ru"
    private fun toggleLanguage() {
        val next = if (currentLanguage() == "ru") "en" else "ru"
        getSharedPreferences("ui", MODE_PRIVATE).edit().putString("language", next).putBoolean("language-focus", true).apply()
        recreate()
    }
    private fun dp(value: Int) = widgets.dp(value)
    private fun full(height: Int = -2) = widgets.full(height)
    private fun column() = widgets.column()
    private fun row() = widgets.row()
    private fun label(value: String, size: Int, color: Int) = widgets.label(value,size,color)
    private fun shape(color: Int, radius: Int, stroke: Int = Color.TRANSPARENT) = widgets.shape(color,radius,stroke)
    private fun focusBackground(color: Int, primary: Boolean = false) = widgets.focusBackground(color,primary)
    private fun card() = column().apply {
        tag="tv-card";background = shape(TvTheme.surface, 22).apply { setStroke(dp(1), TvTheme.border) }
        setPadding(dp(26), dp(22), dp(26), dp(22))
    }
    private fun button(value: String, primary: Boolean = false, action: () -> Unit) = widgets.button(value,primary,action)
    /** Small outlined count badge shown at the end of the connected-devices button. */
    private inner class ConnectionCountBadge(private val count: Int) : Drawable() {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        override fun draw(canvas: Canvas) {
            val bounds = bounds
            val radius = minOf(bounds.width(), bounds.height()) / 2f - dp(2)
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            ring.color = accent
            ring.strokeWidth = dp(1).toFloat()
            canvas.drawCircle(cx, cy, radius, ring)
            number.color = accent
            number.textSize = dp(12).toFloat()
            val value = if (count > 99) "99+" else count.toString()
            val metrics = number.fontMetrics
            canvas.drawText(value, cx, cy - (metrics.ascent + metrics.descent) / 2f, number)
        }

        override fun getIntrinsicWidth() = dp(28)
        override fun getIntrinsicHeight() = dp(28)
        override fun setAlpha(alpha: Int) {
            ring.alpha = alpha
            number.alpha = alpha
            invalidateSelf()
        }
        override fun setColorFilter(colorFilter: ColorFilter?) {
            ring.colorFilter = colorFilter
            number.colorFilter = colorFilter
            invalidateSelf()
        }
        @Deprecated("Deprecated in Android Drawable API", level = DeprecationLevel.WARNING)
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun TextView.show(value: String) { if (text.toString() != value) text = value }
    private fun View.showIf(value: Boolean) {
        val target = if (value) View.VISIBLE else View.GONE
        if (visibility != target) visibility = target
    }
    private fun accessibilityEnabled(): Boolean = try {
        val component = ComponentName(this, TextService::class.java)
        Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
            (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
                .split(':').any { ComponentName.unflattenFromString(it) == component }
    } catch (_: Exception) { runtime.service != null }
    private fun open(action: String) {
        try { startActivity(Intent(action)) }
        catch (_: Exception) { toast(tr("open_settings_failed")) }
    }
    private fun scheduleRefresh() { handler.removeCallbacks(refresh); handler.post(refresh) }
    override fun onResume() { super.onResume(); scheduleRefresh() }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    override fun onDestroy() { handler.removeCallbacks(refresh);dialogs.dismiss();super.onDestroy() }
    private fun update() {
        val missing = runtime.service == null
        val accessibilityOff = !accessibilityEnabled()
        val listening = runtime.listening()
        val error = runtime.error
        val accepting = !accessibilityOff && !missing && listening && error.isEmpty()
        val pin = if (!accessibilityOff) runtime.engine.pairingPin() else ""
        val hasPin = pin.isNotEmpty()
        val screen = ReceiverStatus.resolve(accessibilityOff, missing, listening, error.isNotEmpty(), hasPin)
        val color = when (screen.tone) {
            ReceiverStatus.Tone.ATTENTION -> amber
            ReceiverStatus.Tone.PAIRING -> accent
            ReceiverStatus.Tone.READY -> green
        }
        val title = tr(screen.titleKey)
        val message = tr(screen.messageKey)
        status.show(title); detail.show(message)
        status.showIf(title.isNotEmpty())
        detail.showIf(message.isNotEmpty())
        val compactInfoState = screen.compact
        // Ready and working states mirror the TV name card: a small caption above a clear value.
        status.textSize = if (compactInfoState) 11f else 18f
        status.setTextColor(if (compactInfoState) muted else ink)
        status.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        detail.textSize = if (compactInfoState) 15f else 14f
        detail.setTextColor(if (compactInfoState) ink else muted)
        detail.setPadding(0,if(title.isNotEmpty())dp(if (compactInfoState) 3 else 6) else 0,0,0)
        languageButton.show(languageCode())
        computersButton.show(if (accessibilityOff) tr("open_accessibility") else tr("connected_pcs"))
        val connectionCount = runtime.engine.pairedTokens().size
        if (accessibilityOff) {
            computersButton.setCompoundDrawables(null, null, null, null)
            connectionBadgeCount = -1
        } else if (connectionBadgeCount != connectionCount) {
            computersButton.setCompoundDrawablePadding(dp(8))
            computersButton.setCompoundDrawablesWithIntrinsicBounds(null, null, ConnectionCountBadge(connectionCount), null)
            computersButton.contentDescription = tr("connected_pcs") + ": " + connectionCount
            connectionBadgeCount = connectionCount
        }
        keyboardButton.showIf(!accessibilityOff && !hasPin)
        infoIcon.show(if(accessibilityOff||error.isNotEmpty())"!" else "i")
        if (statusColor != color) {
            statusColor=color;infoIcon.setTextColor(color)
            infoIcon.background=shape(TvTheme.surface,10).apply { setStroke(dp(1),color) }
        }
        val discovery = if (!accepting) "" else if (runtime.advertiser.warning.isNotEmpty())
            tr("discovery_unavailable")
            else runtime.advertiser.name
        val hasDiscoveryMessage = discovery.isNotEmpty()
        deviceCaption.show(if (runtime.advertiser.warning.isNotEmpty()) tr("discovery_caption") else tr("tv_name_caption"))
        device.show(discovery)
        deviceCard.showIf(hasDiscoveryMessage)
        pairCode.show(if (hasPin) pin.chunked(4).joinToString(" ") else "")
        if (hasPin) {
            val seconds = runtime.engine.pairingSeconds().coerceAtLeast(0)
            pairTime.show(tr("pair_code_time_format", seconds / 60, seconds % 60))
            fingerprint.show(tr("fingerprint_format", Identity.shortFingerprint(runtime.fingerprint)))
        }
        pairingCard.showIf(hasPin)
        pairButton.show(if (hasPin) tr("cancel_connection") else tr("connect_pc"))
        computersButton.showIf(!hasPin)
        val pairLayout=pairButton.layoutParams as LinearLayout.LayoutParams
        val pairMargin=if(hasPin)0 else dp(12)
        if(pairLayout.marginStart!=pairMargin) { pairLayout.marginStart=pairMargin;pairButton.layoutParams=pairLayout }
        if (!accepting && pairButton.hasFocus() && !hasPin) {
            computersButton.requestFocus()
        }
        pairButton.showIf(!accessibilityOff)
        pairButton.isEnabled = accepting || hasPin
    }
}
