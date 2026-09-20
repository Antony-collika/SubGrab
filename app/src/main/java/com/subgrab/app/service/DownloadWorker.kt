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
import com.subgrab.app.data.DownloadTaskStore
import com.subgrab.app.data.FileStorage
import com.subgrab.app.data.HistoryRepository
import com.subgrab.app.data.NewPipeExtractorClient
import com.subgrab.app.data.NewPipeDownloader
import com.subgrab.app.data.RequestGovernor
import com.subgrab.app.data.RequestPacer
import com.subgrab.app.data.SettingsRepository
import com.subgrab.app.data.SubGrabDatabase
import com.subgrab.app.data.SubtitleDownloader
import java.util.concurrent.TimeUnit

class DownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val control = DownloadControlStore(appContext)
    private val history = HistoryRepository(appContext)
    private val taskStore = DownloadTaskStore(appContext)

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID)
        val encoded = taskId?.let(taskStore::load) ?: inputData.getString(KEY_TASK)
        if (encoded.isNullOrBlank()) {
            taskId?.let(taskStore::delete)
            return Result.failure()
        }

        val task = runCatching { DownloadTaskCodec.decode(encoded) }.getOrElse {
            taskId?.let(taskStore::delete)
            return Result.failure()
        }
        val runtimeDb = SubGrabDatabase.get(applicationContext)
        runtimeDb.runtimeLogDao().insert(
            com.subgrab.app.data.RuntimeLogEntity(
                timestamp = System.currentTimeMillis(),
                level = "INFO",
                category = "TASK",
                lane = null,
                operation = null,
                message = "task start id=" + taskId + " index=" + task.taskIndex + "/" + task.totalTasks
            )
        )

        val settings = SettingsRepository(applicationContext)
        val database = SubGrabDatabase.get(applicationContext)
        val pacer = RequestPacer(settings, RequestGovernor(), database)
        val downloader = NewPipeDownloader(pacer)
        val extractorClient = runCatching {
            NewPipeExtractorClient(applicationContext, downloader)
        }.getOrElse {
            taskId?.let(taskStore::delete)
            return Result.failure()
        }
        val subtitleDownloader = SubtitleDownloader(extractorClient, downloader)
        val orchestrator = DownloadOrchestrator(
            extractorClient,
            subtitleDownloader,
            FileStorage(applicationContext),
            history,
            control
        )

        setForeground(createForegroundInfo("Đang chuẩn bị tải phụ đề", null, false))
        var finalState: DownloadState = DownloadState.Idle
        orchestrator.start(task.source, task.videos, task.folder, task.config) { state ->
            finalState = state
            val progress = state.toData()
            setProgress(progress)
            setForeground(createForegroundInfo(state.notificationText(), state.progressPair(), state is DownloadState.Paused))
        }

        runtimeDb.runtimeLogDao().insert(
            com.subgrab.app.data.RuntimeLogEntity(
                timestamp = System.currentTimeMillis(),
                level = if (finalState is DownloadState.Error) "ERROR" else "INFO",
                category = "TASK",
                lane = null,
                operation = null,
                message = "task finished status=" + finalState.javaClass.simpleName +
                    " index=" + task.taskIndex + "/" + task.totalTasks
            )
        )

        taskId?.let(taskStore::delete)
        val resultData = finalState.toData()
        return if (finalState is DownloadState.Error) Result.failure(resultData) else Result.success(resultData)
    }

    private fun DownloadState.toData(): Data = Data.Builder()
        .putString(KEY_STATE, when (this) {
            DownloadState.Idle -> "idle"
            is DownloadState.Running -> "running"
            is DownloadState.Paused -> "paused"
            is DownloadState.Done -> "done"
            is DownloadState.Cancelled -> "cancelled"
            is DownloadState.Error -> "error"
        })
        .putInt(KEY_CURRENT, (this as? DownloadState.Running)?.current ?: (this as? DownloadState.Paused)?.current ?: 0)
        .putInt(KEY_TOTAL, (this as? DownloadState.Running)?.total ?: (this as? DownloadState.Paused)?.total ?: 0)
        .putString(KEY_TITLE, (this as? DownloadState.Running)?.title.orEmpty())
        .putInt(KEY_SAVED, when (this) {
            is DownloadState.Running -> this.saved
            is DownloadState.Done -> this.saved
            is DownloadState.Cancelled -> this.saved
            is DownloadState.Error -> this.saved
            else -> 0
        })
        .putInt(KEY_SKIPPED, when (this) {
            is DownloadState.Running -> this.skipped
            is DownloadState.Done -> this.skipped
            is DownloadState.Error -> this.skipped
            else -> 0
        })
        .putLong(KEY_ETA, when (this) {
            is DownloadState.Running -> this.etaSeconds ?: -1L
            is DownloadState.Paused -> this.etaSeconds ?: -1L
            else -> -1L
        })
        .putString(KEY_MESSAGE, (this as? DownloadState.Error)?.message.orEmpty())
        .putStringArray(KEY_LOGS, logsForWorkData())
        .build()

    private fun DownloadState.logsForWorkData(): Array<String?> = when (this) {
        is DownloadState.Running -> logs
        is DownloadState.Paused -> logs
        is DownloadState.Done -> logs
        is DownloadState.Cancelled -> logs
        is DownloadState.Error -> logs
        else -> emptyList()
    }.takeLast(MAX_WORK_LOGS).map { it.take(MAX_WORK_LOG_CHARS) }.toTypedArray()

    private fun DownloadState.notificationText(): String = when (this) {
        DownloadState.Idle -> "Đang chuẩn bị tải phụ đề"
        is DownloadState.Running -> {
            val eta = this.etaSeconds?.let { " · còn khoảng " + formatEta(it) } ?: ""
            this.current.toString() + "/" + this.total + " · " + this.title + eta
        }
        is DownloadState.Paused -> "Đã tạm dừng · " + this.current + "/" + this.total
        is DownloadState.Done -> "Hoàn tất · " + this.saved + " file, bỏ qua " + this.skipped
        is DownloadState.Cancelled -> "Đã hủy · " + this.saved + " file đã lưu"
        is DownloadState.Error -> "Lỗi · " + this.message
    }

    private fun formatEta(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        val minutes = safe / 60
        val remaining = safe % 60
        return if (minutes > 0) minutes.toString() + " phút " + remaining + " giây" else remaining.toString() + " giây"
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
        const val KEY_TASK_ID = "task_id"
        const val KEY_TASK = "task"
        const val KEY_STATE = "state"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val KEY_TITLE = "title"
        const val KEY_SAVED = "saved"
        const val KEY_SKIPPED = "skipped"
        const val KEY_ETA = "eta_seconds"
        const val KEY_MESSAGE = "message"
        const val KEY_LOGS = "logs"
        private const val MAX_WORK_LOGS = 8
        private const val MAX_WORK_LOG_CHARS = 180
        private const val CHANNEL = "subgrab_download"
        private const val NOTIFICATION_ID = 41

        suspend fun enqueue(context: Context, source: com.subgrab.app.domain.Source, videos: List<com.subgrab.app.domain.VideoItem>, folder: String, config: com.subgrab.app.domain.DownloadConfig) {
            enqueueBatch(context, source, videos, folder, config)
        }

        suspend fun enqueueBatch(context: Context, source: com.subgrab.app.domain.Source, videos: List<com.subgrab.app.domain.VideoItem>, folder: String, config: com.subgrab.app.domain.DownloadConfig) {
            DownloadControlStore(context).reset()
            val selected = videos.filter { it.isSelected }.take(50)
            val groups = selected.chunked(10).ifEmpty { listOf(emptyList()) }
            val store = DownloadTaskStore(context)
            val ids = groups.mapIndexed { index, group ->
                store.save(DownloadTaskCodec.encode(source, group, folder, config, index + 1, groups.size), index + 1, groups.size)
            }
            enqueueTaskId(context, ids.first())
        }

        suspend fun enqueueNext(context: Context): Boolean {
            val store = DownloadTaskStore(context)
            val id = store.pendingIds().firstOrNull() ?: return false
            DownloadControlStore(context).reset()
            enqueueTaskId(context, id)
            return true
        }

        private fun enqueueTaskId(context: Context, taskId: String) {
            val input = Data.Builder().putString(KEY_TASK_ID, taskId).build()
            val request = OneTimeWorkRequest.Builder(DownloadWorker::class.java)
                .setInputData(input)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
