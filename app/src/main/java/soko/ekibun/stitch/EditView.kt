package soko.ekibun.stitch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceView
import android.view.VelocityTracker
import android.widget.EdgeEffect
import android.widget.OverScroller
import androidx.core.graphics.withSave
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

class EditView(context: Context, attrs: AttributeSet?) : SurfaceView(context, attrs) {
  private var bound = Rect()
  var scale = 0.8f

  private val minScrollY get() = bound.top * scale
  private val maxScrollY get() = max(minScrollY, bound.bottom * scale - height + paddingTop)
  private val minScrollX get() = bound.left * scale
  private val maxScrollX
    get() = max(
      minScrollX,
      bound.right * scale - width + paddingLeft + paddingRight
    )

  @Suppress("DEPRECATION")
  private val colorUnselected by lazy { resources.getColor(R.color.opaque) }

  @Suppress("DEPRECATION")
  private val colorSelected by lazy { resources.getColor(R.color.colorPrimary) }

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

  fun drawToBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(bound.width(), bound.height(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bitmap)
    c.translate(-bound.left.toFloat(), -bound.top.toFloat())
    runBlocking {
      project?.drawToCanvas(c, false, maskColor, overPaint, gradientPaint)
    }
    return bitmap
  }

  private fun getTranslate(): Array<Float> {
    clampScroll()
    val scale = scale
    val transX = max(0f, (width - paddingLeft - paddingRight - bound.width() * scale) / 2) -
        scrollx + paddingLeft
    val transY = max(0f, (height - paddingTop - bound.height() * scale) / 2) -
        scrolly + paddingTop
    return arrayOf(transX, transY)
  }

  private var lastDrawX = 0f
  private var lastDrawY = 0f
  private var lastDrawScale = 0f

  fun update() {
    bound = project?.updateInfo() ?: bound
    clampScroll()
  }

  private var job: Job? = null
  private val dispatcher by lazy { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }

  val project get() = (context as? EditActivity)?.project

  init {
    setZOrderOnTop(true)
    setWillNotDraw(false)
    holder.setFormat(PixelFormat.TRANSPARENT)
  }

  val dirty = AtomicBoolean(false)

  private fun redraw(transX: Float, transY: Float, scale: Float) {
    if (dirty.get() || lastDrawX != transX || lastDrawY != transY || lastDrawScale != scale) {
      dirty.set(false)
      lastDrawX = transX
      lastDrawY = transY
      lastDrawScale = scale
      job?.cancel()
      job = MainScope().launch(dispatcher) {
        if (!isActive) return@launch
        val c = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
          holder.lockHardwareCanvas() else null) ?: holder.lockCanvas() ?: return@launch
        c.save()
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        c.translate(transX, transY)
        c.scale(scale, scale)
        val project = project ?: return@launch
        project.drawToCanvas(c, true, maskColor, overPaint, gradientPaint)

        paint.strokeWidth = 3 * resources.displayMetrics.density / scale
        paint.textSize = 15 * resources.displayMetrics.density / scale
        val baseline = abs(paint.ascent() + paint.descent()) / 2
        val radius = 20 * resources.displayMetrics.density / scale

        project.stitchInfo.firstOrNull()?.let {
          val selected = project.selected.contains(it.imageKey)
          paint.color = if (selected) colorSelected else colorUnselected
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
        project.stitchInfo.reduceIndexedOrNull { i, acc, it ->
          val selected = project.selected.contains(it.imageKey)
          paint.color = if (selected) colorSelected else colorUnselected
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
        c.restore()
        // status bar
        val clr = (context as? Activity)?.window?.statusBarColor ?: Color.TRANSPARENT
        paint.color = clr and 0xffffff + 0xaa000000.toInt()
        c.drawRect(0f, 0f, width.toFloat(), paddingTop.toFloat(), paint)

        holder.unlockCanvasAndPost(c)
      }
    }
  }

  override fun onDraw(c: Canvas) {
    super.onDraw(c)
    val (transX, transY) = getTranslate()
    val scale = scale
    redraw(transX, transY, scale)

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

  private val edgeEffectTop by lazy { EdgeEffect(context) }
  private val edgeEffectBottom by lazy { EdgeEffect(context) }
  private val edgeEffectLeft by lazy { EdgeEffect(context) }
  private val edgeEffectRight by lazy { EdgeEffect(context) }

  var scrollx: Int = 0
  var scrolly: Int = 0

  private fun clampScroll() {
    val oldX = scrollx
    val oldY = scrolly
    scrollx = max(minScrollX, min(maxScrollX, oldX.toFloat())).roundToInt()
    scrolly = max(minScrollY, min(maxScrollY, oldY.toFloat())).roundToInt()
  }

  override fun computeScroll() {
    super.computeScroll()
    if (scroller.computeScrollOffset()) {
      val oldX = scrollx
      val oldY = scrolly
      val newX = scroller.currX
      val newY = scroller.currY
      scrollto(newX, newY)

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
          scrollby(
            ((detector.focusX + scrollx) * (scale - oldScale) / oldScale).roundToInt(),
            ((detector.focusY + scrolly) * (scale - oldScale) / oldScale).roundToInt(),
          )
          return super.onScale(detector)
        }
      })
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    scaleGestureDetector.onTouchEvent(event)
    velocityTracker.addMovement(event)
    val project = (context as? EditActivity)?.project
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
        project?.stitchInfo?.lastOrNull {
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
            scrollx,
            scrolly,
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
            if (!dragging) project?.updateUndo()
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
          val oldX = scrollx
          val oldY = scrolly
          val dx = (lastTouchX - event.getX(index)).roundToInt()
          val dy = (lastTouchY - event.getY(index)).roundToInt()

          scrollby(dx, dy)
          lastTouchX = event.getX(index)
          lastTouchY = event.getY(index)

          val overX = (oldX + dx - scrollx).toFloat() / width
          val overY = (oldY + dy - scrolly).toFloat() / height
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

  fun scrollto(x: Int, y: Int) {
    scrollx = x
    scrolly = y
    clampScroll()
    postInvalidate()
  }

  fun scrollby(dx: Int, dy: Int) {
    scrollto(scrollx + dx, scrolly + dy)
  }
}