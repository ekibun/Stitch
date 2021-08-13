package soko.ekibun.stitch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max

class RangeSeekbar(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    var a = 0.4f
    var b = 0.6f

    @Suppress("DEPRECATION")
    private val colorPrimary by lazy { resources.getColor(R.color.colorPrimary) }

    @Suppress("DEPRECATION")
    private val colorOpaque by lazy { resources.getColor(R.color.opaque) }

    private val controlPaint by lazy {
        Paint().apply {
            isAntiAlias = true
        }
    }

    val thick by lazy { 1 * resources.displayMetrics.density }
    val radius by lazy { 7 * resources.displayMetrics.density }

    @SuppressLint("DrawAllocation")
    override fun onDraw(c: Canvas) {
        val ax = radius + (width - 2 * radius) * a
        val bx = radius + (width - 2 * radius) * b
        if (ax != bx) {
            controlPaint.shader =
                LinearGradient(
                    ax,
                    0f,
                    bx,
                    0f,
                    colorOpaque,
                    colorPrimary,
                    Shader.TileMode.CLAMP
                )
            c.drawRect(
                radius,
                height / 2f - thick,
                width.toFloat() - radius,
                height / 2f + thick,
                controlPaint
            )
            controlPaint.shader = null
        } else {
            controlPaint.color = colorOpaque
            c.drawRect(
                radius,
                height / 2f - thick,
                width.toFloat() - radius,
                height / 2f + thick,
                controlPaint
            )
            controlPaint.color = colorPrimary
            c.drawRect(
                bx,
                height / 2f - thick,
                width.toFloat() - radius,
                height / 2f + thick,
                controlPaint
            )
        }
        controlPaint.color = colorPrimary
        c.drawOval(
            ax - radius,
            height / 2f - radius,
            ax + radius,
            height / 2f + radius,
            controlPaint
        )
        c.drawOval(
            bx - radius,
            height / 2f - radius,
            bx + radius,
            height / 2f + radius,
            controlPaint
        )
    }

    var onRangeChange: ((Float, Float) -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var downObj = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                val ar = abs(downX - radius - (width - 2 * radius) * a)
                val br = abs(downX - radius - (width - 2 * radius) * b)
                downObj = when {
                    ar <= br && ar < 2 * radius -> 1
                    ar >= br && br < 2 * radius -> 2
                    else -> 0
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                downObj = 0
            }
            MotionEvent.ACTION_MOVE -> {
                val newValue = ((event.x - radius) / max(1f, (width - 2 * radius)))
                    .coerceIn(0f, 1f)
                when (downObj) {
                    1 -> {
                        a = newValue
                        b = max(a, b)
                    }
                    2 -> b = max(a, newValue)
                }
                onRangeChange?.invoke(a, b)
                invalidate()
            }
        }
        return downObj > 0
    }
}