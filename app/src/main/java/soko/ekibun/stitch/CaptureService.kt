package soko.ekibun.stitch

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.Toast

class CaptureService : Service() {
    private var screenCapture: ScreenCapture? = null
    private val floatButton by lazy { FloatButton(this) }
    private val notification by lazy { Notification(this) }

    fun capture() {
        val bmp = screenCapture?.capture()
        if (bmp == null) {
            Toast.makeText(this, R.string.throw_error_capture, Toast.LENGTH_LONG).show()
            longClick()
            return
        }
        val key = App.bitmapCache.saveBitmap(bmp)
        val info = Stitch.StitchInfo(key, bmp.width, bmp.height)
        App.stitchInfo.add(info)
        notification.updateText()
    }

    fun longClick() {
        startActivity(Intent(this, EditActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        stopSelf()
    }


    fun updateOrientation() {
        screenCapture?.updateOrientation()
    }

    override fun onCreate() {
        App.foreground = true
        floatButton.run { }
        notification.run { }
    }

    override fun onDestroy() {
        super.onDestroy()
        App.foreground = false
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
        fun startService(context: Context) {
            if (App.foreground) return
            val intent = Intent(context, StartCaptureActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CaptureService::class.java)
            context.stopService(intent)
        }
    }
}