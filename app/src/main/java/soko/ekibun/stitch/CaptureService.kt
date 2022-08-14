package soko.ekibun.stitch

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast

class CaptureService : Service() {
  private var screenCapture: ScreenCapture? = null
  private val floatButton by lazy { FloatButton(this) }
  private val notification by lazy { Notification(this) }

  fun capture() {
    val bmp = screenCapture?.capture()
    val projectKey = App.captureProject
    if (bmp == null || projectKey == null) {
      Toast.makeText(this, R.string.throw_error_capture, Toast.LENGTH_LONG).show()
      stop()
      return
    }
    val key = App.bitmapCache.saveBitmap(projectKey, bmp)
    val info = Stitch.StitchInfo(key, bmp.width, bmp.height)
    val project = App.getProject(projectKey)
    project.updateUndo(screenCapture) {
      project.stitchInfo.add(info)
    }
    notification.updateText(project.stitchInfo.size)
  }

  fun longClick() {
    stop()
  }


  fun updateOrientation() {
    screenCapture?.updateOrientation()
  }

  override fun onCreate() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      startService(Intent(this, QuickTileService::class.java))
    }
    floatButton.run { }
    notification.run { }
  }

  private fun stop() {
    App.captureProject?.let {
      val project = App.getProject(it)
      if (project.stitchInfo.isNotEmpty()) {
        EditActivity.startActivity(this, it)
      }
    }
    stopSelf()
  }

  override fun onDestroy() {
    super.onDestroy()
    App.captureProject = null
    stopForeground(true)
    screenCapture?.destroy()
    floatButton.destroy()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      startService(Intent(this, QuickTileService::class.java))
    }
  }

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    when {
      intent.hasExtra("notifyClick") -> {
        stop()
      }
      intent.hasExtra("captureData") -> {
        val projectKey = intent.getStringExtra("project")!!
        App.captureProject = projectKey
        screenCapture = ScreenCapture(this, intent.getParcelableExtra("captureData")!!)
        notification.updateText(App.getProject(projectKey).stitchInfo.size)
      }
    }
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent): IBinder? {
    return null
  }

  companion object {
    fun startService(projectKey: String, context: Context) {
      if (App.captureProject != null) return
      val intent = StartCaptureActivity.startActivityIntent(context, projectKey)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
      context.startActivity(intent)
    }

    fun stopService(context: Context) {
      val intent = Intent(context, CaptureService::class.java)
      context.stopService(intent)
    }
  }
}