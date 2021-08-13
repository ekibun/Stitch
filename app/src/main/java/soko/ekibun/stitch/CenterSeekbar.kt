package soko.ekibun.stitch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max

class CenterSeekbar(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    var a = 0f

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
        val ax = width / 2f + (width / 2f - radius) * a.coerceIn(-1f, 1f)
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
            width / 2f,
            height / 2f - thick,
            ax,
            height / 2f + thick,
            controlPaint
        )
        c.drawOval(
            ax - radius,
            height / 2f - radius,
            ax + radius,
            height / 2f + radius,
            controlPaint
        )
    }

    var onRangeChange: ((Float) -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                dragging = abs(
                    downX - width / 2f - (width / 2f - radius) * a.coerceIn(-1f, 1f)
                ) < 2 * radius
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val newValue = ((event.x - width / 2f) / max(1f, (width / 2f - radius)))
                    .coerceIn(-1f, 1f)
                if (dragging) a = newValue
                if (abs(a) < 0.05) a = 0f
                onRangeChange?.invoke(a)
                invalidate()
            }
        }
        return dragging
    }
}