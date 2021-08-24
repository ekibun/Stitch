package soko.ekibun.stitch

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.RectF
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt

object Stitch {
    private val keyIndex = AtomicInteger(0)

    data class StitchInfo(
        val image: String,
        val width: Int,
        val height: Int,
        var dx: Int = 0,
        var dy: Int = height / 2,
        var a: Float = 0.4f, // [0, b]
        var b: Float = 0.6f, // [a, 1]
        var xa: Float = 0f, // [0, xb]
        var xb: Float = 1f, // [xa, 1]
        var ya: Float = 0f, // [0, yb]
        var yb: Float = 1f, // [ya, 1]
        val key: Int = keyIndex.getAndIncrement()
    ) {
        var x: Int = 0
        var y: Int = 0
        var shader: LinearGradient? = null
        val over: RectF by lazy { RectF() }

        fun clone(): StitchInfo {
            return StitchInfo(
                image, width, height, dx, dy, a, b, xa, xb, ya, yb, key
            )
        }
    }

    init {
        System.loadLibrary("stitch")
    }

    private external fun combineNative(img0: Bitmap, img1: Bitmap, array: DoubleArray): Boolean

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

    fun combine(img0: StitchInfo, img1: StitchInfo): StitchInfo? {
        val data = DoubleArray(9)
        val bmp0 = getClipBitmap(img0) ?: return null
        val bmp1 = getClipBitmap(img1) ?: return null
        if (!combineNative(bmp0, bmp1, data)) return null
        return img1.clone().also {
            val dx = (data[2] + (0.5 - img1.xa) * img1.width -
                    (0.5 - img0.xa) * img0.width).roundToInt()
            val dy = (data[5] + (0.5 - img0.ya) * img1.height -
                    (0.5 - img0.ya) * img0.height).roundToInt()
            if ((dx != 0 || dy != 0) &&
                abs(dx) < (img1.width + img0.width) / 2 &&
                abs(dy) < (img1.height + img0.height) / 2
            ) {
                it.dx = dx
                it.dy = dy
            }
        }
    }
}