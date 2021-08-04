package soko.ekibun.stitch

import android.annotation.SuppressLint
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.withClip
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class EditDecorator(private val activity: EditActivity) : RecyclerView.ItemDecoration(),
    View.OnTouchListener {

    private val controlPaint by lazy {
        Paint().apply {
            color = App.resources.getColor(R.color.colorPrimary, null)
        }
    }
    private val controlSelectedPaint by lazy {
        Paint().apply {
            color = App.resources.getColor(R.color.colorWarn, null)
        }
    }
    private val controlPaintText by lazy {
        Paint().apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
    }
    private val dp: Float by lazy { App.resources.displayMetrics.density }

    private fun drawToCanvas(c: Canvas, parent: RecyclerView?) {
        // bitmap
        App.stitchInfo.forEach {
            if (it.x + it.width < c.clipBounds.left ||
                it.x > c.clipBounds.right ||
                it.y > c.clipBounds.bottom ||
                it.y + it.height < c.clipBounds.top
            ) return@forEach

            val bmp = (if (parent != null) App.bitmapCache.tryGetBitmap(it.image) {
                parent.invalidate()
            } else App.bitmapCache.getBitmap(it.image)) ?: return@forEach
            c.withClip(it.path)
            {
                drawBitmap(bmp, it.x.toFloat(), it.y.toFloat(), null)
            }
        }
    }

    fun drawToBitmap(lm: EditLayoutManager): Bitmap {
        val bitmap = Bitmap.createBitmap(lm.rangeX, lm.rangeY, Bitmap.Config.RGB_565)
        val c = Canvas(bitmap)
        c.translate(-lm.minX.toFloat(), -lm.minY.toFloat())
        drawToCanvas(c, null)
        return bitmap
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDraw(c, parent, state)
        val layoutManager = parent.layoutManager as EditLayoutManager
        val (transX, transY, scale) = layoutManager.getTranslate()
        val savaCount = c.save()
        c.translate(transX, transY)
        c.scale(scale, scale)
        // bitmap
        drawToCanvas(c, parent)
        // control
        controlPaint.strokeWidth = 3 * dp / scale
        controlSelectedPaint.strokeWidth = controlPaint.strokeWidth
        controlPaintText.textSize = 15 * dp / scale
        val baseline = abs(controlPaintText.ascent() + controlPaintText.descent()) / 2
        val radius = 20 * dp / scale
        App.stitchInfo.firstOrNull()?.let {
            val selected = activity.selected.contains(it.key)
            c.drawCircle(
                it.x + it.width / 2f,
                it.y + it.height / 2f,
                radius, if (selected) controlSelectedPaint else controlPaint
            )
            c.drawText(
                0.toString(), it.x + it.width / 2f,
                it.y + it.height / 2f + baseline, controlPaintText
            )
        }
        App.stitchInfo.reduceIndexedOrNull { i, acc, it ->
            val selected = activity.selected.contains(it.key)
            c.drawCircle(
                it.x + it.width / 2f,
                it.y + it.height / 2f,
                radius, if (selected) controlSelectedPaint else controlPaint
            )
            c.drawText(
                i.toString(), it.x + it.width / 2f,
                it.y + it.height / 2f + baseline, controlPaintText
            )
            val dx = it.x + it.width / 2f - acc.x - acc.width / 2f
            val dy = it.y + it.height / 2f - acc.y - acc.height / 2f
            val lineOffset = sqrt(dx * dx.toDouble() + dy * dy).toFloat() / radius
            if (lineOffset > 2) c.drawLine(
                acc.x + acc.width / 2f + dx / lineOffset,
                acc.y + acc.height / 2f + dy / lineOffset,
                it.x + it.width / 2f - dx / lineOffset,
                it.y + it.height / 2f - dy / lineOffset,
                if (selected) controlSelectedPaint else controlPaint
            )
            it
        }
        c.restoreToCount(savaCount)
    }


    private var touching: Stitch.StitchInfo? = null
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var downOffsetX: Float = 0f
    private var downOffsetY: Float = 0f
    private var dragging: Boolean = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(parent: View, event: MotionEvent): Boolean {
        if (event.pointerCount != 1) return false
        val touching = touching
        if (event.action == MotionEvent.ACTION_DOWN) {
            dragging = false
            val layoutManager = (parent as RecyclerView).layoutManager as EditLayoutManager
            val (transX, transY, scale) = layoutManager.getTranslate()
            val x = (event.x - transX) / scale
            val y = (event.y - transY) / scale
            val radius = 20 * dp / scale
            val hit = App.stitchInfo.lastOrNull {
                abs(it.x + it.width / 2f - x) < radius && abs(it.y + it.height / 2f - y) < radius
            } ?: return false
            this.touching = hit
            downX = event.x
            downY = event.y
            downOffsetX = x - hit.x
            downOffsetY = y - hit.y
        }
        if (touching == null) return false
        when (event.action) {
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                this.touching = null
                if (!dragging) activity.selectToggle(touching)
            }
            MotionEvent.ACTION_MOVE -> {
                val layoutManager = (parent as RecyclerView).layoutManager as EditLayoutManager
                val (transX, transY, scale) = layoutManager.getTranslate()
                val x = ((event.x - transX) / scale - downOffsetX).roundToInt()
                val y = ((event.y - transY) / scale - downOffsetY).roundToInt()
                val prev =
                    App.stitchInfo.getOrNull(App.stitchInfo.indexOfFirst { it.key == touching.key } - 1)
                if (prev != null) {
                    touching.dx = touching.dx - touching.x + x
                    touching.dy = touching.dy - touching.y + y
                    touching.x = x
                    touching.y = y
                    if (abs(downX - event.x) > 10 || abs(downY - event.y) > 10) {
                        dragging = true
                    }
                    layoutManager.updateRange()
                    parent.invalidate()
                }

            }
        }
        return true
    }
}