package com.subgrab.app.ui
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.subgrab.app.data.*

class DownloadViewModelFactory(context:Context):ViewModelProvider.Factory{
 private val appContext=context.applicationContext
 private val settings=SettingsRepository(appContext)
 private val database=SubGrabDatabase.get(appContext)
 private val governor=RequestGovernor()
 private val pacer=RequestPacer(settings,governor,database)
 private val downloader=NewPipeDownloader(pacer)
 private val extractor=NewPipeExtractorClient(appContext,downloader)
 private val api=YouTubeDataApiClient(settings,pacer)
 private val apiDiscovery=ApiDiscoveryClient(api)
 private val extractorDiscovery=ExtractorDiscoveryClient(extractor)
 private val history=HistoryRepository(appContext)
 private val control=DownloadControlStore(appContext)
 private val subtitle=SubtitleDownloader(extractor,downloader)
 private val orchestrator=DownloadOrchestrator(extractor,subtitle,FileStorage(appContext),history,control)
 @Suppress("UNCHECKED_CAST")
 override fun <T:ViewModel> create(modelClass:Class<T>):T=DownloadViewModel(appContext,extractor,apiDiscovery,extractorDiscovery,settings,orchestrator) as T
}
