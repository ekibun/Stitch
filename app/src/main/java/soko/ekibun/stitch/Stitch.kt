package soko.ekibun.stitch

import android.graphics.Bitmap
import android.graphics.Path
import kotlin.math.roundToInt

object Stitch {
    data class StitchInfo(
        val image: String,
        val width: Int,
        val height: Int,
        var dx: Int = 0,
        var dy: Int = height,
        var trim: Float = 0.5f, // [0, 1]
    ) {
        var x: Int = 0
        var y: Int = 0
        val path: Path by lazy { Path() }

        val key get() = System.identityHashCode(this)
    }

    init {
        System.loadLibrary("stitch")
    }

    private external fun combineNative(img0: Bitmap, img1: Bitmap, array: DoubleArray): Boolean
    fun combine(img0: Bitmap, img1: Bitmap, info: StitchInfo): Boolean {
        val data = DoubleArray(9)
        if (combineNative(img0, img1, data)) {
            info.dx = data[2].roundToInt()
            info.dy = data[5].roundToInt()
            return true
        }
        return false
    }
}