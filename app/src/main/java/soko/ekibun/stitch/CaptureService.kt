package soko.ekibun.stitch

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class CaptureService : Service() {
    private var screenCapture: ScreenCapture? = null
    private val floatButton by lazy { FloatButton(this) }
    private val notification by lazy { Notification(this) }

    private var computing = 0

    fun capture() {
        val bmp = screenCapture?.capture()!!

        computing++
        GlobalScope.launch(Dispatchers.IO) {
            App.bitmapCache.saveBitmap(bmp)?.let { key ->
                val info = Stitch.StitchInfo(key, bmp.width, bmp.height)
                App.stitchInfo.add(info)
                notification.updateText()
            }
            computing--
        }
    }

    fun longClick() {
        while (computing > 0) try {
            Thread.sleep(100)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        startActivity(Intent(this, EditActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        stopSelf()
    }


    fun updateOrientation() {
        screenCapture?.updateOrientation()
    }

    override fun onCreate() {
        floatButton.run { }
        notification.run { }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        screenCapture?.destroy()
        floatButton.destroy()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when {
            intent.hasExtra("notifyClick") -> {
                stopSelf()
            }
            intent.hasExtra("captureData") -> {
                screenCapture = ScreenCapture(this, intent.getParcelableExtra("captureData")!!)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        @Suppress("DEPRECATION")
        fun isServiceRunning(context: Context): Boolean {
            val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val serviceList = activityManager.getRunningServices(Int.MAX_VALUE)
            if (serviceList.size <= 0) {
                return false
            }
            for (i in serviceList.indices) {
                val serviceInfo = serviceList[i]
                val serviceName = serviceInfo.service
                if (serviceName.className == CaptureService::class.java.name) {
                    return true
                }
            }
            return false
        }

        fun startService(context: Context) {
            if (isServiceRunning(context)) return
            val intent = Intent(context, StartCaptureActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            context.startActivity(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CaptureService::class.java)
            context.stopService(intent)
        }
    }
}