@file:Suppress("DEPRECATION")

package soko.ekibun.stitch

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class StartCaptureActivity : AppCompatActivity() {
    lateinit var requestCapture: ActivityResultLauncher<Unit>

    class CaptureContract : ActivityResultContract<Unit, Intent?>() {
        override fun createIntent(context: Context, input: Unit?): Intent =
            (context.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()

        override fun parseResult(resultCode: Int, intent: Intent?): Intent? {
            return if (resultCode == RESULT_OK) intent else null
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
        requestCapture = registerForActivityResult(
            CaptureContract()
        ) {
            // 获得权限，启动Service开始录制
            if (it != null) {
                val service = Intent(this, CaptureService::class.java)
                service.putExtra("captureData", it)
                startService(service)
            } else {
                Toast.makeText(this, R.string.request_failed, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
        startCaptureService()
    }

    private fun startCaptureService() {
        if (!requestPermission()) return
        requestCapture.launch(null)
    }

    private fun requestPermission(): Boolean {
        //悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.request_overlay, Toast.LENGTH_SHORT).show()
            registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                //悬浮窗权限
                if (Settings.canDrawOverlays(this)) {
                    //重新运行
                    startCaptureService()
                } else {
                    Toast.makeText(this, R.string.request_failed, Toast.LENGTH_SHORT)
                        .show()
                    finish()
                }
            }.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return false
        }
        return true
    }
}