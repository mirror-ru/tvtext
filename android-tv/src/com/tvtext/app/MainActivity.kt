package com.tvtext.app

import android.app.Activity
import android.app.AlertDialog
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
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Layout
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        var keyboardTestOpen = false
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
    private var dialog: AlertDialog? = null
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
            if (accessibilityEnabled()) showComputers()
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
        keyboardButton=button(tr("keyboard_test")) { showKeyboardTest() }.apply { tag="keyboard-test" }
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
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun full(height: Int = -2) = LinearLayout.LayoutParams(-1, height)
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun label(value: String, size: Int, color: Int) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        breakStrategy = Layout.BREAK_STRATEGY_BALANCED
        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
    }
    private fun shape(color: Int, radius: Int, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        setStroke(dp(2), stroke)
    }
    private fun focusBackground(color: Int, primary: Boolean = false) = StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), shape(TvTheme.disabled, 13))
        addState(intArrayOf(android.R.attr.state_pressed), shape(if (primary) TvTheme.primaryPressed else TvTheme.buttonPressed, 13, accent))
        addState(intArrayOf(android.R.attr.state_focused), shape(if (primary) TvTheme.primaryFocus else TvTheme.buttonFocus, 13, accent))
        addState(intArrayOf(), shape(color, 13))
    }
    private fun dangerBackground() = StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), shape(TvTheme.disabled, 13))
        addState(intArrayOf(android.R.attr.state_pressed), shape(Color.rgb(157, 38, 57), 13, red))
        addState(intArrayOf(android.R.attr.state_focused), shape(Color.rgb(173, 47, 66), 13, red))
        addState(intArrayOf(), shape(TvTheme.danger, 13))
    }
    private fun card() = column().apply {
        tag="tv-card";background = shape(TvTheme.surface, 22).apply { setStroke(dp(1), TvTheme.border) }
        setPadding(dp(26), dp(22), dp(26), dp(22))
    }
    private fun button(value: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = value; textSize = 14f; isAllCaps = false
        maxLines = 1; isSingleLine = true
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        minimumWidth = 0; minimumHeight = 0
        setPadding(dp(16), 0, dp(16), 0)
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(muted, if (primary) Color.WHITE else accent)))
        background = focusBackground(if (primary) TvTheme.primary else TvTheme.button, primary)
        stateListAnimator = null
        setOnClickListener { action() }
    }
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
    private fun deleteButton(action: () -> Unit) = ImageButton(this).apply {
        val icon = resources.getIdentifier("ic_delete", "drawable", packageName)
        if (icon != 0) setImageResource(icon)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        contentDescription = tr("delete_connection_action")
        scaleType = ImageView.ScaleType.CENTER
        setPadding(dp(10), dp(8), dp(10), dp(8))
        minimumWidth = 0; minimumHeight = 0
        background = dangerBackground()
        stateListAnimator = null
        setOnClickListener { action() }
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
    private fun showComputers() {
        val content = column().apply { tag="connections-panel";setPadding(dp(24),dp(24),dp(24),dp(24)) }
        var listDialog: AlertDialog? = null
        val tokens = runtime.engine.pairedTokens()
        val header=row().apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(computerBadge(40),LinearLayout.LayoutParams(dp(40),dp(40)))
        header.addView(label(tr("connected_pcs"),22,ink).apply {
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(12);marginEnd=dp(12) })
        header.addView(label(tokens.size.toString(),13,accent).apply {
            tag="connections-count";gravity=Gravity.CENTER;contentDescription=tr("connections_count_format",tokens.size)
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);background=shape(TvTheme.icon,10)
            setPadding(dp(10),0,dp(10),0)
        },LinearLayout.LayoutParams(-2,dp(28)))
        content.addView(header,full())
        var firstRemove: View? = null
        val deleteActions=mutableListOf<View>()
        if (tokens.isEmpty()) {
            val empty=column().apply {
                tag="connections-empty";gravity=Gravity.CENTER;setPadding(dp(16),dp(24),dp(16),dp(24))
                background=shape(TvTheme.inset,16)
            }
            empty.addView(computerBadge(48),LinearLayout.LayoutParams(dp(48),dp(48)))
            empty.addView(label(tr("no_connected_pcs"),15,muted).apply { gravity=Gravity.CENTER },full().apply { topMargin=dp(12) })
            content.addView(empty,full().apply { topMargin=dp(20) })
        } else {
            val list=column()
            val itemHeight=maxOf(dp(72),TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,40f,resources.displayMetrics).toInt()+dp(24))
            tokens.forEachIndexed { index, token ->
                val item = row().apply {
                    tag="connection-item"
                    gravity = Gravity.CENTER_VERTICAL
                    background=StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_activated),shape(TvTheme.inset,16,accent))
                        addState(intArrayOf(),shape(TvTheme.inset,16).apply { setStroke(dp(1),TvTheme.border) })
                    }
                    setPadding(dp(14),dp(12),dp(12),dp(12))
                }
                item.addView(computerBadge(40),LinearLayout.LayoutParams(dp(40),dp(40)))
                val text = column()
                text.addView(label(tr("pc_title_format", index + 1),17,ink).apply {
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
                }, full())
                text.addView(label(tr("pc_id_format", token.takeLast(6)), 12, muted), full().apply { topMargin = dp(3) })
                item.addView(text,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(12) })
                val remove = deleteButton {
                    listDialog?.dismiss()
                    confirmRevoke(token,index+1)
                }.apply {
                    tag="connection-delete";contentDescription=tr("delete_connection")+": "+tr("pc_title_format",index+1)
                    setOnFocusChangeListener { _,focused -> item.isActivated=focused }
                }
                if(firstRemove==null)firstRemove=remove
                deleteActions.add(remove)
                item.addView(remove,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginStart=dp(12) })
                list.addView(item,full(itemHeight).apply { if(index>0)topMargin=dp(8) })
            }
            val desiredHeight=minOf(tokens.size,3)*itemHeight+dp(8)*(minOf(tokens.size,3)-1)
            val availableHeight=maxOf(itemHeight,dp(resources.configuration.screenHeightDp-240))
            val scroll=ScrollView(this).apply {
                tag="connections-list";isFocusable=false;isVerticalScrollBarEnabled=true;isScrollbarFadingEnabled=true
                descendantFocusability=ViewGroup.FOCUS_AFTER_DESCENDANTS;addView(list)
            }
            content.addView(scroll,full(minOf(desiredHeight,availableHeight)).apply { topMargin=dp(20) })
        }
        val footer=row().apply { gravity=Gravity.END }
        val close=button(tr("close")) { listDialog?.dismiss() }.apply { tag="connections-close" }
        deleteActions.forEachIndexed { index,action ->
            action.setOnKeyListener { _,key,event ->
                if(event.action!=KeyEvent.ACTION_DOWN)false
                else when {
                    key==KeyEvent.KEYCODE_DPAD_DOWN -> (deleteActions.getOrNull(index+1) ?: close).requestFocus()
                    key==KeyEvent.KEYCODE_DPAD_UP&&index>0 -> deleteActions[index-1].requestFocus()
                    else -> false
                }
            }
        }
        close.setOnKeyListener { _,key,event ->
            if(event.action==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_UP)deleteActions.lastOrNull()?.requestFocus() ?: false else false
        }
        footer.addView(close,LinearLayout.LayoutParams(dp(112),dp(44)))
        content.addView(footer,full().apply { topMargin=dp(20) })
        listDialog=showDialog(AlertDialog.Builder(this),customView=content)
        val width=minOf(520,resources.configuration.screenWidthDp-64)
        listDialog?.window?.setLayout(dp(width),WindowManager.LayoutParams.WRAP_CONTENT)
        content.post { (firstRemove ?: close).requestFocus() }
    }
    private fun computerBadge(size: Int)=ImageView(this).apply {
        val icon=resources.getIdentifier("ic_computer","drawable",packageName)
        setImageResource(icon);imageTintList=ColorStateList.valueOf(accent);scaleType=ImageView.ScaleType.CENTER_INSIDE
        val padding=dp(if(size>40)12 else 9);setPadding(padding,padding,padding,padding)
        background=shape(TvTheme.icon,12);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun dialogContent(title: String, glyph: String, color: Int = accent)=column().apply {
        setPadding(dp(24),dp(24),dp(24),dp(24))
        val header=row().apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(label(glyph,22,color).apply {
            gravity=Gravity.CENTER;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            background=shape(TvTheme.inset,12)
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LinearLayout.LayoutParams(dp(42),dp(42)))
        header.addView(label(title,22,ink).apply {
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(14) })
        addView(header,full())
    }
    private fun dialogMessage(value: String)=label(value,15,muted).apply {
        breakStrategy=Layout.BREAK_STRATEGY_SIMPLE;setLineSpacing(dp(3).toFloat(),1f)
    }
    private fun confirmRevoke(token: String, number: Int) {
        val content=dialogContent(tr("delete_connection_title"),"!",red).apply { tag="revoke-panel" }
        val device=row().apply {
            gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12))
            background=shape(TvTheme.inset,14)
        }
        device.addView(computerBadge(36),LinearLayout.LayoutParams(dp(36),dp(36)))
        device.addView(column().apply {
            addView(label(tr("pc_title_format",number),16,ink),full())
            addView(label(tr("pc_id_format",token.takeLast(6)),12,muted),full().apply { topMargin=dp(4) })
        },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(12) })
        content.addView(device,full().apply { topMargin=dp(18) })
        content.addView(dialogMessage(tr("delete_connection_message")),full().apply { topMargin=dp(16) })
        val actions=row()
        val cancel=button(tr("cancel")) { dialog?.dismiss();showComputers() }.apply { tag="revoke-cancel" }
        val remove=button(tr("delete_connection_action")) {
            try { runtime.engine.revokeToken(token) }
            catch (_: Exception) { toast(tr("save_change_failed")) }
            dialog?.dismiss();update();showComputers()
        }.apply { tag="revoke-confirm";setTextColor(Color.WHITE);background=dangerBackground() }
        actions.addView(cancel,LinearLayout.LayoutParams(0,dp(46),1f))
        actions.addView(remove,LinearLayout.LayoutParams(0,dp(46),1f).apply { marginStart=dp(12) })
        content.addView(actions,full().apply { topMargin=dp(22) })
        showDialog(AlertDialog.Builder(this),customView=content)
        content.post { remove.requestFocus() }
    }
    private fun showKeyboardTest() {
        val content=dialogContent(tr("keyboard_test"),"T").apply { tag="keyboard-test-panel" }
        content.addView(dialogMessage(tr("keyboard_test_hint")),full().apply { topMargin=dp(16) })
        val editor=EditText(this).apply {
            id=resources.getIdentifier("keyboard_test_input","id",packageName);tag="keyboard-test-input"
            hint=tr("keyboard_test_placeholder");contentDescription=tr("keyboard_test_field")
            textSize=18f;setTextColor(ink);setHintTextColor(muted)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions=EditorInfo.IME_FLAG_NO_EXTRACT_UI
            minLines=2;maxLines=3;gravity=Gravity.TOP or Gravity.START
            setPadding(dp(14),dp(12),dp(14),dp(12))
            background=StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused),shape(TvTheme.inset,13,accent))
                addState(intArrayOf(),shape(TvTheme.inset,13,TvTheme.border))
            }
        }
        content.addView(editor,full().apply { topMargin=dp(18) })
        val actions=row()
        val close=button(tr("close"),true) { dialog?.dismiss() }.apply { tag="keyboard-test-close" }
        actions.addView(close,LinearLayout.LayoutParams(0,dp(44),1f))
        content.addView(actions,full().apply { topMargin=dp(18) })
        editor.requestFocus()
        val current=showDialog(AlertDialog.Builder(this),customView=content,showKeyboard=true)
        keyboardTestOpen=true
        current.setOnDismissListener {
            keyboardTestOpen=false
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(editor.windowToken,0)
            if(dialog===current)dialog=null
            keyboardButton.requestFocus()
        }
        editor.post {
            if(current.isShowing) {
                editor.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }
    private fun showDialog(builder: AlertDialog.Builder, customView: View, showKeyboard: Boolean = false): AlertDialog {
        val current=builder.create()
        current.setView(customView,0,0,0,0)
        dialog?.dismiss();dialog=current
        current.setOnDismissListener { if(dialog===current)dialog=null }
        current.setOnShowListener {
            current.window?.setBackgroundDrawable(shape(TvTheme.surface,18,TvTheme.border))
        }
        // The legacy resize flag keeps the dialog usable on Android 9-10 TV firmware.
        @Suppress("DEPRECATION")
        if(showKeyboard)current.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        current.show()
        current.window?.setLayout(dp(minOf(520,resources.configuration.screenWidthDp-64)),WindowManager.LayoutParams.WRAP_CONTENT)
        return current
    }
    private fun scheduleRefresh() { handler.removeCallbacks(refresh); handler.post(refresh) }
    override fun onResume() { super.onResume(); scheduleRefresh() }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    override fun onDestroy() { handler.removeCallbacks(refresh);dialog?.dismiss();super.onDestroy() }
    private fun update() {
        val missing = runtime.service == null
        val accessibilityOff = !accessibilityEnabled()
        val listening = runtime.listening()
        val error = runtime.error
        val accepting = !accessibilityOff && !missing && listening && error.isEmpty()
        val paired = runtime.engine.paired()
        val pin = if (!accessibilityOff) runtime.engine.pairingPin() else ""
        val hasPin = pin.isNotEmpty()
        val color: Int
        val title: String
        val message: String
        when {
            accessibilityOff -> {
                color = amber; title = tr("accessibility_off_title")
                message = tr("accessibility_off_message")
            }
            missing -> {
                color = amber; title = tr("status_caption")
                message = tr("preparing_message")
            }
            error.isNotEmpty() -> {
                color = amber; title = tr("reception_error_title")
                message = tr("reception_error_message")
            }
            listening && hasPin -> {
                color = accent; title = tr("pairing_title")
                message = tr("pairing_hint")
            }
            listening && paired -> {
                color = green; title = tr("status_caption")
                message = tr("ready_to_pair_title")
            }
            listening -> {
                color = green; title = tr("status_caption")
                message = tr("ready_to_pair_title")
            }
            else -> {
                color = amber; title = tr("status_caption")
                message = tr("preparing_message")
            }
        }
        status.show(title); detail.show(message)
        status.showIf(title.isNotEmpty())
        detail.showIf(message.isNotEmpty())
        val compactInfoState = !hasPin && !accessibilityOff && error.isEmpty()
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
