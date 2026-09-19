package com.subgrab.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.subgrab.app.data.DownloadControlStore
import com.subgrab.app.data.DownloadOrchestrator
import com.subgrab.app.data.FileStorage
import com.subgrab.app.data.HistoryRepository
import com.subgrab.app.data.YouTubeSearchClient
import com.subgrab.app.data.YtDlpRunner

class DownloadViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext
    private val runner = runCatching { YtDlpRunner(appContext) }.getOrNull()
    private val history = HistoryRepository(appContext)
    private val control = DownloadControlStore(appContext)
    private val orchestrator = runner?.let { DownloadOrchestrator(it, FileStorage(appContext), history, control) }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DownloadViewModel(appContext, runner, orchestrator, YouTubeSearchClient()) as T
}
