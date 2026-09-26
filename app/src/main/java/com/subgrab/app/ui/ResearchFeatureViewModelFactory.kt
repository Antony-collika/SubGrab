package com.subgrab.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.subgrab.app.data.*
import com.subgrab.app.data.repository.KnowledgeRepository
import com.subgrab.app.data.repository.ResearchRepository

class ResearchFeatureViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext
    private val settings = SettingsRepository(appContext)
    private val database = SubGrabDatabase.get(appContext)
    private val pacer = RequestPacer(settings, RequestGovernor.runtime(), database)
    private val downloader = NewPipeDownloader(pacer)
    private val extractor = NewPipeExtractorClient(appContext, downloader)
    private val api = YouTubeDataApiClient(settings, pacer)
    private val apiDiscovery = ApiDiscoveryClient(api)
    private val knowledge = KnowledgeRepository(database)
    private val repository = ResearchRepository(database)
    private val subtitleDownloader = SubtitleDownloader(extractor, downloader, knowledge)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(ResearchHistoryViewModel::class.java) -> ResearchHistoryViewModel(repository) as T
        modelClass.isAssignableFrom(ResearchSearchViewModel::class.java) -> ResearchSearchViewModel(repository) as T
        modelClass.isAssignableFrom(VideoDetailViewModel::class.java) ->
            VideoDetailViewModel(repository, subtitleDownloader, apiDiscovery, settings, knowledge) as T
        else -> error("Unsupported research ViewModel: " + modelClass.name)
    }
}
