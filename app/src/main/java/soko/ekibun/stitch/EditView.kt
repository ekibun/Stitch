package soko.ekibun.stitch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.*
import android.widget.EdgeEffect
import android.widget.OverScroller
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.toRectF
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
    val rangeX get() = maxX - minX
    val rangeY get() = maxY - minY

    private val minScrollY get() = minY * scale
    private val maxScrollY get() = max(minScrollY, maxY * scale - height + paddingTop)
    private val minScrollX get() = minX * scale
    private val maxScrollX
        get() = max(
            minScrollX,
            maxX * scale - width + paddingLeft + paddingRight
        )

    @Suppress("DEPRECATION")
    private val colorPrimary by lazy { resources.getColor(R.color.colorPrimary) }

    @Suppress("DEPRECATION")
    private val colorWarn by lazy { resources.getColor(R.color.colorWarn) }

    private val paint by lazy {
        Paint().apply {
            textAlign = Paint.Align.CENTER
        }
    }

    @Suppress("DEPRECATION")
    private val maskColor by lazy { resources.getColor(R.color.opaque) }

    private val overPaint by lazy {
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
        }
    }

    private val gradientPaint by lazy {
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
    }

    private fun drawToCanvas(c: Canvas, drawMask: Boolean = true) {
        val activity = context as? EditActivity
        val srcRange = Rect()
        val dstRange = Rect()
        val over = RectF()
        val path = Path()
        for (i in 0 until App.stitchInfo.size) {
            val it = App.stitchInfo.getOrNull(i) ?: continue
            srcRange.set(
                (it.width * it.xa).roundToInt(),
                (it.height * it.ya).roundToInt(),
                (it.width * it.xb).roundToInt(),
                (it.height * it.yb).roundToInt()
            )
            dstRange.set(
                it.x + srcRange.left,
                it.y + srcRange.top,
                it.x + srcRange.right,
                it.y + srcRange.bottom
            )
            over.set(it.over)

            if (dstRange.left > c.clipBounds.right ||
                dstRange.right < c.clipBounds.left ||
                dstRange.top > c.clipBounds.bottom ||
                dstRange.bottom < c.clipBounds.top
            ) continue
            val bmp = App.bitmapCache.getBitmap(it.image) ?: return
            path.reset()
            path.addRect(dstRange.toRectF(), Path.Direction.CW)
            if (over.right > over.left && over.bottom > over.top) {
                it.shader?.let { shader ->
                    gradientPaint.shader = shader
                    c.drawRect(over, gradientPaint)
                }
                path.addRect(over, Path.Direction.CCW)
            }
            gradientPaint.shader = null
            c.drawPath(path, gradientPaint)

            if (drawMask && activity != null &&
                activity.selected.isNotEmpty() &&
                activity.selected.contains(it.key)
            ) {
                overPaint.color = maskColor
                c.drawRect(dstRange, overPaint)
            }
            overPaint.color = Color.BLACK
            c.drawBitmap(bmp, srcRange, dstRange, overPaint)
        }
    }

    fun drawToBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(rangeX, rangeY, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.translate(-minX.toFloat(), -minY.toFloat())
        runBlocking {
            drawToCanvas(c, false)
        }
        return bitmap
    }

    private fun getTranslate(): Array<Float> {
        val scale = scale
        val transX = max(0f, (width - paddingLeft - paddingRight - rangeX * scale) / 2) -
                scrollX + paddingLeft
        val transY = max(0f, (height - paddingTop - rangeY * scale) / 2) -
                scrollY + paddingTop
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
                        ((detector.focusX + scrollX) * (scale - oldScale) / oldScale).roundToInt(),
                        ((detector.focusY + scrollY) * (scale - oldScale) / oldScale).roundToInt(),
                    )
                    return super.onScale(detector)
                }
            })
    }

    private var lastDrawX = 0f
    private var lastDrawY = 0f
    private var lastDrawScale = 0f
    private var cacheBitmap: Bitmap? = null
    private var cacheBitmap2: Bitmap? = null

    fun update() {
        var lastX = 0
        var lastY = 0
        var lastW = 0
        var lastH = 0
        val lastRect = Rect()
        val rect = Rect()
        maxX = Int.MIN_VALUE
        maxY = Int.MIN_VALUE
        minX = Int.MAX_VALUE
        minY = Int.MAX_VALUE
        App.stitchInfo.forEachIndexed { i, it ->
            it.x = if (i == 0) 0 else lastX + it.dx - (it.width - lastW) / 2
            it.y = if (i == 0) 0 else lastY + it.dy - (it.height - lastH) / 2
            lastX = it.x
            lastY = it.y
            lastW = it.width
            lastH = it.height
            rect.set(
                it.x + (it.width * it.xa).roundToInt(),
                it.y + (it.height * it.ya).roundToInt(),
                it.x + (it.width * it.xb).roundToInt(),
                it.y + (it.height * it.yb).roundToInt()
            )
            minX = min(minX, rect.left)
            minY = min(minY, rect.top)
            maxX = max(rect.right, maxX)
            maxY = max(rect.bottom, maxY)

            it.over.left = max(rect.left, lastRect.left).toFloat()
            it.over.top = max(rect.top, lastRect.top).toFloat()
            it.over.right = min(rect.right, lastRect.right).toFloat()
            it.over.bottom = min(rect.bottom, lastRect.bottom).toFloat()
            lastRect.set(rect)

            it.shader = if (abs(it.dx) > abs(it.dy)) {
                if (it.dx > 0) {
                    if (it.a < it.b) LinearGradient(
                        it.over.left + (it.over.right - it.over.left) * it.a,
                        0f,
                        it.over.left + (it.over.right - it.over.left) * it.b,
                        0f,
                        Color.TRANSPARENT,
                        Color.WHITE,
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
                        Color.TRANSPARENT,
                        Color.WHITE,
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
                        Color.TRANSPARENT,
                        Color.WHITE,
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
                        Color.TRANSPARENT,
                        Color.WHITE,
                        Shader.TileMode.CLAMP
                    ) else {
                        it.over.top = it.over.bottom - (it.over.bottom - it.over.top) * it.a
                        null
                    }
                }
            }
        }
        clampScroll()
    }

    private val edgeEffectTop by lazy { EdgeEffect(context) }
    private val edgeEffectBottom by lazy { EdgeEffect(context) }
    private val edgeEffectLeft by lazy { EdgeEffect(context) }
    private val edgeEffectRight by lazy { EdgeEffect(context) }

    private fun clampScroll() {
        val oldX = scrollX
        val oldY = scrollY
        scrollX = max(minScrollX, min(maxScrollX, oldX.toFloat())).roundToInt()
        scrollY = max(minScrollY, min(maxScrollY, oldY.toFloat())).roundToInt()
    }

    override fun scrollBy(x: Int, y: Int) {
        super.scrollBy(x, y)
        clampScroll()
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private val lock = ReentrantLock()
    private var job: Job? = null
    var dirty = false

    @ObsoleteCoroutinesApi
    private val dispatcher by lazy { newSingleThreadContext("draw") }

    @ObsoleteCoroutinesApi
    @SuppressLint("DrawAllocation")
    override fun onDraw(c: Canvas) {
        c.translate(scrollX.toFloat(), scrollY.toFloat())
        val activity = context as? EditActivity
        val (transX, transY) = getTranslate()
        val scale = scale
        lock.withLock {
            if (dirty || lastDrawX != transX || lastDrawY != transY || lastDrawScale != scale) {
                dirty = false
                job?.cancel()
                job = GlobalScope.launch(dispatcher) {
                    if (!isActive) return@launch
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
                    lock.withLock {
                        val cache = cacheBitmap
                        cacheBitmap = cacheBitmap2
                        cacheBitmap2 = cache
                        lastDrawX = transX
                        lastDrawY = transY
                        lastDrawScale = scale
                    }
                    postInvalidate()
                }
            }
            c.withSave {
                val diff = scale / lastDrawScale
                c.translate(transX - lastDrawX * diff, transY - lastDrawY * diff)
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

        if (!edgeEffectTop.isFinished) {
            edgeEffectTop.setSize(width, height)
            if (edgeEffectTop.draw(c)) {
                postInvalidateOnAnimation()
            }
        }
        if (!edgeEffectBottom.isFinished) c.withSave {
            edgeEffectBottom.setSize(width, height)
            translate(width.toFloat(), height.toFloat())
            rotate(180f)
            if (edgeEffectBottom.draw(this)) {
                postInvalidateOnAnimation()
            }
        }
        if (!edgeEffectLeft.isFinished) c.withSave {
            edgeEffectLeft.setSize(height, width)
            translate(0f, height.toFloat())
            rotate(-90f)
            if (edgeEffectLeft.draw(this)) {
                postInvalidateOnAnimation()
            }
        }
        if (!edgeEffectRight.isFinished) c.withSave {
            edgeEffectRight.setSize(height, width)
            translate(width.toFloat(), 0f)
            rotate(90f)
            if (edgeEffectRight.draw(this)) {
                postInvalidateOnAnimation()
            }
        }

        // status bar
        val clr = (context as? Activity)?.window?.statusBarColor ?: Color.TRANSPARENT
        paint.color = clr and 0xffffff + 0xaa000000.toInt()
        c.drawRect(0f, 0f, width.toFloat(), paddingTop.toFloat(), paint)
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

    private val velocityTracker by lazy { VelocityTracker.obtain() }
    private val scroller by lazy { OverScroller(context) }

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            val oldX = scrollX
            val oldY = scrollY
            val newX = scroller.currX
            val newY = scroller.currY
            scrollTo(newX, newY)
            clampScroll()

            if (minScrollX.roundToInt() in newX until oldX) {
                edgeEffectLeft.onAbsorb(scroller.currVelocity.toInt())
            } else if (maxScrollX.roundToInt() in (oldX + 1)..newX) {
                edgeEffectRight.onAbsorb(scroller.currVelocity.toInt())
            }
            if (minScrollY.roundToInt() in newY until oldY) {
                edgeEffectTop.onAbsorb(scroller.currVelocity.toInt())
            } else if (maxScrollY.roundToInt() in (oldY + 1)..newY) {
                edgeEffectBottom.onAbsorb(scroller.currVelocity.toInt())
            }
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
                    velocityTracker.computeCurrentVelocity(1000)
                    val xvel = velocityTracker.xVelocity
                    val yvel = velocityTracker.yVelocity
                    scroller.fling(
                        scrollX,
                        scrollY,
                        (-xvel).roundToInt(),
                        (-yvel).roundToInt(),
                        minScrollX.roundToInt(),
                        maxScrollX.roundToInt(),
                        minScrollY.roundToInt(),
                        maxScrollY.roundToInt(),
                        width / 2,
                        height / 2
                    )
                    edgeEffectLeft.onRelease()
                    edgeEffectRight.onRelease()
                    edgeEffectTop.onRelease()
                    edgeEffectBottom.onRelease()
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
                    val oldX = scrollX
                    val oldY = scrollY
                    val dx = (lastTouchX - event.getX(index)).roundToInt()
                    val dy = (lastTouchY - event.getY(index)).roundToInt()

                    scrollBy(dx, dy)
                    lastTouchX = event.getX(index)
                    lastTouchY = event.getY(index)

                    val overX = (oldX + dx - scrollX).toFloat() / width
                    val overY = (oldY + dy - scrollY).toFloat() / height
                    if (overX < 0) {
                        edgeEffectLeft.onPull(overX)
                        edgeEffectRight.onRelease()
                    } else if (overX > 0) {
                        edgeEffectRight.onPull(overX)
                        edgeEffectLeft.onRelease()
                    }
                    if (overY < 0) {
                        edgeEffectTop.onPull(overY)
                        edgeEffectBottom.onRelease()
                    } else if (overY > 0) {
                        edgeEffectBottom.onPull(overY)
                        edgeEffectTop.onRelease()
                    }
                }
            }
        }
        return true
    }
}