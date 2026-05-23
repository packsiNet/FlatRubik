package com.shahram.flatrubik

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class BoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ---- Public API ----
    var board: PuzzleBoard? = null
        set(value) {
            field = value
            if (width > 0) computeLayout(width, height)
            invalidate()
        }

    var isInteractive = false
    var isPreviewMode = false   // shows solved board during countdown at high-alpha borders
    var onMove: (() -> Unit)? = null
    var onSolved: (() -> Unit)? = null

    // ---- Layout constants ----
    private val density get() = resources.displayMetrics.density
    private val ZONE_GAP get() = 10f * density
    private val ZONE_INSET get() = 4f * density

    private var tileSize = 0f
    private var xCol = FloatArray(0)
    private var yRow = FloatArray(0)
    private val boardClip = RectF()

    // ---- Paints ----
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val zoneBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val diamondPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val diamondPath = Path()

    // ---- Animation ----
    private data class Anim(val isRow: Boolean, val index: Int, val dir: Int, var offset: Float = 0f)
    private var anim: Anim? = null
    private var shuffleAnimator: ValueAnimator? = null

    // ---- Drag ----
    private data class Drag(
        val row: Int, val col: Int,
        val startX: Float, val startY: Float,
        var axis: Char? = null,
        var dx: Float = 0f, var dy: Float = 0f
    )
    private var drag: Drag? = null

    // ---- Layout ----

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeLayout(w, h)
    }

    private fun computeLayout(w: Int, h: Int) {
        val b = board ?: return
        val colBnd = b.colBoundary()
        val rowBnd = b.rowBoundary()
        val nCG = colBnd.count { it }
        val nRG = rowBnd.count { it }

        val zg = ZONE_GAP; val zi = ZONE_INSET
        val tsW = (w - 2 * zi - nCG * zg) / b.level.cols
        val tsH = (h - 2 * zi - nRG * zg) / b.level.rows
        tileSize = min(tsW, tsH)

        xCol = FloatArray(b.level.cols)
        xCol[0] = zi
        for (c in 1 until b.level.cols) xCol[c] = xCol[c - 1] + tileSize + if (colBnd[c - 1]) zg else 0f

        yRow = FloatArray(b.level.rows)
        yRow[0] = zi
        for (r in 1 until b.level.rows) yRow[r] = yRow[r - 1] + tileSize + if (rowBnd[r - 1]) zg else 0f

        val totalW = xCol[b.level.cols - 1] + tileSize + zi
        val totalH = yRow[b.level.rows - 1] + tileSize + zi
        val ox = (w - totalW) / 2f
        val oy = (h - totalH) / 2f
        for (i in xCol.indices) xCol[i] += ox
        for (i in yRow.indices) yRow[i] += oy
        boardClip.set(ox, oy, ox + totalW, oy + totalH)
    }

    // ---- Drawing ----

    override fun onDraw(canvas: Canvas) {
        val b = board ?: return
        if (xCol.isEmpty() || yRow.isEmpty()) return

        canvas.save()
        canvas.clipRect(boardClip)

        drawZoneBgs(canvas, b)
        drawTiles(canvas, b)
        drawCenterDiamonds(canvas, b)

        canvas.restore()
    }

    private fun drawZoneBgs(canvas: Canvas, b: PuzzleBoard) {
        for (z in b.level.zones) {
            val x0 = xCol[z.c0] - ZONE_INSET; val y0 = yRow[z.r0] - ZONE_INSET
            val x1 = xCol[z.c1] + tileSize + ZONE_INSET; val y1 = yRow[z.r1] + tileSize + ZONE_INSET
            zoneBgPaint.color = (z.color and 0x00FFFFFF) or 0x18000000
            canvas.drawRect(x0, y0, x1, y1, zoneBgPaint)
        }
    }

    private fun drawTiles(canvas: Canvas, b: PuzzleBoard) {
        val cols = b.level.cols; val rows = b.level.rows
        val rowWidth = if (cols > 0) xCol[cols - 1] + tileSize - xCol[0] else 0f
        val colHeight = if (rows > 0) yRow[rows - 1] + tileSize - yRow[0] else 0f

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val color = b.grid[r][c]
                val correct = isInteractive && color == b.getZoneColor(r, c)
                val dx = tileOffX(r, c); val dy = tileOffY(r, c)
                val tx = xCol[c] + dx; val ty = yRow[r] + dy

                drawTile(canvas, tx, ty, tileSize - 2f, color, correct)

                // Wrap-around: draw ghost copy on opposite side when tile exits board
                if (dx != 0f && rowWidth > 0f) {
                    if (tx + tileSize > boardClip.right)
                        drawTile(canvas, tx - rowWidth, ty, tileSize - 2f, color, correct)
                    else if (tx < boardClip.left)
                        drawTile(canvas, tx + rowWidth, ty, tileSize - 2f, color, correct)
                }
                if (dy != 0f && colHeight > 0f) {
                    if (ty + tileSize > boardClip.bottom)
                        drawTile(canvas, tx, ty - colHeight, tileSize - 2f, color, correct)
                    else if (ty < boardClip.top)
                        drawTile(canvas, tx, ty + colHeight, tileSize - 2f, color, correct)
                }
            }
        }
    }

    private fun drawTile(canvas: Canvas, x: Float, y: Float, sz: Float, color: Int, correct: Boolean) {
        val radius = max(4f, sz * 0.16f)
        val pad = 1f
        val rect = RectF(x + pad, y + pad, x + sz - pad, y + sz - pad)

        // Glow layers when tile is in correct zone
        if (correct) {
            for (k in 3 downTo 1) {
                val ex = k * 2.5f
                zoneBgPaint.color = (color and 0x00FFFFFF) or ((k * 22) shl 24)
                canvas.drawRoundRect(
                    RectF(rect.left - ex, rect.top - ex, rect.right + ex, rect.bottom + ex),
                    radius + ex, radius + ex, zoneBgPaint
                )
            }
        }

        // Radial gradient tile
        tilePaint.shader = RadialGradient(
            x + sz * 0.32f, y + sz * 0.28f, sz,
            intArrayOf(shade(color, 25), color, shade(color, -20)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, tilePaint)

        // Top shine highlight
        shinePaint.shader = LinearGradient(
            0f, y + sz * 0.05f, 0f, y + sz * 0.25f,
            0x55FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            RectF(x + sz * 0.08f, y + sz * 0.05f, x + sz * 0.92f, y + sz * 0.25f),
            radius * 0.5f, radius * 0.5f, shinePaint
        )
    }

    private fun drawCenterDiamonds(canvas: Canvas, b: PuzzleBoard) {
        val distinctR0 = b.level.zones.map { it.r0 }.distinct().sorted()
        val distinctC0 = b.level.zones.map { it.c0 }.distinct().sorted()

        for (zr in 0 until distinctR0.size - 1) {
            for (zc in 0 until distinctC0.size - 1) {
                val tlZ = b.level.zones.find { it.r0 == distinctR0[zr] && it.c0 == distinctC0[zc] } ?: continue
                val trZ = b.level.zones.find { it.r0 == distinctR0[zr] && it.c0 == distinctC0[zc + 1] } ?: continue
                val blZ = b.level.zones.find { it.r0 == distinctR0[zr + 1] && it.c0 == distinctC0[zc] } ?: continue
                val brZ = b.level.zones.find { it.r0 == distinctR0[zr + 1] && it.c0 == distinctC0[zc + 1] } ?: continue

                val cx = (xCol[tlZ.c1] + tileSize + xCol[trZ.c0]) / 2f
                val cy = (yRow[tlZ.r1] + tileSize + yRow[blZ.r0]) / 2f
                val rad = ZONE_GAP * 1.1f

                // Drop shadow
                diamondPaint.style = Paint.Style.FILL
                for (k in 3 downTo 1) {
                    val ex = k * 3f
                    diamondPaint.color = (k * 20) shl 24
                    diamondPath.rewind()
                    diamondPath.moveTo(cx, cy - rad - ex)
                    diamondPath.lineTo(cx + rad + ex, cy)
                    diamondPath.lineTo(cx, cy + rad + ex)
                    diamondPath.lineTo(cx - rad - ex, cy)
                    diamondPath.close()
                    canvas.drawPath(diamondPath, diamondPaint)
                }

                // 4 triangles
                val quadrants = listOf(
                    Pair(tlZ.color, floatArrayOf(cx, cy, cx - rad, cy, cx, cy - rad)),
                    Pair(trZ.color, floatArrayOf(cx, cy, cx, cy - rad, cx + rad, cy)),
                    Pair(brZ.color, floatArrayOf(cx, cy, cx + rad, cy, cx, cy + rad)),
                    Pair(blZ.color, floatArrayOf(cx, cy, cx, cy + rad, cx - rad, cy))
                )
                for ((color, pts) in quadrants) {
                    diamondPaint.color = color
                    diamondPaint.shader = RadialGradient(
                        pts[2], pts[3], rad * 1.2f,
                        intArrayOf(shade(color, 30), color, shade(color, -15)),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    diamondPath.rewind()
                    diamondPath.moveTo(pts[0], pts[1])
                    diamondPath.lineTo(pts[2], pts[3])
                    diamondPath.lineTo(pts[4], pts[5])
                    diamondPath.close()
                    canvas.drawPath(diamondPath, diamondPaint)
                }
                diamondPaint.shader = null

                // Divider lines
                diamondPaint.style = Paint.Style.STROKE
                diamondPaint.strokeWidth = 1.2f * density
                diamondPaint.color = 0x55000000
                diamondPath.rewind()
                diamondPath.moveTo(cx, cy - rad); diamondPath.lineTo(cx + rad, cy)
                diamondPath.lineTo(cx, cy + rad); diamondPath.lineTo(cx - rad, cy); diamondPath.close()
                diamondPath.moveTo(cx - rad, cy); diamondPath.lineTo(cx + rad, cy)
                diamondPath.moveTo(cx, cy - rad); diamondPath.lineTo(cx, cy + rad)
                canvas.drawPath(diamondPath, diamondPaint)

                // White shine strip on top-left edge
                diamondPaint.style = Paint.Style.STROKE
                diamondPaint.strokeWidth = 1.5f * density
                diamondPaint.color = 0x40FFFFFF
                diamondPath.rewind()
                diamondPath.moveTo(cx - rad * 0.85f, cy - rad * 0.05f)
                diamondPath.lineTo(cx - rad * 0.05f, cy - rad * 0.85f)
                canvas.drawPath(diamondPath, diamondPaint)

                // Center highlight dot
                diamondPaint.style = Paint.Style.FILL
                diamondPaint.color = 0x35FFFFFF
                canvas.drawCircle(cx, cy, tileSize * 0.18f, diamondPaint)
            }
        }
    }

    // ---- Offset computation ----

    private fun tileOffX(r: Int, c: Int): Float {
        anim?.let { a -> if (a.isRow && r == a.index) return a.offset }
        drag?.let { d -> if (d.axis == 'h' && r == d.row) return d.dx }
        return 0f
    }

    private fun tileOffY(r: Int, c: Int): Float {
        anim?.let { a -> if (!a.isRow && c == a.index) return a.offset }
        drag?.let { d -> if (d.axis == 'v' && c == d.col) return d.dy }
        return 0f
    }

    // ---- Touch ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInteractive || anim != null) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val r = hitRow(event.y); val c = hitCol(event.x)
                if (r >= 0 && c >= 0) drag = Drag(r, c, event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val d = drag ?: return true
                val dx = event.x - d.startX; val dy = event.y - d.startY
                val thresh = 8f * density
                if (d.axis == null && (abs(dx) > thresh || abs(dy) > thresh))
                    d.axis = if (abs(dx) > abs(dy)) 'h' else 'v'
                d.dx = dx.coerceIn(-tileSize, tileSize)
                d.dy = dy.coerceIn(-tileSize, tileSize)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val d = drag ?: return true
                drag = null
                val thresh = tileSize * 0.4f
                when (d.axis) {
                    'h' -> if (abs(d.dx) > thresh) commitMove(true, d.row, if (d.dx > 0) 1 else -1)
                    'v' -> if (abs(d.dy) > thresh) commitMove(false, d.col, if (d.dy > 0) 1 else -1)
                }
                invalidate()
            }
        }
        return true
    }

    private fun commitMove(isRow: Boolean, index: Int, dir: Int) {
        val a = Anim(isRow, index, dir)
        anim = a
        ValueAnimator.ofFloat(0f, dir * tileSize).apply {
            duration = 180
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { a.offset = it.animatedValue as Float; invalidate() }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationEnd(an: Animator) {
                    anim = null
                    val b = board ?: return
                    if (isRow) b.shiftRow(index, dir) else b.shiftCol(index, dir)
                    onMove?.invoke()
                    if (b.isSolved()) onSolved?.invoke()
                    invalidate()
                }
                override fun onAnimationStart(a: Animator) {}
                override fun onAnimationCancel(a: Animator) {}
                override fun onAnimationRepeat(a: Animator) {}
            })
            start()
        }
    }

    // Called externally during countdown to animate board scrambling
    fun pushShuffleMove(isRow: Boolean, index: Int, dir: Int, steps: Int = 1, durationMs: Long = 150, onDone: (() -> Unit)? = null) {
        if (anim != null) { onDone?.invoke(); return }
        val boardRef = board ?: return
        val a = Anim(isRow, index, dir)
        anim = a
        val animator = ValueAnimator.ofFloat(0f, dir * steps * tileSize)
        shuffleAnimator = animator
        var cancelled = false
        animator.apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { a.offset = it.animatedValue as Float; invalidate() }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationEnd(an: Animator) {
                    if (shuffleAnimator === animator) shuffleAnimator = null
                    anim = null
                    if (!cancelled) {
                        repeat(steps) {
                            if (isRow) boardRef.shiftRow(index, dir) else boardRef.shiftCol(index, dir)
                        }
                        invalidate()
                        onDone?.invoke()
                    } else {
                        invalidate()
                    }
                }
                override fun onAnimationStart(a: Animator) {}
                override fun onAnimationCancel(a: Animator) { cancelled = true }
                override fun onAnimationRepeat(a: Animator) {}
            })
            start()
        }
    }

    fun cancelShuffle() {
        shuffleAnimator?.cancel()
        shuffleAnimator = null
        anim = null
        invalidate()
    }

    private fun hitRow(y: Float): Int {
        if (yRow.isEmpty()) return -1
        val b = board ?: return -1
        for (r in 0 until b.level.rows) if (y >= yRow[r] && y < yRow[r] + tileSize) return r
        return -1
    }

    private fun hitCol(x: Float): Int {
        if (xCol.isEmpty()) return -1
        val b = board ?: return -1
        for (c in 0 until b.level.cols) if (x >= xCol[c] && x < xCol[c] + tileSize) return c
        return -1
    }

    // ---- Color helpers (match JS shade() function) ----
    private fun shade(color: Int, percent: Int): Int {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        val t = if (percent < 0) 0 else 255; val p = abs(percent) / 100f
        return Color.rgb(
            ((t - r) * p + r).roundToInt().coerceIn(0, 255),
            ((t - g) * p + g).roundToInt().coerceIn(0, 255),
            ((t - b) * p + b).roundToInt().coerceIn(0, 255)
        )
    }

    // Public helper: zone solved counts
    fun solvedZoneCount(): Int = board?.let { b ->
        b.level.zones.indices.count { b.isZoneSolved(it) }
    } ?: 0
}
