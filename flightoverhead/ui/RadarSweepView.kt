package com.flightoverhead.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class RadarSweepView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var sweepAngle = 0f
    private var cx = 0f
    private var cy = 0f
    private var maxR = 0f

    // Simulated contact blips (fraction of radius, angle in degrees)
    private val blips = listOf(
        Pair(0.62f, 38f),  Pair(0.41f, 117f),
        Pair(0.78f, 195f), Pair(0.33f, 262f),
        Pair(0.55f, 330f), Pair(0.70f, 155f)
    )

    private val ringPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val crossPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 0.8f
        color = Color.argb(25, 201, 168, 76)
    }
    private val sweepEdgePaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE
        strokeWidth = 2.5f; color = Color.argb(220, 0, 230, 118)
    }
    private val centerDotPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.FILL
        color = Color.rgb(201, 168, 76)
    }
    private val centerGlowPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.FILL
        color = Color.argb(60, 201, 168, 76)
    }
    private val blipPaint  = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val blipGlow   = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val sweepFill  = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    private val sweepAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 3200
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener { sweepAngle = it.animatedValue as Float; invalidate() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f; cy = h / 2f
        maxR = min(cx, cy) - 12f
    }

    override fun onDraw(canvas: Canvas) {
        if (maxR <= 0f) return

        // ── Rings ─────────────────────────────────────────────────────
        val ringR     = floatArrayOf(0.95f, 0.70f, 0.47f, 0.26f)
        val ringAlpha = intArrayOf(18, 30, 48, 70)
        for (i in ringR.indices) {
            ringPaint.color = Color.argb(ringAlpha[i], 201, 168, 76)
            canvas.drawCircle(cx, cy, maxR * ringR[i], ringPaint)
        }

        // ── Crosshairs ────────────────────────────────────────────────
        canvas.drawLine(cx, cy - maxR * 1.02f, cx, cy + maxR * 1.02f, crossPaint)
        canvas.drawLine(cx - maxR * 1.02f, cy, cx + maxR * 1.02f, cy, crossPaint)

        // ── Diagonal guides ───────────────────────────────────────────
        val d = maxR * 0.72f
        val diagPaint = Paint(crossPaint).apply { color = Color.argb(12, 201, 168, 76) }
        canvas.drawLine(cx - d, cy - d, cx + d, cy + d, diagPaint)
        canvas.drawLine(cx + d, cy - d, cx - d, cy + d, diagPaint)

        // ── Rotating sweep ────────────────────────────────────────────
        canvas.save()
        canvas.rotate(sweepAngle, cx, cy)

        // Gradient trail sectors (25 layers, each 5° wide)
        val sweepRect = RectF(cx - maxR, cy - maxR, cx + maxR, cy + maxR)
        val steps = 25
        for (i in 0 until steps) {
            val frac = i.toFloat() / steps          // 0 → fresh, 1 → old
            val alpha = ((1f - frac) * (1f - frac) * 110f).toInt()
            sweepFill.color = Color.argb(alpha, 0, 200, 100)
            canvas.drawArc(sweepRect, -frac * 100f, 100f / steps, true, sweepFill)
        }

        // Leading edge line
        canvas.drawLine(cx, cy, cx + maxR, cy, sweepEdgePaint)
        canvas.restore()

        // ── Contact blips ─────────────────────────────────────────────
        for ((r, angleDeg) in blips) {
            val rad = Math.toRadians(angleDeg.toDouble())
            val bx  = cx + (maxR * r * cos(rad)).toFloat()
            val by  = cy + (maxR * r * sin(rad)).toFloat()

            // Alpha based on how recently the sweep passed this blip
            val delta = ((sweepAngle - angleDeg + 360f) % 360f)
            val alpha = if (delta < 100f) ((1f - delta / 100f) * 255f).toInt() else 0

            if (alpha > 5) {
                blipGlow.color = Color.argb(alpha / 4, 0, 230, 118)
                blipPaint.color = Color.argb(alpha,    0, 230, 118)
                canvas.drawCircle(bx, by, 14f, blipGlow)
                canvas.drawCircle(bx, by, 4f,  blipPaint)
            }
        }

        // ── Center glow + dot ─────────────────────────────────────────
        canvas.drawCircle(cx, cy, 22f, centerGlowPaint)
        canvas.drawCircle(cx, cy,  8f, centerDotPaint)
    }

    fun startSweep() { if (!sweepAnimator.isRunning) sweepAnimator.start() }
    fun stopSweep()  { sweepAnimator.cancel() }
}
