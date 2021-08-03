package soko.ekibun.stitch

import android.graphics.Bitmap
import android.graphics.Path
import android.util.Log
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.features2d.FlannBasedMatcher
import org.opencv.features2d.SIFT
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt


object Stitch {
    data class StitchInfo(
        val key: String,
        val width: Int,
        val height: Int,
        var dx: Float = 0f, // [-1, 1]
        var dy: Float = 1f, // [-1, 1]
        var trim: Float = 0.5f, // [0, 1]
    ) {
        var x: Int = 0
        var y: Int = 0
        val path: Path by lazy { Path() }
    }

    init {
        System.loadLibrary("opencv_java4")
    }

    private val sift by lazy { SIFT.create() }
    private val matcher by lazy { FlannBasedMatcher.create() }

    fun bitmapToMat(img: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(img, mat)
        return mat
    }

    fun combine(img0: Mat, img1: Mat, info: StitchInfo): Boolean {
        val data = byteArrayOf(1, 1, 1, 1, -8, 1, 1, 1, 1)
        val kernel = Mat(3, 3, CvType.CV_8S)
        kernel.put(0, 0, data)
        val matOfKp0 = MatOfKeyPoint()
        val matOfKp1 = MatOfKeyPoint()
        if (img0.width() == img1.width() && img0.height() == img1.height()) {
            val grad0 = Mat()
            val grad1 = Mat()
            Imgproc.filter2D(img0, grad0, CvType.CV_8U, kernel)
            Imgproc.filter2D(img1, grad1, CvType.CV_8U, kernel)
            val diff = Mat()
            Core.absdiff(grad0, grad1, diff)
            val diff0 = Mat()
            val diff1 = Mat()
            Core.bitwise_and(grad0, diff, diff0)
            Core.bitwise_and(grad1, diff, diff1)
            sift.detect(diff0, matOfKp0)
            sift.detect(diff1, matOfKp1)
        } else {
            sift.detect(img0, matOfKp0)
            sift.detect(img1, matOfKp1)
        }
        val desc0 = Mat()
        val desc1 = Mat()
        sift.compute(img0, matOfKp0, desc0)
        sift.compute(img1, matOfKp1, desc1)
        val matOfMatchPoints = mutableListOf<MatOfDMatch>()
        matcher.knnMatch(desc0, desc1, matOfMatchPoints, 2)
        val kp0 = matOfKp0.toArray()
        val kp1 = matOfKp1.toArray()
        Log.v("STITCH", "${matOfMatchPoints.size} matches")
        val matchPoints = matOfMatchPoints
            .mapNotNull {
                val (a, b) = it.toArray()
                val q0 = kp0[a.queryIdx].pt
                val q1 = kp0[b.queryIdx].pt
                val t0 = kp1[a.trainIdx].pt
                val t1 = kp1[b.trainIdx].pt
                if (a.distance < 0.7 * b.distance &&
                    q0.x - q1.x - t0.x + t1.x < 0.2 * b.distance &&
                    q0.y - q1.y - t0.y + t1.y < 0.2 * b.distance
                ) a else null
            }
        if (matchPoints.size < 10) return false
        val mat = Calib3d.findHomography(MatOfPoint2f().apply {
            fromList(matchPoints.map {
                kp1[it.trainIdx].pt
            })
        }, MatOfPoint2f().apply {
            fromList(matchPoints.map {
                kp0[it.queryIdx].pt
            })
        }, Calib3d.RANSAC)
        info.dx = mat[0, 2][0].toFloat() / img0.width()
        info.dy = mat[1, 2][0].roundToInt().toFloat() / img0.height()
        return true
    }
}