package soko.ekibun.stitch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.view.WindowManager

class ScreenCapture(private val context: Context, private val captureData: Intent) {
  private val mediaProjection by lazy {
    (context.applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(
      Activity.RESULT_OK,
      captureData
    )
  }
  private val wm by lazy {
    context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  }

  private var screenDensity = 0
  private var windowWidth = 0
  private var windowHeight = 0

  @SuppressLint("WrongConstant")
  fun updateOrientation() {
    val metric = DisplayMetrics()
    @Suppress("DEPRECATION")
    wm.defaultDisplay.getRealMetrics(metric)
    windowWidth = metric.widthPixels
    windowHeight = metric.heightPixels
    screenDensity = metric.densityDpi
    if (!this::imageReader.isInitialized || imageReader.width != windowWidth || imageReader.height != windowHeight) {
      imageReader = ImageReader.newInstance(windowWidth, windowHeight, 0x1, 1)
    }
    virtualDisplay?.let {
      it.surface = imageReader.surface
      it.resize(
        windowWidth,
        windowHeight,
        screenDensity
      )
    }
  }

  private lateinit var imageReader: ImageReader
  private var virtualDisplay: VirtualDisplay? = null

  fun destroy() {
    virtualDisplay?.release()
    virtualDisplay = null
    mediaProjection.stop()
  }

  fun capture(): Bitmap? {
    if (virtualDisplay == null) return null
    val image = imageReader.acquireLatestImage() ?: return null
    val width = image.width
    val height = image.height
    val planes = image.planes
    val buffer = planes[0].buffer
    val pixelStride = planes[0].pixelStride
    val rowStride = planes[0].rowStride
    val rowPadding = rowStride - pixelStride * width
    val bitmap =
      Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(buffer)
    image.close()
    return Bitmap.createBitmap(bitmap, 0, 0, windowWidth, windowHeight)
  }

  init {
    updateOrientation()
    virtualDisplay = mediaProjection.createVirtualDisplay(
      "capture_screen", windowWidth, windowHeight, screenDensity,
      DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, null
    )
  }
}