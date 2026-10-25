package com.tvtext.mobile.app

import android.content.Context
import android.graphics.*
import android.view.View

class Artwork(context: Context, private val kind: String, private val palette: Palette = Palette(false)) : View(context) {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    var glyphRotation: Float = 0f
        set(value) { field=value;invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = if (kind == "link") 124f else 40f
        canvas.save(); canvas.scale(width / w, height / if (kind == "link") 68f else 40f)
        fun line(color: Int, thickness: Float = 1.7f) { pen.color = color; pen.style = Paint.Style.STROKE; pen.strokeWidth = thickness; pen.strokeCap = Paint.Cap.ROUND }
        fun fill(color: Int) { pen.color = color; pen.style = Paint.Style.FILL }
        fun tv(x: Float, y: Float, size: Float) {
            line(palette.accent); canvas.drawRoundRect(x, y, x + size, y + size * .66f, 4f, 4f, pen)
            canvas.drawLine(x + size / 2, y + size * .67f, x + size / 2, y + size * .93f, pen)
            canvas.drawLine(x + size * .25f, y + size * .93f, x + size * .75f, y + size * .93f, pen)
        }
        when (kind) {
            "link" -> {
                fill(palette.art); canvas.drawOval(26f, 2f, 123f, 65f, pen)
                fill(palette.surface); canvas.drawRoundRect(7f, 27f, 46f, 53f, 4f, 4f, pen)
                line(palette.accent); canvas.drawRoundRect(7f, 27f, 46f, 53f, 4f, 4f, pen)
                canvas.drawLine(2f, 57f, 51f, 57f, pen); canvas.drawLine(15f, 36f, 34f, 36f, pen); canvas.drawLine(15f, 42f, 27f, 42f, pen)
                line(palette.accent); pen.pathEffect = DashPathEffect(floatArrayOf(1f, 3f), 0f); canvas.drawLine(52f, 39f, 72f, 39f, pen); pen.pathEffect = null
                tv(79f, 16f, 39f); fill(palette.green); canvas.drawCircle(111f, 54f, 4.7f, pen)
            }
            "refresh" -> {
                canvas.save();canvas.rotate(glyphRotation,20f,20f)
                line(palette.buttonInk, 2f); canvas.drawArc(11f, 10f, 29f, 29f, 30f, 300f, false, pen)
                canvas.drawLine(28f, 10f, 28f, 16f, pen); canvas.drawLine(22f, 16f, 28f, 16f, pen)
                canvas.restore()
            }
            "check" -> {
                fill(palette.primary); canvas.drawCircle(20f, 20f, 9f, pen)
                line(Color.WHITE, 1.8f); canvas.drawLines(floatArrayOf(16f, 20f, 19f, 23f, 19f, 23f, 24f, 17f), pen)
            }
            "send" -> {
                line(Color.WHITE, 2f); canvas.drawLine(10f, 20f, 29f, 20f, pen)
                canvas.drawLine(23f, 14f, 29f, 20f, pen); canvas.drawLine(23f, 26f, 29f, 20f, pen)
            }
            "sun" -> {
                fill(palette.buttonInk);canvas.drawCircle(20f,20f,4f,pen)
                line(palette.buttonInk)
                repeat(8) { index ->
                    val angle=index*Math.PI/4
                    canvas.drawLine(20f+kotlin.math.cos(angle).toFloat()*7,20f+kotlin.math.sin(angle).toFloat()*7,20f+kotlin.math.cos(angle).toFloat()*10,20f+kotlin.math.sin(angle).toFloat()*10,pen)
                }
            }
            "moon" -> {
                fill(palette.buttonInk);canvas.drawCircle(19f,20f,8f,pen)
                fill(palette.button);canvas.drawCircle(23f,16f,8f,pen)
            }
            else -> tv(9f, 11f, 22f)
        }
        canvas.restore()
    }
}
