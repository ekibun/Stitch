package soko.ekibun.stitch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.*
import android.widget.RelativeLayout
import androidx.core.content.edit
import kotlin.math.abs
import kotlin.math.roundToInt

@SuppressLint("ClickableViewAccessibility", "RtlHardcoded")
class FloatButton(var service: CaptureService) {
  private var windowManager: WindowManager
  private var floatLayout: RelativeLayout?
  private var floatView: View
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
    wmParams.width = WindowManager.LayoutParams.WRAP_CONTENT
    wmParams.height = WindowManager.LayoutParams.WRAP_CONTENT
    val inflater = LayoutInflater.from(service.application)
    @SuppressLint("InflateParams")
    floatLayout = inflater.inflate(R.layout.float_button, null) as RelativeLayout

    floatView = floatLayout!!.findViewById(R.id.floatingActionButton)
    wmParams.gravity = Gravity.LEFT or Gravity.TOP
    wmParams.x = App.sp.getInt("float_x", Int.MAX_VALUE / 2)
    wmParams.y = App.sp.getInt("float_y", Int.MAX_VALUE / 2)
    windowManager.addView(floatLayout, wmParams)


    var downX = 0
    var downY = 0
    var isMove = false
    var clickTime: Long
    var isLongClick = false
    floatView.setOnTouchListener { _, event ->
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          floatView.parent.requestDisallowInterceptTouchEvent(true)
          downX = event.rawX.roundToInt()
          downY = event.rawY.roundToInt()
          isMove = false
          isLongClick = false
          val time = System.currentTimeMillis()
          clickTime = time
          floatView.postDelayed({
            if (time == clickTime) {
              floatLayout?.visibility = View.GONE
              service.longClick()
              isLongClick = true
            }
          }, ViewConfiguration.getLongPressTimeout().toLong())
        }
        MotionEvent.ACTION_MOVE -> {
          if (!isMove && abs(downX - event.rawX) + abs(downY - event.rawY) > 10) {
            clickTime = 0L
            isMove = true
            val rect = Rect()
            val pos = IntArray(2)
            floatLayout?.getWindowVisibleDisplayFrame(rect)
            floatLayout?.getLocationOnScreen(pos)
            downX = pos[0] - rect.left - downX
            downY = pos[1] - rect.top - downY
          }
          if (isMove) {
            wmParams.gravity = Gravity.LEFT or Gravity.TOP
            wmParams.x = event.rawX.roundToInt() + downX
            wmParams.y = event.rawY.roundToInt() + downY
            windowManager.updateViewLayout(floatLayout, wmParams)
          }
        }
        MotionEvent.ACTION_CANCEL,
        MotionEvent.ACTION_UP -> {
          clickTime = 0L
          if (!isMove && !isLongClick) {
            floatView.visibility = View.INVISIBLE
            service.updateOrientation()
            floatView.postDelayed({
              service.capture()
              floatView.visibility = View.VISIBLE
            }, 100)
          } else if (isMove) {
            App.sp.edit {
              putInt("float_x", wmParams.x)
              putInt("float_y", wmParams.y)
            }
          }
        }
      }
      true
    }
  }
}