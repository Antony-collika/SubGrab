package com.subgrab.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.subgrab.app.MainActivity
import com.subgrab.app.data.DownloadOrchestrator
import com.subgrab.app.data.DownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi

object DownloadServiceRegistry {
    private val _orchestrator = MutableStateFlow<DownloadOrchestrator?>(null)
    val orchestratorFlow = _orchestrator
    var orchestrator: DownloadOrchestrator?
        get() = _orchestrator.value
        set(value) { _orchestrator.value = value }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadService : LifecycleService() {
    private var terminalHandled = false
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
        startForeground(NOTIFICATION_ID, buildNotification("Đang chuẩn bị tải phụ đề", null, false, true))
        lifecycleScope.launch {
            DownloadServiceRegistry.orchestratorFlow.filterNotNull().flatMapLatest { it.state }.collect { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> DownloadServiceRegistry.orchestrator?.pause()
            ACTION_RESUME -> DownloadServiceRegistry.orchestrator?.resume()
            ACTION_CANCEL -> DownloadServiceRegistry.orchestrator?.cancel()
        }
        return START_STICKY
    }

    private fun updateNotification(state: DownloadState) {
        val (text, progress, paused, ongoing) = when (state) {
            DownloadState.Idle -> NotificationInfo("Đang chuẩn bị tải phụ đề", null, false, true)
            is DownloadState.Running -> NotificationInfo("${state.current}/${state.total} · ${state.title}", state.current to state.total, false, true)
            is DownloadState.Paused -> NotificationInfo("Đã tạm dừng · ${state.current}/${state.total}", state.current to state.total, true, true)
            is DownloadState.Done -> NotificationInfo("Hoàn tất · ${state.saved} file, bỏ qua ${state.skipped}", null, false, false)
            is DownloadState.Cancelled -> NotificationInfo("Đã hủy · ${state.saved} file đã lưu", null, false, false)
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text, progress, paused, ongoing))
        if (!ongoing && !terminalHandled) {
            terminalHandled = true
            lifecycleScope.launch {
                delay(1500)
                stopForegroundCompat()
                stopSelf()
            }
        }
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

    private fun buildNotification(text: String, progress: Pair<Int, Int>?, paused: Boolean, ongoing: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("SubGrab")
            .setContentText(text)
            .setContentIntent(openPending)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
        progress?.let { (current, total) -> builder.setProgress(total, current.coerceAtMost(total), false) }
        if (ongoing) builder.addAction(if (paused) action("Tiếp tục", ACTION_RESUME) else action("Tạm dừng", ACTION_PAUSE)).addAction(action("Hủy", ACTION_CANCEL))
        else builder.addAction(openDownloadsAction())
        builder.setAutoCancel(!ongoing)
        return builder.build()
    }

    private fun openDownloadsAction(): NotificationCompat.Action {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://downloads/my_downloads")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(this, 90, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(0, "Mở Downloads", pending).build()
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_DETACH) else stopForeground(false)
    }

    private data class NotificationInfo(val text: String, val progress: Pair<Int, Int>?, val paused: Boolean, val ongoing: Boolean)
}
