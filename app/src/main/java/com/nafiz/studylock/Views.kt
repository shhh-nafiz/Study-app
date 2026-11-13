package com.nafiz.studylock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import java.util.Locale

/**
 * Alarm-clock style wheel. Drag or fling to spin, tap the number above/below the
 * centre to step by one. Wraps around (59 -> 0).
 */
class WheelPicker(
    ctx: Context,
    private val min: Int,
    max: Int,
    private val fmt: String
) : View(ctx) {
    private val count = max - min + 1
    private var pos = 0f // continuous position, in rows
    private var lastValue = min
    private var anim: ValueAnimator? = null
    private var downY = 0f
    private var lastY = 0f
    private var moved = false
    private var vt: VelocityTracker? = null
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val rowH = ctx.dp(60).toFloat()
    private val bigSize = ctx.sp(46f)
    private val smallSize = ctx.sp(26f)
    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.CARD }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    var onChange: ((Int) -> Unit)? = null

    val value: Int get() = min + idxOf(Math.round(pos))

    private fun idxOf(k: Int): Int = ((k % count) + count) % count

    fun setValue(v: Int, animate: Boolean) {
        val idx = (v - min).coerceIn(0, count - 1)
        val cur = Math.round(pos)
        var d = idx - idxOf(cur)
        if (d > count / 2) d -= count else if (d < -count / 2) d += count
        if (animate) {
            animateTo(cur + d)
        } else {
            anim?.cancel()
            pos = (cur + d).toFloat()
            lastValue = value
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(context.dp(96), widthMeasureSpec),
            (rowH * 3).toInt()
        )
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val cy = height / 2f
        val r = context.dp(14).toFloat()
        c.drawRoundRect(RectF(0f, cy - rowH / 2, w, cy + rowH / 2), r, r, band)
        val base = Math.round(pos)
        for (i in -2..2) {
            val k = base + i
            val dy = (k - pos) * rowH
            val t = Math.abs(dy) / rowH
            val size = bigSize + (smallSize - bigSize) * Math.min(t, 1f)
            val alpha = if (t <= 1f) 255 - (130 * t).toInt() else (125 * (2f - Math.min(t, 2f))).toInt()
            txt.color = C.TEXT
            txt.alpha = alpha
            txt.textSize = size
            val label = String.format(Locale.US, fmt, min + idxOf(k))
            c.drawText(label, w / 2f, cy + dy + size * 0.35f, txt)
        }
    }

    private fun changed() {
        invalidate()
        val v = value
        if (v != lastValue) {
            lastValue = v
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onChange?.invoke(v)
        }
    }

    private fun animateTo(target: Int) {
        anim?.cancel()
        val from = pos
        val dist = Math.abs(target - from)
        if (dist < 0.001f) {
            pos = target.toFloat()
            changed()
            return
        }
        anim = ValueAnimator.ofFloat(from, target.toFloat()).apply {
            duration = Math.min(600L, (120 + 70 * dist).toLong())
            interpolator = DecelerateInterpolator()
            addUpdateListener { pos = it.animatedValue as Float; changed() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anim?.cancel()
                parent?.requestDisallowInterceptTouchEvent(true)
                downY = e.y
                lastY = e.y
                moved = false
                vt?.recycle()
                vt = VelocityTracker.obtain().also { it.addMovement(e) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                if (!moved && Math.abs(e.y - downY) > slop) moved = true
                if (moved) {
                    pos -= (e.y - lastY) / rowH
                    changed()
                }
                lastY = e.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                vt?.addMovement(e)
                if (!moved) {
                    val third = height / 3f
                    val cur = Math.round(pos)
                    when {
                        e.y < third -> animateTo(cur - 1)
                        e.y > 2 * third -> animateTo(cur + 1)
                        else -> animateTo(cur)
                    }
                } else {
                    vt?.computeCurrentVelocity(1000)
                    val rowsPerSec = -(vt?.yVelocity ?: 0f) / rowH
                    val coast = (rowsPerSec * 0.18f).coerceIn(-30f, 30f)
                    animateTo(Math.round(pos + coast))
                }
                vt?.recycle()
                vt = null
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                animateTo(Math.round(pos))
                vt?.recycle()
                vt = null
                return true
            }
        }
        return true
    }
}

/** Simple bar chart of study time. Tap or drag across it to inspect a bar. */
class BarChartView(ctx: Context) : View(ctx) {
    class Bar(val x: String, val value: String, val full: String, val secs: Long)

    var onPick: ((Int) -> Unit)? = null
    private var bars: List<Bar> = emptyList()
    private var picked = -1
    private var plotLeft = 0f
    private var slot = 1f
    private var downX = 0f
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val dim = Color.parseColor("#2F7D5E")
    private val grid = Paint().apply {
        color = Color.parseColor("#262A31")
        strokeWidth = ctx.dp(1).toFloat()
    }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = ctx.sp(11f) }

    fun setBars(b: List<Bar>, selected: Int) {
        bars = b
        picked = selected
        invalidate()
    }

    private fun niceScale(maxSecs: Long): Pair<Int, Int> {
        if (maxSecs <= 0L) return Pair(30, 60)
        val maxMin = maxSecs / 60.0
        for (s in intArrayOf(5, 10, 15, 20, 30, 60, 90, 120, 180, 240, 360, 480, 720)) {
            val n = Math.max(1, Math.ceil(maxMin / s).toInt())
            if (n <= 4) return Pair(s, n * s)
        }
        return Pair(720, 4 * 720)
    }

    override fun onDraw(c: Canvas) {
        if (bars.isEmpty()) return
        val (stepMin, topMin) = niceScale(bars.maxOf { it.secs })
        val topSecs = topMin * 60f
        val ticks = (0..topMin / stepMin).map { it * stepMin }

        text.textAlign = Paint.Align.RIGHT
        val axisW = ticks.maxOf { text.measureText(fmtDur(it * 60L)) } + context.dp(10)
        val topPad = context.dp(18).toFloat()
        val bottomPad = context.dp(24).toFloat()
        val chartH = height - topPad - bottomPad
        val right = width.toFloat()

        text.color = C.MUTED
        for (m in ticks) {
            val y = topPad + chartH * (1f - m * 60f / topSecs)
            c.drawLine(axisW, y, right, y, grid)
            c.drawText(fmtDur(m * 60L), axisW - context.dp(6), y + text.textSize * 0.35f, text)
        }

        plotLeft = axisW
        slot = (right - axisW) / bars.size
        val bw = slot * 0.6f
        val rad = context.dp(3).toFloat()
        val baseY = topPad + chartH
        text.textAlign = Paint.Align.CENTER
        bars.forEachIndexed { i, b ->
            val cx = axisW + slot * i + slot / 2f
            val h = if (b.secs > 0) Math.max(chartH * (b.secs / topSecs), context.dp(3).toFloat()) else 0f
            if (h > 0f) {
                bar.color = if (i == picked) C.ACCENT else dim
                c.drawRoundRect(RectF(cx - bw / 2, baseY - h, cx + bw / 2, baseY), rad, rad, bar)
            }
            if (b.value.isNotEmpty() && b.secs > 0) {
                text.color = C.TEXT
                c.drawText(b.value, cx, baseY - h - context.dp(4), text)
            }
            if (b.x.isNotEmpty()) {
                text.color = if (i == picked) C.TEXT else C.MUTED
                c.drawText(b.x, cx, height - context.dp(7).toFloat(), text)
            }
        }
    }

    private fun pick(x: Float) {
        if (bars.isEmpty()) return
        val idx = ((x - plotLeft) / slot).toInt().coerceIn(0, bars.size - 1)
        if (idx != picked) {
            picked = idx
            onPick?.invoke(idx)
            invalidate()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; pick(e.x) }
            MotionEvent.ACTION_MOVE -> {
                if (Math.abs(e.x - downX) > slop) parent?.requestDisallowInterceptTouchEvent(true)
                pick(e.x)
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
