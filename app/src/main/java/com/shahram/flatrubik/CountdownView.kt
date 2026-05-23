package com.shahram.flatrubik

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Overlay drawn on top of the board during the 10-second countdown.
 * Shows a circular progress ring, large countdown number, and helper text.
 */
class CountdownView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var countdown = 10
        set(value) { field = value; invalidate() }

    var accentColor = 0xFF34d399.toInt()
        set(value) { field = value; invalidate() }

    private val density get() = resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x14FFFFFF
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x8CFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99050810.toInt()
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xA6050810.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f

        val ringR = minOf(w, h) * 0.22f
        val strokeW = 4f * density

        trackPaint.strokeWidth = strokeW
        arcPaint.strokeWidth = strokeW
        arcPaint.color = accentColor

        numPaint.textSize = ringR * 1.0f
        labelPaint.textSize = 11f * density
        subPaint.textSize = 10f * density

        val oval = RectF(cx - ringR, cy - ringR - ringR * 0.3f, cx + ringR, cy + ringR - ringR * 0.3f)

        // Track circle
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)

        // Progress arc (starts at top = -90°)
        val sweep = 360f * (countdown / 10f)
        canvas.drawArc(oval, -90f, sweep, false, arcPaint)

        // Countdown number centered in ring
        val ringCy = cy - ringR * 0.3f
        val textY = ringCy + numPaint.textSize * 0.36f
        canvas.drawText(countdown.toString(), cx, textY, numPaint)

        // "watch it scramble" pill
        val label = "watch it scramble"
        val lw = labelPaint.measureText(label) + 24f * density
        val lh = 28f * density
        val ly = cy + ringR * 0.9f
        canvas.drawRoundRect(RectF(cx - lw / 2, ly - lh / 2, cx + lw / 2, ly + lh / 2), lh / 2, lh / 2, pillPaint)
        canvas.drawText(label, cx, ly + labelPaint.textSize * 0.36f, labelPaint)

        // "starts in Xs" pill
        val sub = "starts in ${countdown}s"
        val sw = subPaint.measureText(sub) + 20f * density
        val sh = 22f * density
        val sy = ly + lh * 0.7f + sh
        canvas.drawRoundRect(RectF(cx - sw / 2, sy - sh / 2, cx + sw / 2, sy + sh / 2), sh / 2, sh / 2, pillPaint)
        canvas.drawText(sub, cx, sy + subPaint.textSize * 0.36f, subPaint)
    }
}
