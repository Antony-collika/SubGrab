package com.subgrab.app.ui
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.subgrab.app.data.*
import com.subgrab.app.domain.*
import com.subgrab.app.service.DownloadWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface AnalysisState{
 data object Idle:AnalysisState
 data object Loading:AnalysisState
 data class Ready(val source:Source,val videos:List<VideoItem>,val folder:String):AnalysisState
 data class Error(val message:String,val retryUrl:String?=null):AnalysisState
}
class DownloadViewModel(
 private val context:Context,
 private val extractorClient:NewPipeExtractorClient,
 private val apiDiscovery:ApiDiscoveryClient,
 private val extractorDiscovery:ExtractorDiscoveryClient,
 private val settingsRepository:SettingsRepository,
 private val orchestrator:DownloadOrchestrator?=null
):ViewModel(){
 private val _state=MutableStateFlow<AnalysisState>(AnalysisState.Idle);val state:StateFlow<AnalysisState> = _state.asStateFlow()
 private val control=DownloadControlStore(context.applicationContext);private val workManager=WorkManager.getInstance(context.applicationContext)
 val downloadState:StateFlow<DownloadState> =workManager.getWorkInfosForUniqueWorkFlow(DownloadWorker.UNIQUE_WORK).map{infos->
  infos.firstOrNull()?.let{w->w.toDownloadState(if(w.state.isFinished)w.outputData else w.progress)}?:DownloadState.Idle
 }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),DownloadState.Idle)
 private var lastUrl:String?=null;private var lastKeyword:String?=null
 fun analyze(url:String){lastUrl=url;if(!UrlValidator.isValid(url)){_state.value=AnalysisState.Error("Link không hợp lệ. Vui lòng kiểm tra lại",url);return};_state.value=AnalysisState.Loading;viewModelScope.launch{val s=settingsRepository.current();if(s.useYouTubeDataApi&&isPlaylist(url)){runCatching{apiDiscovery.discoverPlaylist(url)}.onSuccess{v->val source=Source(url,url,"YouTube playlist",v.size);_state.value=AnalysisState.Ready(source,v,source.title)}.onFailure{_state.value=AnalysisState.Error(it.message?:"Không thể phân tích playlist",url)}}else if(s.useYouTubeDataApi&&isChannel(url)){runCatching{apiDiscovery.discoverChannel(url)}.onSuccess{v->val source=Source(url,url,"YouTube channel",v.size);_state.value=AnalysisState.Ready(source,v,source.title)}.onFailure{_state.value=AnalysisState.Error(it.message?:"Không thể phân tích channel",url)}} else if(s.useYouTubeDataApi){runCatching{apiDiscovery.discoverVideo(url)}.onSuccess{v->val source=Source(url,url,v.firstOrNull()?.title?:"YouTube video",v.size);_state.value=AnalysisState.Ready(source,v,source.title)}.onFailure{_state.value=AnalysisState.Error(it.message?:"Không thể phân tích video",url)}} else runCatching{extractorDiscovery.discoverPlaylist(url)}.map{v->Source(url,url,"YouTube playlist",v.size) to v}.onSuccess{(source,v)->_state.value=AnalysisState.Ready(source,v,source.title)}.onFailure{_state.value=AnalysisState.Error(it.message?:"Không thể phân tích link",url)}}}
 fun searchKeyword(keyword:String){lastKeyword=keyword;_state.value=AnalysisState.Loading;viewModelScope.launch{val s=settingsRepository.current();if(s.useYouTubeDataApi)runCatching{apiDiscovery.discoverKeyword(keyword)}.onSuccess{v->_state.value=AnalysisState.Ready(Source("keyword:"+keyword.trim(), "https://www.youtube.com/results?search_query="+android.net.Uri.encode(keyword.trim()),keyword.trim(),v.size),v,"Search - "+keyword.trim())}.onFailure{_state.value=AnalysisState.Error(it.message?:"Không thể tìm video",null)} else runCatching{extractorDiscovery.discoverKeyword(keyword)}.map{v->Source("keyword:"+keyword.trim(),"https://www.youtube.com/results?search_query="+android.net.Uri.encode(keyword.trim()),keyword.trim(),v.size) to v}.onSuccess{(source,v)->_state.value=AnalysisState.Ready(source,v,"Search - "+keyword.trim())}.onFailure{_state.value=AnalysisState.Error(it.message?:"Không thể tìm video",null)}}}
 private fun isPlaylist(url:String)=url.contains("playlist",true)||url.contains("list=",true)
 private fun isChannel(url:String)=url.contains("/channel/",true)||url.contains("/c/",true)||url.contains("/@",true)
 fun retryAnalysis(){lastUrl?.let(::analyze)?:lastKeyword?.let(::searchKeyword)};fun resetAnalysis(){_state.value=AnalysisState.Idle}
 fun toggle(index:Int){val c=_state.value as? AnalysisState.Ready?:return;_state.value=c.copy(videos=c.videos.map{if(it.index==index&&(it.isSelected||c.videos.count{v->v.isSelected}<50)&&it.canSelect)it.copy(isSelected=!it.isSelected)else it})}
 fun selectAll(){val c=_state.value as? AnalysisState.Ready?:return;_state.value=c.copy(videos=c.videos.map{if(it.canSelect)it.copy(isSelected=true)else it})}
 fun clearSelection(){val c=_state.value as? AnalysisState.Ready?:return;_state.value=c.copy(videos=c.videos.map{it.copy(isSelected=false)})}
 fun updateFolder(folder:String){val c=_state.value as? AnalysisState.Ready?:return;_state.value=c.copy(folder=folder)}
 fun startDownload(settings:AppSettings,onEnqueued:()->Unit={}){val c=_state.value as? AnalysisState.Ready?:return;viewModelScope.launch{
  DownloadWorker.enqueueBatch(context,c.source,c.videos,c.folder,settings.toDownloadConfig())
  onEnqueued()
 }}
 fun pauseDownload(){viewModelScope.launch{control.pause()}};fun resumeDownload(){viewModelScope.launch{control.resume()}};fun cancelDownload(){viewModelScope.launch{control.cancel()}}
 fun continueNextTask(){viewModelScope.launch{DownloadWorker.enqueueNext(context)}}
 private fun WorkInfo.toDownloadState(data:Data):DownloadState{
  val current=data.getInt(DownloadWorker.KEY_CURRENT,0);val total=data.getInt(DownloadWorker.KEY_TOTAL,0);val title=data.getString(DownloadWorker.KEY_TITLE).orEmpty()
  val taskIndex=data.getInt(DownloadWorker.KEY_TASK_INDEX,1);val totalTasks=data.getInt(DownloadWorker.KEY_TOTAL_TASKS,1)
  val saved=data.getInt(DownloadWorker.KEY_SAVED,0);val skipped=data.getInt(DownloadWorker.KEY_SKIPPED,0);val eta=data.getLong(DownloadWorker.KEY_ETA, -1L).takeIf{it>=0}
  val logs=data.getStringArray(DownloadWorker.KEY_LOGS)?.toList().orEmpty()
  return when(data.getString(DownloadWorker.KEY_STATE)){
   "running"->DownloadState.Running(current,total,title,saved,skipped,logs,eta,taskIndex,totalTasks)
   "paused"->DownloadState.Paused(current,total,logs,eta,taskIndex,totalTasks)
   "done"->DownloadState.Done(saved,skipped,logs,taskIndex,totalTasks)
   "cancelled"->DownloadState.Cancelled(saved,logs,taskIndex,totalTasks)
   "error"->DownloadState.Error(saved,skipped,data.getString(DownloadWorker.KEY_MESSAGE).orEmpty(),logs,taskIndex,totalTasks)
   else->DownloadState.Idle
  }
 }
}
private fun AppSettings.toDownloadConfig()=DownloadConfig(languages,formats,preferManualSub,skipNoSub,outputDir,timestampMode,subtitleConcurrency)