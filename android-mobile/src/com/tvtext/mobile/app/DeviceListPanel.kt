package com.tvtext.mobile.app

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Owns the TV list rows, selection visuals and empty-state presentation. */
internal class DeviceListPanel(
    private val activity: Activity,
    private val palette: () -> Palette,
    private val dp: (Int) -> Int,
    private val rowHeight: () -> Int,
    private val onSelect: (Tv) -> Unit
) {
    private val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var lastRows: List<Tv>? = null
    private val rowsScroll = ScrollView(activity).apply {
        tag = "tv-list"
        isFillViewport = false
        isVerticalScrollBarEnabled = true
        isScrollbarFadingEnabled = true
        addView(rows)
    }
    private val emptyMessage = label("", 13f, palette().muted).apply {
        tag = "list-error"
        gravity = Gravity.CENTER
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private val empty = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(12), dp(12), dp(12))
        addView(emptyMessage, LinearLayout.LayoutParams(-1, -2))
    }

    val view = FrameLayout(activity).apply {
        tag = "devices-area"
        addView(rowsScroll, FrameLayout.LayoutParams(-1, -1))
        addView(empty, FrameLayout.LayoutParams(-1, -1))
    }

    val scrollPosition: Int get() = rowsScroll.scrollY

    fun restoreScroll(position: Int) { rowsScroll.scrollTo(0, position) }

    fun render(
        values: List<Tv>,
        selectedId: String,
        isPaired: (Tv) -> Boolean,
        caption: (Tv) -> String,
        emptyText: String,
        emptyIsError: Boolean
    ) {
        if (lastRows != values) {
            rows.removeAllViews()
            lastRows = values.toList()
            values.forEach { tv ->
                rows.addView(createRow(tv), LinearLayout.LayoutParams(-1, rowHeight()).apply {
                    if (rows.childCount > 0) topMargin = dp(4)
                })
            }
        }
        rowsScroll.visibility = if (values.isEmpty()) View.INVISIBLE else View.VISIBLE
        empty.visibility = if (values.isEmpty() && emptyText.isNotEmpty()) View.VISIBLE else View.GONE
        emptyMessage.text = emptyText
        emptyMessage.setTextColor(if (emptyIsError) palette().error else palette().muted)

        values.forEachIndexed { index, tv ->
            val row = rows.getChildAt(index) as LinearLayout
            val selected = tv.id == selectedId
            row.background = RippleDrawable(
                ColorStateList.valueOf(palette().ripple),
                shape(
                    if (selected) palette().selected else palette().row,
                    12,
                    if (selected) palette().selectedBorder else Color.TRANSPARENT
                ),
                null
            )
            row.findViewWithTag<View>("check").visibility = if (selected) View.VISIBLE else View.INVISIBLE
            row.findViewWithTag<TextView>("pair-label").apply {
                text = caption(tv)
                setTextColor(palette().muted)
            }
            row.contentDescription = listOf(tv.displayName, caption(tv)).joinToString(", ")
        }
    }

    fun focus(id: String) {
        view.findViewWithTag<View>("tv-$id")?.requestFocus()
    }

    private fun createRow(tv: Tv): LinearLayout = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, dp(7), 0)
        tag = "tv-${tv.id}"
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = false
        setOnClickListener { onSelect(tv) }

        addView(Artwork(activity, "tv", palette()).apply {
            background = shape(palette().surface, 11)
        }, LinearLayout.LayoutParams(dp(36), dp(36)))

        val labels = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
            tag = "labels"
            addView(label(tv.displayName, 14f, palette().ink).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(label("", 11f, palette().muted).apply {
                tag = "pair-label"
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        }
        addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        addView(Artwork(activity, "check", palette()).apply { tag = "check" },
            LinearLayout.LayoutParams(dp(32), dp(32)))
    }

    private fun label(value: String, size: Float, color: Int) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private fun shape(fill: Int, radius: Int, border: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (border != Color.TRANSPARENT) setStroke(dp(1), border)
    }
}
