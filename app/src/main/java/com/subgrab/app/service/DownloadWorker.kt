package com.subgrab.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.subgrab.app.MainActivity
import com.subgrab.app.data.DownloadControlStore
import com.subgrab.app.data.DownloadOrchestrator
import com.subgrab.app.data.DownloadState
import com.subgrab.app.data.DownloadTaskCodec
import com.subgrab.app.data.FileStorage
import com.subgrab.app.data.HistoryRepository
import com.subgrab.app.data.NewPipeExtractorClient
import com.subgrab.app.data.NewPipeDownloader
import com.subgrab.app.data.SubtitleDownloader
import java.util.concurrent.TimeUnit

class DownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val control = DownloadControlStore(appContext)
    private val history = HistoryRepository(appContext)

    override suspend fun doWork(): Result {
        val encoded = inputData.getString(KEY_TASK) ?: return Result.failure()
        val task = runCatching { DownloadTaskCodec.decode(encoded) }.getOrElse { return Result.failure() }
        val extractorClient = runCatching { NewPipeExtractorClient(applicationContext) }.getOrElse { return Result.failure() }
        val subtitleDownloader = SubtitleDownloader(extractorClient, NewPipeDownloader())
        val orchestrator = DownloadOrchestrator(extractorClient, subtitleDownloader, FileStorage(applicationContext), history, control)

        setForeground(createForegroundInfo("Đang chuẩn bị tải phụ đề", null, false))
        orchestrator.start(task.source, task.videos, task.folder, task.config) { state ->
            val progress = state.toData()
            setProgress(progress)
            setForeground(createForegroundInfo(state.notificationText(), state.progressPair(), state is DownloadState.Paused))
        }
        return Result.success()
    }

    private fun DownloadState.toData(): Data = Data.Builder()
        .putString(KEY_STATE, when (this) {
            DownloadState.Idle -> "idle"
            is DownloadState.Running -> "running"
            is DownloadState.Paused -> "paused"
            is DownloadState.Done -> "done"
            is DownloadState.Cancelled -> "cancelled"
        })
        .putInt(KEY_CURRENT, (this as? DownloadState.Running)?.current ?: (this as? DownloadState.Paused)?.current ?: 0)
        .putInt(KEY_TOTAL, (this as? DownloadState.Running)?.total ?: (this as? DownloadState.Paused)?.total ?: 0)
        .putString(KEY_TITLE, (this as? DownloadState.Running)?.title.orEmpty())
        .putInt(KEY_SAVED, when (this) {
            is DownloadState.Running -> this.saved
            is DownloadState.Done -> this.saved
            is DownloadState.Cancelled -> this.saved
            else -> 0
        })
        .putInt(KEY_SKIPPED, when (this) {
            is DownloadState.Running -> this.skipped
            is DownloadState.Done -> this.skipped
            else -> 0
        })
        .putStringArray(KEY_LOGS, when (this) {
            is DownloadState.Running -> this.logs.toTypedArray()
            is DownloadState.Paused -> this.logs.toTypedArray()
            is DownloadState.Done -> this.logs.toTypedArray()
            is DownloadState.Cancelled -> this.logs.toTypedArray()
            else -> emptyArray()
        })
        .build()

    private fun DownloadState.notificationText(): String = when (this) {
        DownloadState.Idle -> "Đang chuẩn bị tải phụ đề"
        is DownloadState.Running -> "${this.current}/${this.total} · ${this.title}"
        is DownloadState.Paused -> "Đã tạm dừng · ${this.current}/${this.total}"
        is DownloadState.Done -> "Hoàn tất · ${this.saved} file, bỏ qua ${this.skipped}"
        is DownloadState.Cancelled -> "Đã hủy · ${this.saved} file đã lưu"
    }

    private fun DownloadState.progressPair(): Pair<Int, Int>? = when (this) {
        is DownloadState.Running -> this.current to this.total
        is DownloadState.Paused -> this.current to this.total
        else -> null
    }

    private fun createForegroundInfo(text: String, progress: Pair<Int, Int>?, paused: Boolean): ForegroundInfo {
        createChannel()
        val openIntent = Intent(applicationContext, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(applicationContext, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("SubGrab")
            .setContentText(text)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { progress?.let { setProgress(it.second, it.first.coerceAtMost(it.second), false) } }
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "Đang tải phụ đề", NotificationManager.IMPORTANCE_LOW)
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val UNIQUE_WORK = "subgrab-download"
        const val KEY_TASK = "task"
        const val KEY_STATE = "state"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val KEY_TITLE = "title"
        const val KEY_SAVED = "saved"
        const val KEY_SKIPPED = "skipped"
        const val KEY_LOGS = "logs"
        private const val CHANNEL = "subgrab_download"
        private const val NOTIFICATION_ID = 41

        suspend fun enqueue(context: Context, source: com.subgrab.app.domain.Source, videos: List<com.subgrab.app.domain.VideoItem>, folder: String, config: com.subgrab.app.domain.DownloadConfig) {
            DownloadControlStore(context).reset()
            val input = Data.Builder().putString(KEY_TASK, DownloadTaskCodec.encode(source, videos, folder, config)).build()
            val request = OneTimeWorkRequest.Builder(DownloadWorker::class.java)
                .setInputData(input)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
