package com.subgrab.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.subgrab.app.data.DownloadOrchestrator
import com.subgrab.app.data.FileStorage
import com.subgrab.app.data.YtDlpRunner
import com.subgrab.app.service.DownloadServiceRegistry

class DownloadViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val runner = runCatching { YtDlpRunner(context) }.getOrNull()
    private val orchestrator = runner?.let { DownloadOrchestrator(it, FileStorage(context)) }
    init { DownloadServiceRegistry.orchestrator = orchestrator }
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = DownloadViewModel(runner, orchestrator) as T
}
