package com.tvtext.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
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
    private lateinit var statusCard: ReceiverStatusCard
    private val widgets by lazy { TvWidgets(this) }
    private lateinit var identityPanel: ReceiverIdentityPanel
    private lateinit var dialogs: ReceiverDialogs
    private lateinit var actionsPanel: ReceiverActionsPanel
    private var initialFocus: View? = null
    private val ink get() = TvTheme.ink
    private val muted get() = TvTheme.muted
    private val accent get() = TvTheme.accent
    private val green get() = TvTheme.green
    private val amber get() = TvTheme.amber
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
        identityPanel = ReceiverIdentityPanel(widgets)
        actionsPanel = ReceiverActionsPanel(
            context = this,
            widgets = widgets,
            onComputersClick = {
                if (accessibilityEnabled()) dialogs.showComputers()
                else open(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            },
            onPairClick = ::togglePairing,
            onKeyboardTestClick = { dialogs.showKeyboardTest() },
            onLanguageClick = ::toggleLanguage,
            onThemeClick = {
                preferences.edit().putBoolean("dark-mode", !TvTheme.dark).putBoolean("theme-focus", true).apply()
                recreate()
            },
        )
        dialogs = ReceiverDialogs(
            this,
            runtime,
            { update() },
            { keyboardTestOpen = it },
            { actionsPanel.keyboardButton.requestFocus() },
        )
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
        buildHeader(panel)
        buildStatusCard(panel)
        buildDeviceCard(panel)
        buildPairingCard(panel)
        buildActionsAndFooter(panel,preferences)
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
            restoreThemeFocus -> actionsPanel.themeButton
            restoreLanguageFocus -> actionsPanel.languageButton
            !accessibilityEnabled() -> actionsPanel.computersButton
            actionsPanel.pairButton.visibility == View.VISIBLE && actionsPanel.pairButton.isEnabled -> actionsPanel.pairButton
            else -> actionsPanel.computersButton
        }
        initialFocus?.requestFocus()
    }
    private fun buildHeader(panel: LinearLayout) {
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
    }
    private fun buildStatusCard(panel: LinearLayout) {
        statusCard = ReceiverStatusCard(this)
        panel.addView(statusCard.view, full().apply { topMargin = dp(16) })
    }
    private fun buildDeviceCard(panel: LinearLayout) {
        identityPanel.addDeviceCard(panel)
    }

    private fun buildPairingCard(panel: LinearLayout) {
        identityPanel.addPairingCard(panel)
    }
    private fun buildActionsAndFooter(panel: LinearLayout,preferences: android.content.SharedPreferences) {
        actionsPanel.addTo(panel, preferences)
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
    private fun card() = column().apply {
        tag="tv-card";background = shape(TvTheme.surface, 22).apply { setStroke(dp(1), TvTheme.border) }
        setPadding(dp(26), dp(22), dp(26), dp(22))
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
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
    private fun togglePairing() {
        if (runtime.engine.pairingPin().isNotEmpty()) runtime.engine.closePairing()
        else if (runtime.service != null && runtime.listening()) runtime.engine.openPairing()
        update()
        scheduleRefresh()
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
        statusCard.render(screen,title,message,color,accessibilityOff||error.isNotEmpty())
        actionsPanel.render(
            accessibilityOff = accessibilityOff,
            hasPin = hasPin,
            accepting = accepting,
            connectionCount = runtime.engine.pairedTokens().size,
            languageCode = if (currentLanguage() == "en") "EN" else "RU",
        )
        renderDeviceIdentity(accepting)
        renderPairing(pin, hasPin)
    }
    private fun renderDeviceIdentity(accepting: Boolean) {
        val discovery = if (!accepting) "" else if (runtime.advertiser.warning.isNotEmpty())
            tr("discovery_unavailable")
            else runtime.advertiser.name
        val caption = if (runtime.advertiser.warning.isNotEmpty()) tr("discovery_caption") else tr("tv_name_caption")
        identityPanel.renderDeviceIdentity(caption, discovery)
    }
    private fun renderPairing(pin: String, hasPin: Boolean) {
        val seconds = if (hasPin) runtime.engine.pairingSeconds().coerceAtLeast(0L) else 0L
        val fingerprint = if (hasPin) Identity.shortFingerprint(runtime.fingerprint) else ""
        identityPanel.renderPairing(pin, seconds, fingerprint)
    }
}
