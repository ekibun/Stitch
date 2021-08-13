package soko.ekibun.stitch

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.RectF

object Stitch {
    data class StitchInfo(
        val image: String,
        val width: Int,
        val height: Int,
        var dx: Int = 0,
        var dy: Int = height / 2,
        var a: Float = 0.4f, // [0, 1]
        var b: Float = 0.6f, // [0, 1]
    ) {
        var x: Int = 0
        var y: Int = 0
        var shader: LinearGradient? = null
        val over: RectF by lazy { RectF() }

        val key get() = System.identityHashCode(this)
    }

    init {
        System.loadLibrary("stitch")
    }

    external fun combineNative(img0: Bitmap, img1: Bitmap, array: DoubleArray): Boolean
}