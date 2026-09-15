package com.subgrab.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.subgrab.app.MainActivity
import com.subgrab.app.data.DownloadOrchestrator

object DownloadServiceRegistry {
    var orchestrator: DownloadOrchestrator? = null
}

class DownloadService : LifecycleService() {
    companion object {
        const val ACTION_PAUSE = "com.subgrab.app.PAUSE"
        const val ACTION_RESUME = "com.subgrab.app.RESUME"
        const val ACTION_CANCEL = "com.subgrab.app.CANCEL"
        private const val CHANNEL = "subgrab_download"
        private const val NOTIFICATION_ID = 40
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Đang chuẩn bị tải phụ đề"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> DownloadServiceRegistry.orchestrator?.pause()
            ACTION_RESUME -> DownloadServiceRegistry.orchestrator?.resume()
            ACTION_CANCEL -> DownloadServiceRegistry.orchestrator?.cancel()
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "Đang tải phụ đề", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun action(label: String, action: String): NotificationCompat.Action {
        val intent = Intent(this, DownloadService::class.java).setAction(action)
        val pending = PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(0, label, pending).build()
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("SubGrab")
            .setContentText(text)
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(action("Tạm dừng", ACTION_PAUSE))
            .addAction(action("Hủy", ACTION_CANCEL))
            .build()
    }
}
