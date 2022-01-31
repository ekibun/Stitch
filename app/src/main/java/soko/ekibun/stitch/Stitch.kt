package soko.ekibun.stitch

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Rect
import android.util.Log
import androidx.core.graphics.applyCanvas
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

object Stitch {
  private val keyIndex = AtomicInteger(0)

  data class StitchInfo(
    val image: String,
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
    val key: Int = keyIndex.getAndIncrement()
  ) {
    var cx: Float = 0f
    var cy: Float = 0f
    var rot: Float = 0f
    var scale: Float = 1f
    var shader: LinearGradient? = null

    fun clone(): StitchInfo {
      return StitchInfo(
        image, width, height, dx, dy, drot, dscale, a, b, xa, xb, ya, yb, key
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
    return App.bitmapCache.getBitmap(img0.image)?.let {
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
    return App.bitmapCache.getBitmap(img0.image)?.let {
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

  enum class CombineMethod {
    PHASE_CORRELATE, FIND_HOMOGRAPHY, PHASE_CORRELATE_DIFF, FIND_HOMOGRAPHY_DIFF,
  }

  fun combine(method: CombineMethod, img0: StitchInfo, img1: StitchInfo): StitchInfo? {
    val data = DoubleArray(9)
    return when (method) {
      CombineMethod.PHASE_CORRELATE, CombineMethod.PHASE_CORRELATE_DIFF -> {
        val bmp0 = getClipBitmap(img0, img1) ?: return null
        val bmp1 = getClipBitmap(img1, img0) ?: return null
        if (!phaseCorrelateNative(
            bmp0,
            bmp1,
            method == CombineMethod.PHASE_CORRELATE_DIFF,
            data
          )
        ) return null
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
      CombineMethod.FIND_HOMOGRAPHY, CombineMethod.FIND_HOMOGRAPHY_DIFF -> {
        val bmp0 = getClipBitmap(img0) ?: return null
        val bmp1 = getClipBitmap(img1) ?: return null
        if (!findHomographyNative(
            bmp0,
            bmp1,
            method == CombineMethod.FIND_HOMOGRAPHY_DIFF,
            data
          )
        ) return null
        img1.clone().also {
          val cx = (0.5 - img1.xa) * img1.width
          val cy = (0.5 - img1.ya) * img1.height
          val ps = data[6] * cx + data[7] * cy + data[8]
          Log.v("HOMO", data.toList().toString())
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
      }
    }

  }
}