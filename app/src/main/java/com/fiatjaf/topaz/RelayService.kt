package com.fiatjaf.topaz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import backend.Backend

class RelayService : Service() {
  companion object {
    const val CHANNEL_ID = "relay_channel"
    const val NOTIFICATION_ID = 1
    const val ACTION_STOP = "com.fiatjaf.topaz.STOP_RELAY"

    fun start(context: Context) {
      val intent = Intent(context, RelayService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopRelayAndApp()
      return START_NOT_STICKY
    }

    val notification = createNotification()
    startForeground(NOTIFICATION_ID, notification)

    return START_STICKY
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
          NotificationChannel(
                  CHANNEL_ID,
                  "Relay Service",
                  NotificationManager.IMPORTANCE_LOW,
              )
              .apply {
                description = "keeps the relay running in the background"
                setShowBadge(false)
              }
      val manager = getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(channel)
    }
  }

  private fun createNotification(): Notification {
    val stopIntent = Intent(this, RelayService::class.java).apply { action = ACTION_STOP }
    val stopPendingIntent =
        PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("topaz relay running")
        // TODO: display first address with port
        .setContentText("")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "stop",
            stopPendingIntent,
        )
        .setContentIntent(stopPendingIntent)
        .build()
  }

  private fun stopRelayAndApp() {
    try {
      Backend.stopRelay()
    } catch (e: Exception) {
      // ignore
    }
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
    // Force stop the app
    @Suppress("DEPRECATION") android.os.Process.killProcess(android.os.Process.myPid())
  }
}
