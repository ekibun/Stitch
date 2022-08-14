package soko.ekibun.stitch

import android.graphics.*
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.withRotation
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.*

object Stitch {

  private val gson = Gson()

  data class StitchProject(
    val projectKey: String
  ) {
    val file by lazy {
      App.getProjectFile(projectKey)
    }
    val stitchInfo by lazy {
      val list = mutableListOf<StitchInfo>()
      if (file.exists()) runBlocking(App.dispatcherIO) {
        try {
          list.addAll(
            gson.fromJson<ArrayList<StitchInfo>>(
              file.readText(),
              object : TypeToken<ArrayList<StitchInfo>>() {}.type
            )
          )
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }
      list
    }

    private val stitchInfoBak by lazy {
      mutableListOf<StitchInfo>().apply { addAll(stitchInfo.map { it.clone() }) }
    }
    private val selectedBak by lazy {
      mutableSetOf<String>().apply { addAll(selected) }
    }
    private var undoTag: Any? = null
    fun clearUndoTag() {
      undoTag = null
    }

    fun updateUndo(tag: Any? = System.currentTimeMillis(), runBeforeSave: () -> Unit) {
      if (tag != null && tag != undoTag) {
        undoTag = tag
        selectedBak.clear()
        selectedBak.addAll(selected)
        stitchInfoBak.clear()
        stitchInfoBak.addAll(stitchInfo.map {
          it.clone()
        })
      }
      runBeforeSave()
      save()
    }

    var job: Job? = null
    private fun save() {
      runBlocking {
        job?.cancelAndJoin()
        job = launch(App.dispatcherIO) job@{
          val info = stitchInfo.toList()
          if (!file.exists()) {
            if (info.isNotEmpty()) file.parentFile?.mkdirs()
            else return@job
          }
          file.writeText(gson.toJson(info))
        }
      }
    }

    fun undo() {
      val last = stitchInfo.map { it.clone() }
      val lastSelect = selected.map { it }
      stitchInfo.clear()
      stitchInfo.addAll(stitchInfoBak)
      selected.clear()
      selected.addAll(selectedBak)
      selectedBak.clear()
      selectedBak.addAll(lastSelect)
      stitchInfoBak.clear()
      stitchInfoBak.addAll(last)
      save()
    }

    fun updateInfo(): Rect {
      var cx = 0f
      var cy = 0f
      var rot = 0f
      var scale = 1f
      var lastPoints = listOf<PointF>()
      val bound = Rect(
        Int.MAX_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MIN_VALUE
      )
      var cos = 1f
      var sin = 0f
      stitchInfo.forEachIndexed { i, it ->
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

          bound.left = min(bound.left, p.x.roundToInt())
          bound.top = min(bound.top, p.y.roundToInt())
          bound.right = max(bound.right, p.x.roundToInt())
          bound.bottom = max(bound.bottom, p.y.roundToInt())
        }
        var minO = Float.MAX_VALUE
        var maxO = Float.MIN_VALUE
        for (p in lastPoints) {
          val prod = (cx - p.x) * dx + (cy - p.y) * dy
          minO = min(minO, prod)
          maxO = max(maxO, prod)
        }
        lastPoints = points

        minV = max(minV, minO)
        maxV = max(minV, min(maxV, maxO))

        val va = maxV - (maxV - minV) * it.a
        val vb = maxV - (maxV - minV) * it.b - 0.01f * sqrt(mag2)

        it.shader = LinearGradient(
          cx - (dx * cos + dy * sin) * va / mag2,
          cy - (-dx * sin + dy * cos) * va / mag2,
          cx - (dx * cos + dy * sin) * vb / mag2,
          cy - (-dx * sin + dy * cos) * vb / mag2,
          Color.TRANSPARENT,
          Color.WHITE,
          Shader.TileMode.CLAMP
        )
      }
      return bound
    }

    val selected by lazy {
      mutableSetOf<String>()
    }

    fun drawToCanvas(
      c: Canvas,
      drawMask: Boolean,
      maskColor: Int,
      overPaint: Paint,
      gradientPaint: Paint,
    ) {
      val srcRange = Rect()
      val dstRange = RectF()
      val path = Path()
      for (i in 0 until stitchInfo.size) {
        val it = stitchInfo.getOrNull(i) ?: continue
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
        val bmp = App.bitmapCache.getBitmap(it.imageKey) ?: return
        c.withRotation(it.rot, it.cx, it.cy) {
          path.reset()
          path.addRect(dstRange, Path.Direction.CW)
          it.shader?.let { shader ->
            try {
              gradientPaint.shader = shader
              c.drawRect(dstRange, gradientPaint)
            } catch (e: Exception) {
            }
          }
          if (drawMask && selected.contains(it.imageKey)) {
            overPaint.color = maskColor
            c.drawRect(dstRange, overPaint)
          }
          overPaint.color = Color.BLACK
          c.drawBitmap(bmp, srcRange, dstRange, overPaint)
        }
      }
    }
  }

  data class StitchInfo(
    val imageKey: String,
    val width: Int,
    val height: Int,
    var dx: Float = 0f,
    var dy: Float = height / 2f,
    var drot: Float = 0f, // [-180, 180]
    var dscale: Float = 1f, // (-inf, +inf)
    var a: Float = 0.4f, // [0, b]
    var b: Float = 0.6f, // [a, 1]
    var xa: Float = 0f, // [0, xb]
    var xb: Float = 1f, // [xa, 1]
    var ya: Float = 0f, // [0, yb]
    var yb: Float = 1f, // [ya, 1]
  ) {
    @Transient
    var cx: Float = 0f

    @Transient
    var cy: Float = 0f

    @Transient
    var rot: Float = 0f

    @Transient
    var scale: Float = 1f

    @Transient
    var shader: LinearGradient? = null

    fun clone(): StitchInfo {
      return StitchInfo(
        imageKey, width, height, dx, dy, drot, dscale, a, b, xa, xb, ya, yb
      )
    }
  }

  init {
    System.loadLibrary("stitch")
  }

  private external fun phaseCorrelateNative(
    img0: Bitmap,
    img1: Bitmap,
    isDiff: Boolean,
    array: DoubleArray
  ): Boolean

  private external fun findHomographyNative(
    img0: Bitmap,
    img1: Bitmap,
    isDiff: Boolean,
    array: DoubleArray
  ): Boolean

  private fun getClipBitmap(img0: StitchInfo): Bitmap? {
    val left = (img0.xa * img0.width).roundToInt()
    val top = (img0.ya * img0.height).roundToInt()
    val right = (img0.xb * img0.width).roundToInt()
    val bottom = (img0.yb * img0.height).roundToInt()
    if (left >= right || top >= bottom) return null
    return App.bitmapCache.getBitmap(img0.imageKey)?.let {
      if (left == 0 && right == img0.width && top == 0 && bottom == img0.height) it
      else Bitmap.createBitmap(it, left, top, right - left, bottom - top)
    }
  }

  private fun getClipBitmap(img0: StitchInfo, img1: StitchInfo): Bitmap? {
    val left = (img0.xa * img0.width).roundToInt()
    val top = (img0.ya * img0.height).roundToInt()
    val right = (img0.xb * img0.width).roundToInt()
    val bottom = (img0.yb * img0.height).roundToInt()
    if (left >= right || top >= bottom) return null
    val width = (img1.xb * img1.width).roundToInt() - (img1.xa * img1.width).roundToInt()
    val height = (img1.yb * img1.height).roundToInt() - (img1.ya * img1.height).roundToInt()
    return App.bitmapCache.getBitmap(img0.imageKey)?.let {
      if (left == 0 && right == img0.width
        && top == 0 && bottom == img0.height
        && img0.width == width && img0.height == height
      ) it
      else Bitmap.createBitmap(max(img0.width, width), max(img0.height, height), it.config)
        .applyCanvas {
          drawBitmap(
            it,
            Rect(left, top, right, bottom),
            Rect(0, 0, right - left, bottom - top),
            null
          )
        }
    }
  }

  fun combine(homo: Boolean, diff: Boolean, img0: StitchInfo, img1: StitchInfo): StitchInfo? {
    val data = DoubleArray(9)
    return if (homo) {
      val bmp0 = getClipBitmap(img0) ?: return null
      val bmp1 = getClipBitmap(img1) ?: return null
      if (!findHomographyNative(bmp0, bmp1, diff, data)) return null
      img1.clone().also {
        val cx = (0.5 - img1.xa) * img1.width
        val cy = (0.5 - img1.ya) * img1.height
        val ps = data[6] * cx + data[7] * cy + data[8]
        val dx = ((data[0] * cx + data[1] * cy + data[2]) / ps -
            (0.5 - img0.xa) * img0.width).toFloat()
        val dy = ((data[3] * cx + data[4] * cy + data[5]) / ps -
            (0.5 - img0.ya) * img0.height).toFloat()
        val scale = sqrt(abs(data[0] * data[4] - data[1] * data[3]))
        val rot = atan2((data[3] - data[1]) / 2, (data[0] + data[4]) / 2) * 180 / Math.PI
        if ((dx != 0f || dy != 0f) &&
          abs(dx) < (img1.width + img0.width) / 2 &&
          abs(dy) < (img1.height + img0.height) / 2
        ) {
          it.dx = dx
          it.dy = dy
          it.drot = rot.toFloat()
          it.dscale = scale.toFloat()
        }
      }
    } else {
      val bmp0 = getClipBitmap(img0, img1) ?: return null
      val bmp1 = getClipBitmap(img1, img0) ?: return null
      if (!phaseCorrelateNative(bmp0, bmp1, diff, data)) return null
      img1.clone().also {
        val dx = (data[2] + (0.5 - img1.xa) * img1.width -
            (0.5 - img0.xa) * img0.width).toFloat()
        val dy = (data[5] + (0.5 - img0.ya) * img1.height -
            (0.5 - img0.ya) * img0.height).toFloat()
        if ((dx != 0f || dy != 0f) &&
          abs(dx) < (img1.width + img0.width) / 2 &&
          abs(dy) < (img1.height + img0.height) / 2
        ) {
          it.dx = dx
          it.dy = dy
          it.drot = 0f
          it.dscale = 1f
        }
      }
    }
  }
}