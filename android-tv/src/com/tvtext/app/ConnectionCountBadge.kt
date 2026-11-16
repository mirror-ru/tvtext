package com.tvtext.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/** Small outlined count badge shown at the end of the connected-devices button. */
internal class ConnectionCountBadge(
    context: Context,
    private val count: Int,
    private val tint: Int
) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = value * density
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
        ring.color = tint
        ring.strokeWidth = dp(1)
        canvas.drawCircle(cx, cy, radius, ring)
        number.color = tint
        number.textSize = dp(12)
        val value = if (count > 99) "99+" else count.toString()
        val metrics = number.fontMetrics
        canvas.drawText(value, cx, cy - (metrics.ascent + metrics.descent) / 2f, number)
    }

    override fun getIntrinsicWidth() = dp(28).toInt()
    override fun getIntrinsicHeight() = dp(28).toInt()
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
