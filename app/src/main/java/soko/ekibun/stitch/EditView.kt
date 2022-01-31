package soko.ekibun.stitch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.widget.EdgeEffect
import android.widget.OverScroller
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.withRotation
import androidx.core.graphics.withSave
import kotlinx.coroutines.*
import java.util.concurrent.Executors
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
        val dstRange = RectF()
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
                it.cx + (srcRange.left - it.width / 2f) * it.scale,
                it.cy + (srcRange.top - it.height / 2f) * it.scale,
                it.cx + (srcRange.right - it.width / 2f) * it.scale,
                it.cy + (srcRange.bottom - it.height / 2f) * it.scale,
            )

            if (dstRange.left > c.clipBounds.right ||
                dstRange.right < c.clipBounds.left ||
                dstRange.top > c.clipBounds.bottom ||
                dstRange.bottom < c.clipBounds.top
            ) continue
            val bmp = App.bitmapCache.getBitmap(it.image) ?: return
            c.withRotation(it.rot, it.cx, it.cy) {
                path.reset()
                path.addRect(dstRange, Path.Direction.CW)
                it.shader?.let { shader ->
                    try {
                        gradientPaint.shader = shader
                        c.drawRect(dstRange, gradientPaint)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

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
                    scale = 0.5f.coerceAtLeast(beginScale * detector.scaleFactor)
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
        var cx = 0f
        var cy = 0f
        var rot = 0f
        var scale = 1f
        var lastPoints = listOf<PointF>()
        maxX = Int.MIN_VALUE
        maxY = Int.MIN_VALUE
        minX = Int.MAX_VALUE
        minY = Int.MAX_VALUE
        var cos = 1f
        var sin = 0f
        App.stitchInfo.forEachIndexed { i, it ->
            var dx = (it.dx * cos - it.dy * sin) * scale
            var dy = (it.dy * cos + it.dx * sin) * scale
            cx = if (i == 0) 0f else cx + dx
            cy = if (i == 0) 0f else cy + dy
            rot += it.drot
            scale *= it.dscale
            it.rot = rot
            it.scale = scale
            it.cx = cx
            it.cy = cy

            cos = cos(it.rot * Math.PI / 180).toFloat()
            sin = sin(it.rot * Math.PI / 180).toFloat()
            val l = it.width * (it.xa - 0.5f) * it.scale
            val t = it.height * (it.ya - 0.5f) * it.scale
            val r = it.width * (it.xb - 0.5f) * it.scale
            val b = it.height * (it.yb - 0.5f) * it.scale
            val points = listOf(
                PointF(cx + l * cos - t * sin, cy + l * sin + t * cos),
                PointF(cx + l * cos - b * sin, cy + l * sin + b * cos),
                PointF(cx + r * cos - t * sin, cy + r * sin + t * cos),
                PointF(cx + r * cos - b * sin, cy + r * sin + b * cos)
            )
            if (it.dx == 0f && it.dy == 0f) {
                dx = -sin
                dy = cos
            }
            val mag2 = dx * dx + dy * dy
            var minV = Float.MAX_VALUE
            var maxV = Float.MIN_VALUE
            for (p in points) {
                val prod = (cx - p.x) * dx + (cy - p.y) * dy
                minV = min(minV, prod)
                maxV = max(maxV, prod)

                minX = min(minX, p.x.roundToInt())
                minY = min(minY, p.y.roundToInt())
                maxX = max(maxX, p.x.roundToInt())
                maxY = max(maxY, p.y.roundToInt())
            }
            var minO = Float.MAX_VALUE
            var maxO = Float.MIN_VALUE
            for (p in lastPoints) {
                val prod = (cx - p.x) * dx + (cy - p.y) * dy
                minO = min(minO, prod)
                maxO = max(maxO, prod)
            }
            lastPoints = points

            minV = max(minV, minO) / mag2
            maxV = min(maxV, maxO) / mag2

            val va = maxV - (maxV - minV) * it.a
            val vb = maxV - (maxV - minV) * it.b * (1 + 1e-5f)

            it.shader = LinearGradient(
                cx - (dx * cos + dy * sin) * va,
                cy - (-dx * sin + dy * cos) * va,
                cx - (dx * cos + dy * sin) * vb,
                cy - (-dx * sin + dy * cos) * vb,
                Color.TRANSPARENT,
                Color.WHITE,
                Shader.TileMode.CLAMP
            )
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

    private val dispatcher by lazy { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }

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
                    it.cx,
                    it.cy,
                    radius, paint
                )
                paint.color = Color.WHITE
                c.drawText(
                    0.toString(), it.cx,
                    it.cy + baseline, paint
                )
            }
            App.stitchInfo.reduceIndexedOrNull { i, acc, it ->
                val selected = activity?.selected?.contains(it.key) ?: false
                paint.color = if (selected) colorWarn else colorPrimary
                c.drawCircle(
                    it.cx,
                    it.cy,
                    radius, paint
                )
                val dx = it.cx - acc.cx
                val dy = it.cy - acc.cy
                val lineOffset = sqrt(dx * dx.toDouble() + dy * dy).toFloat() / radius
                if (lineOffset > 2) c.drawLine(
                    acc.cx + dx / lineOffset,
                    acc.cy + dy / lineOffset,
                    it.cx - dx / lineOffset,
                    it.cy - dy / lineOffset,
                    paint
                )
                paint.color = Color.WHITE
                c.drawText(
                    i.toString(), it.cx,
                    it.cy + baseline, paint
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
                    abs(it.cx - x) < radius && abs(it.cy - y) < radius
                }?.let { hit ->
                    this.touching = hit
                    downOffsetX = x - hit.cx
                    downOffsetY = y - hit.cy
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
                    val x = (event.getX(index) - transX) / scale - downOffsetX
                    val y = (event.getY(index) - transY) / scale - downOffsetY
                    if (abs(initialTouchX - event.x) > 10 || abs(initialTouchY - event.y) > 10) {
                        if (!dragging) App.updateUndo()
                        dragging = true
                    }
                    if (dragging) {
                        val ddx = x - touching.cx
                        val ddy = y - touching.cy
                        val cos = cos((touching.rot - touching.drot) * Math.PI / 180).toFloat()
                        val sin = sin((touching.rot - touching.drot) * Math.PI / 180).toFloat()
                        val scale =
                            if (touching.dscale == 0f) 0f else touching.scale / touching.dscale
                        touching.dx =
                            touching.dx + if (scale == 0f) 0f else (ddx * cos + ddy * sin) / scale
                        touching.dy =
                            touching.dy + if (scale == 0f) 0f else (-ddx * sin + ddy * cos) / scale
                        (context as? EditActivity)?.updateSelectInfo()
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