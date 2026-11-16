package com.tvtext.app

import android.content.SharedPreferences
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout

/** Owns the main-screen actions and keeps their visibility, labels, and focus state together. */
class ReceiverActionsPanel(
    private val context: Context,
    private val widgets: TvWidgets,
    private val onComputersClick: () -> Unit,
    private val onPairClick: () -> Unit,
    private val onKeyboardTestClick: () -> Unit,
    private val onLanguageClick: () -> Unit,
    private val onThemeClick: () -> Unit,
) {
    val computersButton: Button = widgets.button(widgets.tr("connected_pcs"), action = onComputersClick)
    val pairButton: Button = widgets.button(widgets.tr("connect_pc"), primary = true, action = onPairClick)
    val keyboardButton: Button = widgets.button(widgets.tr("keyboard_test"), action = onKeyboardTestClick)
    val languageButton: Button = widgets.button("RU", action = onLanguageClick)
    val themeButton: ImageButton = ImageButton(context).apply {
        tag = "theme"
        background = widgets.focusBackground(TvTheme.button)
        stateListAnimator = null
        setPadding(widgets.dp(8), widgets.dp(8), widgets.dp(8), widgets.dp(8))
        minimumWidth = 0
        minimumHeight = 0
        setOnClickListener { onThemeClick() }
    }

    private var connectionBadgeCount = -1

    fun addTo(panel: LinearLayout, preferences: SharedPreferences) {
        val actions = widgets.row().apply { isBaselineAligned = false }
        actions.addView(computersButton, LinearLayout.LayoutParams(0, widgets.dp(46), 1f))
        actions.addView(pairButton, LinearLayout.LayoutParams(0, widgets.dp(46), 1f).apply {
            marginStart = widgets.dp(12)
        })
        panel.addView(actions, widgets.full().apply { topMargin = widgets.dp(14) })

        panel.addView(View(context).apply { setBackgroundColor(TvTheme.border) },
            widgets.full(widgets.dp(1)).apply { topMargin = widgets.dp(16) })

        val footer = widgets.row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        keyboardButton.tag = "keyboard-test"
        footer.addView(keyboardButton, LinearLayout.LayoutParams(-2, widgets.dp(40)))
        footer.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        footer.addView(languageButton, LinearLayout.LayoutParams(widgets.dp(58), widgets.dp(40)))
        footer.addView(themeButton, LinearLayout.LayoutParams(widgets.dp(44), widgets.dp(40)).apply {
            marginStart = widgets.dp(10)
        })
        panel.addView(footer, widgets.full().apply { topMargin = widgets.dp(10) })
        renderTheme(preferences.getBoolean("dark-mode", true))
    }

    fun render(
        accessibilityOff: Boolean,
        hasPin: Boolean,
        accepting: Boolean,
        connectionCount: Int,
        languageCode: String,
    ) {
        languageButton.show(languageCode)
        val computersLabel = widgets.tr(if (accessibilityOff) "open_accessibility" else "connected_pcs")
        computersButton.show(computersLabel)

        if (accessibilityOff) {
            computersButton.setCompoundDrawables(null, null, null, null)
            computersButton.contentDescription = computersLabel
            connectionBadgeCount = -1
        } else if (connectionBadgeCount != connectionCount) {
            computersButton.setCompoundDrawablePadding(widgets.dp(8))
            computersButton.setCompoundDrawablesWithIntrinsicBounds(
                null,
                null,
                ConnectionCountBadge(context, connectionCount, TvTheme.accent),
                null,
            )
            computersButton.contentDescription = "${widgets.tr("connected_pcs")}: $connectionCount"
            connectionBadgeCount = connectionCount
        }

        keyboardButton.visibility = if (!accessibilityOff && !hasPin) View.VISIBLE else View.GONE
        pairButton.show(widgets.tr(if (hasPin) "cancel_connection" else "connect_pc"))
        computersButton.visibility = if (hasPin) View.GONE else View.VISIBLE
        (pairButton.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            val margin = if (hasPin) 0 else widgets.dp(12)
            if (params.marginStart != margin) {
                params.marginStart = margin
                pairButton.layoutParams = params
            }
        }
        if (!accepting && pairButton.hasFocus() && !hasPin) computersButton.requestFocus()
        pairButton.visibility = if (accessibilityOff) View.GONE else View.VISIBLE
        pairButton.isEnabled = accepting || hasPin
    }

    fun renderTheme(isDark: Boolean) {
        val name = if (isDark) "theme_light" else "theme_dark"
        themeButton.contentDescription = widgets.tr(name)
        themeButton.tooltipText = themeButton.contentDescription
        val icon = context.resources.getIdentifier(
            if (isDark) "ic_sun" else "ic_moon",
            "drawable",
            context.packageName,
        )
        themeButton.setImageResource(icon)
        themeButton.imageTintList = ColorStateList.valueOf(TvTheme.accent)
        themeButton.scaleType = ImageView.ScaleType.CENTER
    }

    private fun Button.show(value: String) {
        if (text.toString() != value) text = value
    }
}
