package soko.ekibun.stitch

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.RectF

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
        val bound: RectF by lazy { RectF() }

        val key get() = System.identityHashCode(this)
    }

    init {
        System.loadLibrary("stitch")
    }

    external fun combineNative(img0: Bitmap, img1: Bitmap, array: DoubleArray): Boolean
}