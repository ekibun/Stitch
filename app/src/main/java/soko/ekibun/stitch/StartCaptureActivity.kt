@file:Suppress("DEPRECATION")

package soko.ekibun.stitch

import android.app.Activity
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
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY) { //悬浮窗权限
            if (Build.VERSION.SDK_INT > 22 && Settings.canDrawOverlays(this)) {
                startCaptureService()
            } else {
                Toast.makeText(this, R.string.request_failed, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK) {
                // 获得权限，启动Service开始录制
                val service = Intent(this, CaptureService::class.java)
                service.putExtra("captureData", data)
                startService(service)
            } else {
                Toast.makeText(this, R.string.request_failed, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val policyVersion = getString(R.string.policy_version)
        if (sp.getString("policy_version", "") != policyVersion) {
            startActivity(Intent(this, EditActivity::class.java))
            finish()
        }
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