@file:Suppress("DEPRECATION")
package com.tvtext.mobile.app

import android.app.Activity
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.animation.LinearInterpolator
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
    private val messages = DeviceMessages()
    private var refreshAnimation: ObjectAnimator? = null
    private lateinit var screen: SenderScreen
    private var keyboardViewport: KeyboardViewport? = null
    private val page get() = screen.page
    private val devicePanel get() = screen.devicePanel
    private val input get() = screen.composer.input
    private val send get() = screen.composer.sendButton
    private val connect get() = screen.connectButton
    private val rename get() = screen.renameButton
    private val refresh get() = screen.refreshButton
    private val languageButton get() = screen.composer.languageButton
    private val themeButton get() = screen.composer.themeButton
    private val palette get() = Palette(darkMode)
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
                if(this::screen.isInitialized && !destroyed) {
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
        screen = SenderScreen(
            activity = this,
            palette = { palette },
            dp = ::dp,
            text = { key -> text(key) },
            versionLabel = {
                val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
                text("version", version)
            },
            language = { language },
            darkMode = { darkMode },
            rowHeight = { rowHeight },
            onDeviceSelected = ::selectDevice,
            onRefresh = { scan() },
            onRename = ::renameSelected,
            onConnect = {
                selected()?.let { if (paired(it)) unpair(it) else pair(it) }
            },
            onInputChanged = { updateControls() },
            onSend = ::sendText,
            onLanguageClick = ::toggleLanguage,
            onThemeClick = ::toggleTheme,
        )
        buildUi(state?.getString("draft").orEmpty())
        state?.let {
            val start = it.getInt("selection-start", input.length()).coerceIn(0, input.length())
            val end = it.getInt("selection-end", input.length()).coerceIn(0, input.length())
            input.setSelection(start, end)
        }
        if (!storageHealthy) message("storage_error", true)
        else if (state?.getBoolean("sending") == true) message("uncertain", true)
    }
    override fun onStart() {
        super.onStart()
        scanAtStop = false
        if (!startupChecked) {
            startupChecked = true
            if (!catalog.hasPairings()) scan()
        } else if (resumeScanAfterRecreate) {
            resumeScanAfterRecreate = false
            controller.scan(preserveExisting = true, revalidateId = invalidSelection())
        }
    }
    override fun onStop() {
        scanAtStop = scanning
        controller.stopDiscovery()
        super.onStop()
    }
    override fun onDestroy() {
        controller.destroy()
        refreshAnimation?.cancel()
        keyboardViewport?.close()
        keyboardViewport = null
        dialogs.dismiss()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("draft", input.text.toString())
        out.putString("selected", selectedId)
        out.putBoolean("sending", sending)
        out.putBoolean("startup-checked", startupChecked)
        out.putInt("selection-start", input.selectionStart)
        out.putInt("selection-end", input.selectionEnd)
        out.putBoolean("resume-scan", scanning || scanAtStop)
        out.putStringArrayList("invalid-ids", ArrayList(catalog.invalidIds()))
        out.putParcelableArrayList("discovered", ArrayList(catalog.advertisements().map { tv ->
            Bundle().apply {
                putString("id", tv.id)
                putString("name", tv.name)
                putString("host", tv.endpoint.host)
                putInt("port", tv.endpoint.port)
            }
        }))
        super.onSaveInstanceState(out)
    }
    private fun restoreDeviceState(state: Bundle?) {
        if (state == null) return
        catalog.restoreInvalidIds(state.getStringArrayList("invalid-ids").orEmpty())
        val devices = runCatching { state.getParcelableArrayList<Bundle>("discovered") }.getOrNull().orEmpty()
        devices.take(100).forEach { item ->
            val id = item.getString("id").orEmpty()
            val name = item.getString("name").orEmpty()
            if (Wire.validId(id) && name.isNotBlank() && name.length <= 160) {
                runCatching { Tv(id, name, Endpoint(item.getString("host").orEmpty(), item.getInt("port"))) }
                    .getOrNull()?.let(catalog::discover)
            }
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun text(key: String, vararg args: Any): String {
        val id = resources.getIdentifier(key, "string", packageName)
        return if (args.isEmpty()) localized.getString(id) else localized.getString(id, *args)
    }
    private fun rebuildUi(focus: String) {
        val draft = input.text.toString()
        val start = input.selectionStart
        val end = input.selectionEnd
        val pageY = page.scrollY
        val rowsY = devicePanel.scrollPosition

        buildUi(draft)
        input.setSelection(start.coerceIn(0, input.length()), end.coerceIn(0, input.length()))
        page.post {
            if (!destroyed) {
                page.scrollTo(0, pageY)
                devicePanel.restoreScroll(rowsY)
            }
        }
        if (focus == "theme") themeButton.requestFocus() else languageButton.requestFocus()
    }

    private fun buildUi(draft: String) {
        keyboardViewport?.close()
        keyboardViewport = null
        refreshAnimation?.cancel()
        refreshAnimation = null
        val theme = if (darkMode) "AppTheme.Dark" else "AppTheme"
        setTheme(resources.getIdentifier(theme, "style", packageName))

        val config = Configuration(resources.configuration).apply { setLocale(Locale(language)) }
        localized = createConfigurationContext(config)
        window.setBackgroundDrawable(ColorDrawable(canvas))
        window.statusBarColor = canvas
        window.navigationBarColor = canvas
        val lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility = if (darkMode) {
            window.decorView.systemUiVisibility and lightFlags.inv()
        } else {
            window.decorView.systemUiVisibility or lightFlags
        }

        setContentView(screen.build(draft))
        screen.requestContentFocus()
        renderDevices()
        updateRefreshAnimation()
        keyboardViewport = KeyboardViewport(this, page, input, send, { dp(it) }).also { it.start() }
    }
    private fun toggleLanguage() {
        language = if (language == "ru") "en" else "ru"
        getPreferences(MODE_PRIVATE).edit().putString("language", language).apply()
        rebuildUi("language")
    }

    private fun toggleTheme() {
        darkMode = !darkMode
        getPreferences(MODE_PRIVATE).edit().putBoolean("dark", darkMode).apply()
        rebuildUi("theme")
    }

    private fun choices(): List<Tv> = catalog.choices()
    private fun selected() = choices().firstOrNull { it.id == selectedId }
    private fun paired(tv: Tv) = catalog.isPaired(tv)

    private fun renderDevices() {
        val values = choices()
        if (selectedId.isEmpty() || values.none { it.id == selectedId }) {
            selectedId = values.firstOrNull()?.id.orEmpty()
        }
        devicePanel.render(
            values,
            selectedId,
            ::paired,
            { tv -> messageText(messages.forDevice(tv.id, DeviceMessage(if (paired(tv)) "paired" else "not_paired"))) },
            messages.listMessage()?.let(::messageText).orEmpty(),
            messages.listMessage()?.error == true,
        )
        updateControls()
    }

    private fun selectDevice(tv: Tv) {
        if (busy) return
        selectedId = tv.id
        getPreferences(MODE_PRIVATE).edit().putString("selected", selectedId).apply()
        clearMessage()
        input.clearFocus()
        if (!devicePanel.view.isInTouchMode) devicePanel.focus(tv.id)
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun updateControls() {
        val tv = selected()
        send.isEnabled = !busy && tv != null && paired(tv) && input.text.isNotEmpty()
        send.alpha = if (send.isEnabled) 1f else 0.45f
        send.text = text(if (sending) "sending" else "send")
        connect.text = text(if (tv != null && paired(tv)) "unbind" else "connect")
        connect.isEnabled = !busy && tv != null && storageHealthy
        connect.alpha = if (connect.isEnabled) 1f else 0.45f
        rename.isEnabled = !busy && tv != null && storageHealthy
        rename.alpha = if (rename.isEnabled) 1f else 0.45f
        refresh.isEnabled = !busy && !scanning
        refresh.alpha = if (busy) 0.45f else 1f
        languageButton.isEnabled = !busy
        themeButton.isEnabled = !busy
    }
    private fun messageText(value: DeviceMessage) = value.detail?.let { text(value.key, it) } ?: text(value.key)

    private fun message(key: String, error: Boolean = false, target: String = selectedId, detail: String? = null) {
        messages.set(DeviceMessage(key, error, detail), target, choices().mapTo(mutableSetOf()) { it.id })
        renderDevices()
    }

    private fun clearMessage(target: String? = null) {
        messages.clear(target)
        renderDevices()
    }

    private fun updateRefreshAnimation() {
        if (!this::screen.isInitialized) return
        if (scanning) {
            if (refreshAnimation == null) {
                refreshAnimation = ObjectAnimator.ofFloat(refresh, "glyphRotation", 0f, 360f).apply {
                    duration = 1000
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    start()
                }
            }
        } else {
            refreshAnimation?.cancel()
            refreshAnimation = null
            (refresh as Artwork).glyphRotation = 0f
        }
    }

    private fun invalidSelection() = selectedId.takeIf { it in catalog.invalidIds() }

    private fun scan(completion: (() -> Unit)? = null) =
        controller.scan(revalidateId = invalidSelection(), completion = completion)

    private fun pair(tv: Tv) {
        dialogs.pair { pin ->
            controller.probe(tv) { fingerprint ->
                dialogs.fingerprint(fingerprint, {
                    controller.pair(tv, pin, fingerprint) {
                        selectedId = tv.id
                        clearMessage(tv.id)
                        input.requestFocus()
                    }
                }, { clearMessage(tv.id) })
            }
        }
    }

    private fun sendText() {
        val tv = selected() ?: return
        val draft = input.text.toString()
        controller.send(tv, draft) {
            if (input.text.toString() == draft) input.text.clear()
        }
    }

    private fun renameSelected() {
        val tv = selected() ?: return
        dialogs.rename(tv.displayName) { controller.rename(tv, it) }
    }

    private fun unpair(tv: Tv) {
        dialogs.confirmUnpair { controller.unpair(tv) { dialogs.remoteFailure() } }
    }
}
