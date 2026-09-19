package com.subgrab.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.subgrab.app.data.DownloadControlStore
import com.subgrab.app.data.DownloadOrchestrator
import com.subgrab.app.data.NewPipeExtractorClient
import com.subgrab.app.data.NewPipeDownloader
import com.subgrab.app.data.SubtitleDownloader
import com.subgrab.app.data.FileStorage
import com.subgrab.app.data.HistoryRepository

class DownloadViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext
    private val extractorClient = NewPipeExtractorClient(appContext)
    private val history = HistoryRepository(appContext)
    private val control = DownloadControlStore(appContext)
    private val subtitleDownloader = SubtitleDownloader(extractorClient, NewPipeDownloader())
    private val orchestrator = DownloadOrchestrator(extractorClient, subtitleDownloader, FileStorage(appContext), history, control)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DownloadViewModel(appContext, extractorClient, orchestrator) as T
}
