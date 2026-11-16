package com.tvtext.mobile.app

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView

/** Shared style primitives for the mobile sender's screen and composer. */
internal class SenderWidgets(
    private val activity: Activity,
    private val palette: () -> Palette,
    private val toDp: (Int) -> Int,
) {
    fun colors() = palette()
    fun dp(value: Int) = toDp(value)

    fun label(value: String, size: Float, color: Int = palette().ink, bold: Boolean = false) =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
        }

    fun button(value: String, primary: Boolean = false) =
        label(value, 13f, if (primary) Color.WHITE else palette().buttonInk, bold = true).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = false
            minHeight = toDp(44)
            setPadding(toDp(14), 0, toDp(14), 0)
            background = RippleDrawable(
                ColorStateList.valueOf(palette().ripple),
                background(if (primary) palette().primary else palette().button, 12),
                null,
            )
            accessibilityDelegate = buttonAccessibilityDelegate()
        }

    fun iconButton(kind: String, description: String) = Artwork(activity, kind, palette()).apply {
        contentDescription = description
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = false
        minimumWidth = toDp(48)
        minimumHeight = toDp(48)
        background = RippleDrawable(ColorStateList.valueOf(palette().ripple), background(palette().button, 12), null)
        accessibilityDelegate = buttonAccessibilityDelegate()
    }

    fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

    fun card() = column().apply {
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = background(palette().surface, 20, palette().border)
    }

    private fun background(fill: Int, radius: Int, border: Int = Color.TRANSPARENT) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = toDp(radius).toFloat()
            if (border != Color.TRANSPARENT) setStroke(toDp(1), border)
        }

    private fun buttonAccessibilityDelegate() = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = android.widget.Button::class.java.name
        }
    }
}
