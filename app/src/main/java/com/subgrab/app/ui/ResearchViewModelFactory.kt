package com.subgrab.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.subgrab.app.data.*
import com.subgrab.app.data.repository.KnowledgeRepository

class ResearchViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext
    private val settings = SettingsRepository(appContext)
    private val database = SubGrabDatabase.get(appContext)
    private val pacer = RequestPacer(settings, RequestGovernor.runtime(), database)
    private val downloader = NewPipeDownloader(pacer)
    private val extractor = NewPipeExtractorClient(appContext, downloader)
    private val api = YouTubeDataApiClient(settings, pacer)
    private val apiDiscovery = ApiDiscoveryClient(api)
    private val repository = KnowledgeRepository(database)
    private val subtitleDownloader = SubtitleDownloader(extractor, downloader, repository)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ResearchViewModel(subtitleDownloader, apiDiscovery, settings, repository) as T
}
