package soko.ekibun.stitch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class Notification(private val service: CaptureService) {
  private val notifyID = 5875

  private val manager by lazy { service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
  private val builder by lazy {
    val notificationIntent = Intent(
      service,
      CaptureService::class.java
    )
    notificationIntent.putExtra("notifyClick", true)

    val pendingIntent = PendingIntent.getService(
      service.applicationContext, 0, notificationIntent,
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    )
    if (Build.VERSION.SDK_INT > 25) {
      val channel =
        NotificationChannel("stitch", "Stitch", NotificationManager.IMPORTANCE_MIN)
      manager.createNotificationChannel(channel)
    }
    NotificationCompat.Builder(service.applicationContext, "stitch")
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .setSmallIcon(R.drawable.ic_tile)
      .setColor(ContextCompat.getColor(service.applicationContext, R.color.colorPrimary))
      .setContentTitle(service.getString(R.string.tile_label))
  }

  init {
    service.startForeground(notifyID, builder.build())
  }

  fun updateText(size: Int) {
    builder.setContentText(service.resources.getQuantityString(R.plurals.notify_text, size, size))
    manager.notify(notifyID, builder.build())
  }
}