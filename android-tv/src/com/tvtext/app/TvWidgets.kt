package com.tvtext.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Layout
import android.view.View
import android.widget.*

/** Shared TV layout primitives; main screen and dialogs use the same focus/style rules. */
open class TvWidgets(protected val context: Context) {
    protected val resources get() = context.resources
    protected val packageName get() = context.packageName
    protected val ink get() = TvTheme.ink
    protected val muted get() = TvTheme.muted
    protected val accent get() = TvTheme.accent
    protected val red get() = TvTheme.alert
    fun tr(name: String, vararg args: Any): String {
        val id = resources.getIdentifier(name, "string", packageName)
        return if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
    }
    fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    fun full(height: Int = -2) = LinearLayout.LayoutParams(-1, height)
    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    fun label(value: String, size: Int, color: Int) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        breakStrategy = Layout.BREAK_STRATEGY_BALANCED
        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
    }
    fun shape(color: Int, radius: Int, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        setStroke(dp(2), stroke)
    }
    fun focusBackground(color: Int, primary: Boolean = false) = StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), shape(TvTheme.disabled, 13))
        addState(intArrayOf(android.R.attr.state_pressed), shape(if (primary) TvTheme.primaryPressed else TvTheme.buttonPressed, 13, accent))
        addState(intArrayOf(android.R.attr.state_focused), shape(if (primary) TvTheme.primaryFocus else TvTheme.buttonFocus, 13, accent))
        addState(intArrayOf(), shape(color, 13))
    }
    fun dangerBackground() = StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), shape(TvTheme.disabled, 13))
        addState(intArrayOf(android.R.attr.state_pressed), shape(Color.rgb(157, 38, 57), 13, red))
        addState(intArrayOf(android.R.attr.state_focused), shape(Color.rgb(173, 47, 66), 13, red))
        addState(intArrayOf(), shape(TvTheme.danger, 13))
    }
    fun button(value: String, primary: Boolean = false, action: () -> Unit) = Button(context).apply {
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
    fun deleteButton(action: () -> Unit) = ImageButton(context).apply {
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
    fun computerBadge(size: Int)=ImageView(context).apply {
        val icon=resources.getIdentifier("ic_computer","drawable",packageName)
        setImageResource(icon);imageTintList=ColorStateList.valueOf(accent);scaleType=ImageView.ScaleType.CENTER_INSIDE
        val padding=dp(if(size>40)12 else 9);setPadding(padding,padding,padding,padding)
        background=shape(TvTheme.icon,12);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}
