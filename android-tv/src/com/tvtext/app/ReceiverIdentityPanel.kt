package com.tvtext.app

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Renders receiver identity and the temporary pairing code in their own cards. */
class ReceiverIdentityPanel(
    private val widgets: TvWidgets,
) {
    private val deviceCard = widgets.row().apply {
        gravity = Gravity.CENTER_VERTICAL
        background = widgets.shape(TvTheme.inset, 16).apply { setStroke(widgets.dp(1), TvTheme.border) }
        setPadding(widgets.dp(12), widgets.dp(10), widgets.dp(14), widgets.dp(10))
    }
    private val deviceCaption = widgets.label("", 11, TvTheme.muted).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val deviceName = widgets.label("", 15, TvTheme.ink).apply {
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val pairingCard = widgets.column().apply {
        tag = "pairing-card"
        setPadding(widgets.dp(14), widgets.dp(10), widgets.dp(14), widgets.dp(10))
        background = widgets.shape(TvTheme.inset, 16).apply { setStroke(widgets.dp(1), TvTheme.border) }
    }
    private val pairCode = widgets.label("", 40, TvTheme.ink).apply {
        gravity = Gravity.CENTER
        typeface = Typeface.create("monospace", Typeface.NORMAL)
        letterSpacing = 0.08f
    }
    private val pairTime = widgets.label("", 12, TvTheme.accent).apply { gravity = Gravity.CENTER }
    private val fingerprint = widgets.label("", 12, TvTheme.muted).apply {
        gravity = Gravity.CENTER
        typeface = Typeface.MONOSPACE
    }

    init {
        val icon = widgets.label("TV", 12, TvTheme.accent).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            background = widgets.shape(TvTheme.icon, 10)
        }
        deviceCard.addView(icon, LinearLayout.LayoutParams(widgets.dp(34), widgets.dp(34)))

        val identity = widgets.column()
        identity.addView(deviceCaption, widgets.full())
        identity.addView(deviceName, widgets.full().apply { topMargin = widgets.dp(3) })
        deviceCard.addView(identity, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = widgets.dp(11) })

        pairingCard.addView(pairCode, widgets.full())
        pairingCard.addView(pairTime, widgets.full().apply { topMargin = widgets.dp(3) })
        pairingCard.addView(fingerprint, widgets.full().apply { topMargin = widgets.dp(6) })
    }

    fun addDeviceCard(panel: LinearLayout) {
        panel.addView(deviceCard, widgets.full().apply { topMargin = widgets.dp(14) })
    }

    fun addPairingCard(panel: LinearLayout) {
        panel.addView(pairingCard, widgets.full().apply { topMargin = widgets.dp(12) })
    }

    fun renderDeviceIdentity(caption: String, name: String) {
        deviceCaption.show(caption)
        deviceName.show(name)
        deviceCard.showIf(name.isNotEmpty())
    }

    fun renderPairing(pin: String, remainingSeconds: Long, shortFingerprint: String) {
        val hasPin = pin.isNotEmpty()
        pairCode.show(if (hasPin) pin.chunked(4).joinToString(" ") else "")
        if (hasPin) {
            pairTime.show(widgets.tr("pair_code_time_format", remainingSeconds / 60, remainingSeconds % 60))
            fingerprint.show(widgets.tr("fingerprint_format", shortFingerprint))
        }
        pairingCard.showIf(hasPin)
    }

    private fun TextView.show(value: String) {
        if (text.toString() != value) text = value
    }

    private fun View.showIf(value: Boolean) {
        val target = if (value) View.VISIBLE else View.GONE
        if (visibility != target) visibility = target
    }
}
