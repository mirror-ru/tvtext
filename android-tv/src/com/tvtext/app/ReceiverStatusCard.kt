package com.tvtext.app

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Renders the receiver's current readiness state without owning its lifecycle. */
internal class ReceiverStatusCard(context: Context) : TvWidgets(context) {
    private val icon = label("i", 18, TvTheme.accent).apply {
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val title = label("", 18, TvTheme.ink).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
    }
    private val detail = label("", 14, TvTheme.muted).apply {
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setLineSpacing(dp(3).toFloat(), 1f)
        breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
    }
    private val information = column().apply {
        addView(title, full())
        addView(detail, full())
    }
    val view = row().apply {
        tag = "information-card"
        gravity = Gravity.CENTER_VERTICAL
        background = shape(TvTheme.inset, 16).apply { setStroke(dp(1), TvTheme.border) }
        setPadding(dp(12), dp(10), dp(14), dp(10))
        addView(icon, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(11) })
        addView(information, LinearLayout.LayoutParams(0, -2, 1f))
    }
    private var statusColor = 0

    fun render(screen: ReceiverStatus, heading: String, message: String, tone: Int, attention: Boolean) {
        title.show(heading)
        detail.show(message)
        title.visible(heading.isNotEmpty())
        detail.visible(message.isNotEmpty())
        val compact = screen.compact
        title.textSize = if (compact) 11f else 18f
        title.setTextColor(if (compact) muted else ink)
        title.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        detail.textSize = if (compact) 15f else 14f
        detail.setTextColor(if (compact) ink else muted)
        detail.setPadding(0, if (heading.isNotEmpty()) dp(if (compact) 3 else 6) else 0, 0, 0)
        icon.show(if (attention) "!" else "i")
        if (statusColor != tone) {
            statusColor = tone
            icon.setTextColor(tone)
            icon.background = shape(TvTheme.surface, 10).apply { setStroke(dp(1), tone) }
        }
    }

    private fun TextView.show(value: String) { if (text.toString() != value) text = value }
    private fun View.visible(value: Boolean) {
        val target = if (value) View.VISIBLE else View.GONE
        if (visibility != target) visibility = target
    }
}
