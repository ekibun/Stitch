@file:Suppress("DEPRECATION")

package soko.ekibun.stitch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.widget.Toast

class StartCaptureActivity : Activity() {
  companion object {
    const val REQUEST_CAPTURE = 1
    const val REQUEST_OVERLAY = 2

    fun startActivityIntent(context: Context, projectKey: String): Intent {
      return Intent(context, StartCaptureActivity::class.java).apply {
        putExtra("project", projectKey)
      }
    }
  }

  private val projectKey by lazy { intent.extras!!.getString("project")!! }
  val project by lazy { App.getProject(projectKey) }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == REQUEST_OVERLAY) { //悬浮窗权限
      if (Build.VERSION.SDK_INT > 22 && Settings.canDrawOverlays(this)) {
        startCaptureService()
      } else {
        Toast.makeText(this, R.string.request_failed, Toast.LENGTH_SHORT).show()
        setResult(RESULT_CANCELED)
        finish()
      }
    }
    if (requestCode == REQUEST_CAPTURE) {
      if (resultCode == RESULT_OK) {
        // 获得权限，启动Service开始录制
        val service = Intent(this, CaptureService::class.java)
        service.putExtra("captureData", data)
        service.putExtra("project", projectKey)
        startService(service)
        setResult(RESULT_OK)
      } else {
        Toast.makeText(this, R.string.request_failed, Toast.LENGTH_SHORT).show()
        setResult(RESULT_CANCELED)
      }
      finish()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    startCaptureService()
  }

  private fun startCaptureService() {
    if (!requestPermission()) return
    val mediaProjectionManager =
      getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val permissionIntent = mediaProjectionManager.createScreenCaptureIntent()
    startActivityForResult(permissionIntent, REQUEST_CAPTURE)
  }

  private fun requestPermission(): Boolean {
    //悬浮窗权限
    if (Build.VERSION.SDK_INT > 22 && !Settings.canDrawOverlays(this)) {
      Toast.makeText(this, R.string.request_overlay, Toast.LENGTH_SHORT).show()
      val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:$packageName")
      )
      startActivityForResult(intent, REQUEST_OVERLAY)
      return false
    }
    return true
  }
}