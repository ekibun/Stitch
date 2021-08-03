package soko.ekibun.stitch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.RelativeLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton

class FloatButton(var service: CaptureService) {
    private var windowManager: WindowManager
    private var floatLayout: RelativeLayout?
    private var floatView: FloatingActionButton
    fun destroy() {
        if (floatLayout != null) {
            //移除悬浮窗口
            windowManager.removeView(floatLayout)
            floatLayout = null
        }
    }

    init {
        service.applicationContext.setTheme(R.style.Theme_Stitch_Transparent)
        val wmParams = WindowManager.LayoutParams()
        windowManager =
            service.application.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wmParams.type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        wmParams.format = PixelFormat.RGBA_8888
        wmParams.flags =
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        wmParams.gravity = Gravity.END or Gravity.BOTTOM
        wmParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        wmParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        val inflater = LayoutInflater.from(service.application)
        @SuppressLint("InflateParams")
        floatLayout = inflater.inflate(R.layout.float_button, null) as RelativeLayout?
        windowManager.addView(floatLayout, wmParams)
        floatView =
            floatLayout!!.findViewById<View>(R.id.floatingActionButton) as FloatingActionButton

        //点击
        floatView.setOnClickListener { // 截屏
            floatView.visibility = View.INVISIBLE
            service.updateOrientation()
            floatView.postDelayed({
                service.capture()
                floatView.visibility = View.VISIBLE
            }, 100)
        }
        //长按
        floatView.setOnLongClickListener {
            floatLayout?.visibility = View.GONE
            service.longClick()
            true
        }
    }
}