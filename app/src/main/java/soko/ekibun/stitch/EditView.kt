package soko.ekibun.stitch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.*
import android.widget.Scroller
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.withSave
import kotlinx.coroutines.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.*

class EditView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private var minX = 0
    private var minY = 0
    private var maxX = 0
    private var maxY = 0
    var scale = 0.8f
    private var offsetX = 0f
    private var offsetY = 0f
    val rangeX get() = maxX - minX
    val rangeY get() = maxY - minY

    @Suppress("DEPRECATION")
    private val colorPrimary by lazy { resources.getColor(R.color.colorPrimary) }

    @Suppress("DEPRECATION")
    private val colorWarn by lazy { resources.getColor(R.color.colorWarn) }

    private val paint by lazy {
        Paint().apply {
            textAlign = Paint.Align.CENTER
        }
    }

    private val maskPaint by lazy {
        Paint().apply {
            color = (0x88888888).toInt()
        }
    }

    private val gradientPaint by lazy {
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
    }

    private fun CoroutineScope.drawToCanvas(c: Canvas, drawMask: Boolean = true) {
        val activity = context as? EditActivity
        for (i in 0 until App.stitchInfo.size) {
            if (!isActive) break
            val it = App.stitchInfo.getOrNull(i) ?: continue
            val x = it.x
            val y = it.y
            val width = it.width
            val height = it.height
            if (x > c.clipBounds.right ||
                x + width < c.clipBounds.left ||
                y > c.clipBounds.bottom ||
                y + height < c.clipBounds.top
            ) continue
            val bmp = App.bitmapCache.getBitmap(it.image) ?: return

            val l = c.saveLayer(
                x.toFloat(),
                y.toFloat(),
                x.toFloat() + width,
                y.toFloat() + height,
                null
            )

            c.drawBitmap(bmp, x.toFloat(), y.toFloat(), null)
            if (drawMask && activity != null &&
                activity.selected.isNotEmpty() &&
                activity.selected.contains(it.key)
            ) {
                c.drawRect(
                    x.toFloat(),
                    y.toFloat(),
                    x.toFloat() + width,
                    y.toFloat() + height,
                    maskPaint
                )
            }
            gradientPaint.shader = it.shader
            c.drawRect(it.over, gradientPaint)
            c.restoreToCount(l)
        }
    }

    fun drawToBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(rangeX, rangeY, Bitmap.Config.RGB_565)
        val c = Canvas(bitmap)
        c.translate(-minX.toFloat(), -minY.toFloat())
        runBlocking {
            drawToCanvas(c, false)
        }
        return bitmap
    }

    private fun getTranslate(): Array<Float> {
        val scale = scale
        val transX = max(0f, (width - rangeX * scale) / 2) -
                offsetX * scale
        val transY = max(0f, (height - rangeY * scale) / 2) -
                offsetY * scale
        return arrayOf(transX, transY)
    }

    private var beginScale = 0f
    private val scaleGestureDetector by lazy {
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
                    beginScale = scale
                    return super.onScaleBegin(detector)
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val oldScale = scale
                    scale = 0.6f.coerceAtLeast(beginScale * detector.scaleFactor)
                    scrollBy(
                        (detector.focusX * (scale - oldScale) / oldScale).toInt(),
                        (detector.focusY * (scale - oldScale) / oldScale).toInt(),
                    )
                    return super.onScale(detector)
                }
            })
    }

    private var lastX = 0f
    private var lastY = 0f
    private var lastScale = 0f
    private var deferred: Deferred<*>? = null
    private var cacheBitmap: Bitmap? = null
    private var cacheBitmap2: Bitmap? = null

    fun update() {
        var lastX = 0
        var lastY = 0
        var lastW = 0
        var lastH = 0
        maxX = 0
        maxY = 0
        minX = 0
        minY = 0
        App.stitchInfo.forEachIndexed { i, it ->
            it.x = if (i == 0) 0 else lastX + it.dx - (it.width - lastW) / 2
            it.y = if (i == 0) 0 else lastY + it.dy - (it.height - lastH) / 2

            minX = min(minX, it.x)
            minY = min(minY, it.y)
            maxX = max(it.x + it.width, maxX)
            maxY = max(it.y + it.height, maxY)

            it.over.left = max(it.x, lastX).toFloat()
            it.over.top = max(it.y, lastY).toFloat()
            it.over.right = min(it.x + it.width, lastX + lastW).toFloat()
            it.over.bottom = min(it.y + it.height, lastY + lastH).toFloat()
            lastX = it.x
            lastY = it.y
            lastW = it.width
            lastH = it.height

            it.shader = if (abs(it.dx) > abs(it.dy)) {
                if (it.dx > 0) {
                    if (it.a < it.b) LinearGradient(
                        it.over.left + (it.over.right - it.over.left) * it.a,
                        0f,
                        it.over.left + (it.over.right - it.over.left) * it.b,
                        0f,
                        Color.WHITE,
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    ) else {
                        it.over.right = it.over.left + (it.over.right - it.over.left) * it.a
                        null
                    }
                } else {
                    if (it.a < it.b) LinearGradient(
                        it.over.right - (it.over.right - it.over.left) * it.a,
                        0f,
                        it.over.right - (it.over.right - it.over.left) * it.b,
                        0f,
                        Color.WHITE,
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    ) else {
                        it.over.left = it.over.right - (it.over.right - it.over.left) * it.a
                        null
                    }
                }
            } else {
                if (it.dy > 0) {
                    if (it.a < it.b) LinearGradient(
                        0f,
                        it.over.top + (it.over.bottom - it.over.top) * it.a,
                        0f,
                        it.over.top + (it.over.bottom - it.over.top) * it.b,
                        Color.WHITE,
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    ) else {
                        it.over.bottom = it.over.top + (it.over.bottom - it.over.top) * it.a
                        null
                    }
                } else {
                    if (it.a < it.b) LinearGradient(
                        0f,
                        it.over.bottom - (it.over.bottom - it.over.top) * it.a,
                        0f,
                        it.over.bottom - (it.over.bottom - it.over.top) * it.b,
                        Color.WHITE,
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    ) else {
                        it.over.top = it.over.bottom - (it.over.bottom - it.over.top) * it.a
                        null
                    }
                }
            }
        }
        offsetX = max(minX * scale, min(maxX * scale - width, offsetX * scale)) / scale
        offsetY = max(minY * scale, min(maxY * scale - height, offsetY * scale)) / scale
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    val lock = ReentrantLock()
    var dirty = false
    override fun onDraw(c: Canvas) {
        val activity = context as? EditActivity
        val (transX, transY) = getTranslate()
        val scale = scale
        lock.withLock {
            if (dirty || lastX != transX || lastY != transY || lastScale != scale) {
                dirty = false
                val lastDeferred = deferred
                @SuppressLint("DrawAllocation")
                deferred = GlobalScope.async {
                    lastDeferred?.cancelAndJoin()
                    if (!isActive) return@async
                    val bitmap =
                        (if (cacheBitmap2?.width == width && cacheBitmap2?.height == height)
                            cacheBitmap2 else null)
                            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    cacheBitmap2 = bitmap
                    bitmap.eraseColor(Color.TRANSPARENT)
                    bitmap.applyCanvas {
                        translate(transX, transY)
                        scale(scale, scale)
                        drawToCanvas(this)
                    }
                    if (!isActive) return@async
                    lock.withLock {
                        val cache = cacheBitmap
                        cacheBitmap = cacheBitmap2
                        cacheBitmap2 = cache
                        lastX = transX
                        lastY = transY
                        lastScale = scale
                    }
                    postInvalidate()
                }
            }
            c.withSave {
                val diff = scale / lastScale
                c.translate(transX - lastX * diff, transY - lastY * diff)
                c.scale(diff, diff)
                cacheBitmap?.let {
                    c.drawBitmap(it, 0f, 0f, null)
                }
            }
        }
        c.withSave {
            c.translate(transX, transY)
            c.scale(scale, scale)
            paint.strokeWidth = 3 * resources.displayMetrics.density / scale
            paint.textSize = 15 * resources.displayMetrics.density / scale
            val baseline = abs(paint.ascent() + paint.descent()) / 2
            val radius = 20 * resources.displayMetrics.density / scale
            App.stitchInfo.firstOrNull()?.let {
                val selected = activity?.selected?.contains(it.key) ?: false
                paint.color = if (selected) colorWarn else colorPrimary
                c.drawCircle(
                    it.x + it.width / 2f,
                    it.y + it.height / 2f,
                    radius, paint
                )
                paint.color = Color.WHITE
                c.drawText(
                    0.toString(), it.x + it.width / 2f,
                    it.y + it.height / 2f + baseline, paint
                )
            }
            App.stitchInfo.reduceIndexedOrNull { i, acc, it ->
                val selected = activity?.selected?.contains(it.key) ?: false
                paint.color = if (selected) colorWarn else colorPrimary
                c.drawCircle(
                    it.x + it.width / 2f,
                    it.y + it.height / 2f,
                    radius, paint
                )
                val dx = it.x + it.width / 2f - acc.x - acc.width / 2f
                val dy = it.y + it.height / 2f - acc.y - acc.height / 2f
                val lineOffset = sqrt(dx * dx.toDouble() + dy * dy).toFloat() / radius
                if (lineOffset > 2) c.drawLine(
                    acc.x + acc.width / 2f + dx / lineOffset,
                    acc.y + acc.height / 2f + dy / lineOffset,
                    it.x + it.width / 2f - dx / lineOffset,
                    it.y + it.height / 2f - dy / lineOffset,
                    paint
                )
                paint.color = Color.WHITE
                c.drawText(
                    i.toString(), it.x + it.width / 2f,
                    it.y + it.height / 2f + baseline, paint
                )
                it
            }
        }
    }

    private var touching: Stitch.StitchInfo? = null
    private var dragging: Boolean = false
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var downOffsetX: Float = 0f
    private var downOffsetY: Float = 0f
    private var touchPointerId: Int = -1

    private fun scrollHorizontallyBy(dx: Int): Int {
        val oldX = offsetX * scale
        val newX = max(minX * scale, min(maxX * scale - width, oldX + dx))
        offsetX = newX / scale
        return (newX - oldX).toInt()
    }

    private fun scrollVerticallyBy(dy: Int): Int {
        val oldY = offsetY * scale
        val newY = max(minY * scale, min(maxY * scale - height, oldY + dy))
        offsetY = newY / scale
        return (newY - oldY).toInt()
    }

    override fun scrollBy(dx: Int, dy: Int) {
        scrollHorizontallyBy(dx)
        scrollVerticallyBy(dy)
        invalidate()
    }

    private val velocityTracker by lazy { VelocityTracker.obtain() }
    private val scroller by lazy { Scroller(context) }
    private val maxVelocity by lazy { ViewConfiguration.get(context).scaledMaximumFlingVelocity }

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            offsetX = max(
                minX * scale,
                min(maxX * scale - width, scroller.currX.toFloat() * scale)
            ) / scale
            offsetY = max(
                minY * scale,
                min(maxY * scale - height, scroller.currY.toFloat() * scale)
            ) / scale
            postInvalidate()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        velocityTracker.addMovement(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchPointerId = event.getPointerId(0)
                parent.requestDisallowInterceptTouchEvent(true)
                if (!scroller.isFinished) scroller.abortAnimation()
                dragging = false
                val (transX, transY) = getTranslate()
                val x = (event.x - transX) / scale
                val y = (event.y - transY) / scale
                val radius = 20 * resources.displayMetrics.density / scale
                App.stitchInfo.lastOrNull {
                    abs(it.x + it.width / 2f - x) < radius && abs(it.y + it.height / 2f - y) < radius
                }?.let { hit ->
                    this.touching = hit
                    downOffsetX = x - hit.x
                    downOffsetY = y - hit.y
                }
                initialTouchX = event.x
                lastTouchX = event.x
                initialTouchY = event.y
                lastTouchY = event.y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val actionIndex: Int = event.actionIndex
                if (event.getPointerId(actionIndex) == touchPointerId) {
                    // Pick a new pointer to pick up the slack.
                    val newIndex = if (actionIndex == 0) 1 else 0
                    touchPointerId = event.getPointerId(newIndex)
                    lastTouchX = event.getX(newIndex)
                    initialTouchX = lastTouchX
                    lastTouchY = event.getY(newIndex)
                    initialTouchY = lastTouchY
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                this.touching = null
            }
            MotionEvent.ACTION_UP -> {
                val touching = this.touching
                this.touching = null
                if (!dragging && touching != null)
                    (context as? EditActivity)?.selectToggle(touching)
                else if (touching == null) {
                    velocityTracker.computeCurrentVelocity(1000, maxVelocity.toFloat())
                    scroller.fling(
                        offsetX.roundToInt(),
                        offsetY.roundToInt(),
                        (-velocityTracker.xVelocity / scale).roundToInt(),
                        (-velocityTracker.yVelocity / scale).roundToInt(),
                        minX,
                        maxX,
                        minY,
                        maxY
                    )
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val index: Int = event.findPointerIndex(touchPointerId)
                if (index < 0) return true
                val touching = touching
                if (touching != null && event.pointerCount == 1) {
                    val (transX, transY) = getTranslate()
                    val x = ((event.getX(index) - transX) / scale - downOffsetX).roundToInt()
                    val y = ((event.getY(index) - transY) / scale - downOffsetY).roundToInt()
                    val prev = App.stitchInfo.getOrNull(App.stitchInfo.indexOfFirst {
                        it.key == touching.key
                    } - 1)
                    if (prev != null) {
                        touching.dx = touching.dx - touching.x + x
                        touching.dy = touching.dy - touching.y + y
                        touching.x = x
                        touching.y = y
                        if (abs(initialTouchX - event.x) > 10 || abs(initialTouchY - event.y) > 10) {
                            dragging = true
                        }
                        (context as? EditActivity)?.updateRange()
                    }
                } else {
                    val dx = lastTouchX - event.getX(index)
                    val dy = lastTouchY - event.getY(index)
                    scrollBy(dx.roundToInt(), dy.roundToInt())
                    lastTouchX = event.getX(index)
                    lastTouchY = event.getY(index)
                }
            }
        }
        return true
    }
}