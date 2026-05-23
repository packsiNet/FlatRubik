package com.shahram.flatrubik

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Draws "FLAT RUBIK" text with a multi-color horizontal gradient (matches the web design).
 */
class GradientTitleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        isFakeBoldText = true
    }

    private val gradientColors = intArrayOf(
        Color.parseColor("#ef4444"),
        Color.parseColor("#facc15"),
        Color.parseColor("#34d399"),
        Color.parseColor("#22d3ee"),
        Color.parseColor("#a78bfa"),
        Color.parseColor("#f472b6"),
    )

    override fun onDraw(canvas: Canvas) {
        val text = "FLAT RUBIK"
        paint.textSize = height * 0.72f
        val textW = paint.measureText(text)
        paint.shader = LinearGradient(0f, 0f, textW, 0f, gradientColors, null, Shader.TileMode.CLAMP)
        val y = (height - paint.ascent() - paint.descent()) / 2f
        canvas.drawText(text, 0f, y, paint)
    }
}
