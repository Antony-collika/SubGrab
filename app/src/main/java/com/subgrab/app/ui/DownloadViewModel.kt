package com.subgrab.app.ui
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.subgrab.app.data.*
import com.subgrab.app.data.repository.KnowledgeRepository
import com.subgrab.app.domain.*
import com.subgrab.app.service.DownloadWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface AnalysisState{
 data object Idle:AnalysisState
 data object Loading:AnalysisState
 data class Ready(val source:Source,val videos:List<VideoItem>,val folder:String,val persistenceWarning:String?=null):AnalysisState
 data class Error(val message:String,val retryUrl:String?=null):AnalysisState
}
class DownloadViewModel(
 private val context:Context,
 private val extractorClient:NewPipeExtractorClient,
 private val apiDiscovery:ApiDiscoveryClient,
 private val extractorDiscovery:ExtractorDiscoveryClient,
 private val settingsRepository:SettingsRepository,
 private val knowledgeRepository:KnowledgeRepository,
 private val orchestrator:DownloadOrchestrator?=null
):ViewModel(){
 private val _state=MutableStateFlow<AnalysisState>(AnalysisState.Idle);val state:StateFlow<AnalysisState> = _state.asStateFlow()
 private val control=DownloadControlStore(context.applicationContext);private val workManager=WorkManager.getInstance(context.applicationContext)
 val downloadState:StateFlow<DownloadState> =workManager.getWorkInfosForUniqueWorkFlow(DownloadWorker.UNIQUE_WORK).map{infos->
  val work=infos.firstOrNull{!it.state.isFinished} ?: infos.firstOrNull()
  work?.let{w->
   val state=w.toDownloadState(if(w.state.isFinished)w.outputData else w.progress)
   if(!w.state.isFinished && state is DownloadState.Idle) DownloadState.Running(0,1,"Đang chuẩn bị tải phụ đề",0,0, emptyList())
   else state
  }?:DownloadState.Idle
 }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),DownloadState.Idle)
 private var lastUrl:String?=null;private var lastKeyword:String?=null
 fun analyze(input:String, forceRefresh:Boolean=false){
  lastUrl=input
  val urls=YoutubeUrlParser.extractUrls(input)
  if(urls.isEmpty()){
   _state.value=AnalysisState.Error("Không tìm thấy URL YouTube hợp lệ",input)
   return
  }
  if(urls.size>1){
   if(urls.any{!YoutubeUrlParser.isVideoUrl(it)}){
    _state.value=AnalysisState.Error("Chuỗi nhiều URL chỉ hỗ trợ URL video YouTube",input)
    return
   }
   _state.value=AnalysisState.Loading
   viewModelScope.launch{
    val s=settingsRepository.current()
    val result=runCatching{
     if(s.useYouTubeDataApi) apiDiscovery.discoverVideoCollectionWithSource(urls)
     else extractorDiscovery.discoverVideoCollectionWithSource(urls)
    }
    result.onSuccess{(source,v)->
    val persistenceError=runCatching { knowledgeRepository.saveAnalysis(source,v,"ANALYZE_URL",s.metadataCacheHours,forceRefresh) }.exceptionOrNull()
    _state.value=AnalysisState.Ready(source,v,source.title,persistenceError?.let { "Phân tích thành công nhưng chưa lưu được dữ liệu vào database: ${it.message?:"lỗi không xác định"}" })
   }.onFailure{_state.value=AnalysisState.Error(it.message?:"Không thể phân tích chuỗi URL",input)}
   }
   return
  }

  val url=urls.single()
  if(!UrlValidator.isValid(url)){
   _state.value=AnalysisState.Error("Link không hợp lệ. Vui lòng kiểm tra lại",url)
   return
  }
  lastUrl=url
  _state.value=AnalysisState.Loading
  viewModelScope.launch{
   val s=settingsRepository.current()
   val cached = if (!forceRefresh) {
    knowledgeRepository.getFreshCachedAnalysis(url, s.metadataCacheHours)
   } else null
   val result = cached?.let { Result.success(it) } ?: runCatching{
    when {
     s.useYouTubeDataApi && YoutubeUrlParser.isPlaylistUrl(url) ->
      apiDiscovery.discoverPlaylistWithSource(url)
     s.useYouTubeDataApi && YoutubeUrlParser.isChannelUrl(url) ->
      apiDiscovery.discoverChannelWithSource(url)
     s.useYouTubeDataApi ->
      apiDiscovery.discoverVideo(url).let{v->Source(url,url,v.firstOrNull()?.title?:"YouTube video",v.size) to v}
     YoutubeUrlParser.isPlaylistUrl(url) ->
      extractorDiscovery.discoverPlaylistWithSource(url)
     YoutubeUrlParser.isChannelUrl(url) ->
      extractorDiscovery.discoverChannelWithSource(url)
     else ->
      extractorClient.extractSource(url).getOrThrow()
    }
   }
   result.onSuccess{(source,v)->
    val persistenceError=runCatching { knowledgeRepository.saveAnalysis(source,v,"ANALYZE_URL",s.metadataCacheHours,forceRefresh) }.exceptionOrNull()
    _state.value=AnalysisState.Ready(source,v,source.title,persistenceError?.let { "Phân tích thành công nhưng chưa lưu được dữ liệu vào database: ${it.message?:"lỗi không xác định"}" })
   }.onFailure{_state.value=AnalysisState.Error(it.message?:"Không thể phân tích link",url)}
  }
 }
 fun searchKeyword(keyword:String, forceRefresh:Boolean=false){
  lastKeyword=keyword
  _state.value=AnalysisState.Loading
  viewModelScope.launch{
   val s=settingsRepository.current()
   val cached = if (!forceRefresh) knowledgeRepository.getFreshCachedSearch(keyword, s.metadataCacheHours) else null
   if(cached != null){
    val (source,v)=cached
    val persistenceError=runCatching { knowledgeRepository.saveKeywordSearch(keyword.trim(),source,v,s.metadataCacheHours,forceRefresh) }.exceptionOrNull()
    _state.value=AnalysisState.Ready(source,v,source.title,persistenceError?.let { "Tìm kiếm thành công nhưng chưa lưu được dữ liệu vào database: " + (it.message ?: "lỗi không xác định") })
   } else if(s.useYouTubeDataApi){
    runCatching{apiDiscovery.discoverKeyword(keyword)}
     .map{v->
      val clean=keyword.trim()
      Source("keyword:"+clean,"https://www.youtube.com/results?search_query="+android.net.Uri.encode(clean),clean,v.size) to v
     }
     .onSuccess{(source,v)->
      val clean=keyword.trim()
      val persistenceError=runCatching { knowledgeRepository.saveKeywordSearch(clean,source,v,s.metadataCacheHours,forceRefresh) }.exceptionOrNull()
      _state.value=AnalysisState.Ready(source,v,clean,persistenceError?.let { "Tìm kiếm thành công nhưng chưa lưu được dữ liệu vào database: " + (it.message ?: "lỗi không xác định") })
     }
     .onFailure{_state.value=AnalysisState.Error(it.message?:"Không thể tìm video",null)}
   } else {
    runCatching{extractorDiscovery.discoverKeyword(keyword)}
     .map{v->Source("keyword:"+keyword.trim(),"https://www.youtube.com/results?search_query="+android.net.Uri.encode(keyword.trim()),keyword.trim(),v.size) to v}
     .onSuccess{(source,v)->
      val persistenceError=runCatching { knowledgeRepository.saveKeywordSearch(keyword.trim(),source,v,s.metadataCacheHours,forceRefresh) }.exceptionOrNull()
      _state.value=AnalysisState.Ready(source,v,source.title,persistenceError?.let { "Tìm kiếm thành công nhưng chưa lưu được dữ liệu vào database: " + (it.message ?: "lỗi không xác định") })
     }
     .onFailure{_state.value=AnalysisState.Error(it.message?:"Không thể tìm video",null)}
   }
  }
 }
 private fun isPlaylist(url:String)=url.contains("playlist",true)||url.contains("list=",true)
 private fun isChannel(url:String)=url.contains("/channel/",true)||url.contains("/c/",true)||url.contains("/@",true)
 fun retryAnalysis(){lastUrl?.let(::analyze)?:lastKeyword?.let(::searchKeyword)}
 fun refreshMetadata(){lastUrl?.let{analyze(it,true)}?:lastKeyword?.let{searchKeyword(it,true)}};fun resetAnalysis(){_state.value=AnalysisState.Idle}
 fun toggle(index:Int){val c=_state.value as? AnalysisState.Ready?:return;_state.value=c.copy(videos=c.videos.map{if(it.index==index&&(it.isSelected||c.videos.count{v->v.isSelected}<50)&&it.canSelect)it.copy(isSelected=!it.isSelected)else it})}
 fun selectAll(){val c=_state.value as? AnalysisState.Ready?:return;_state.value=c.copy(videos=c.videos.map{if(it.canSelect)it.copy(isSelected=true)else it})}
 fun clearSelection(){val c=_state.value as? AnalysisState.Ready?:return;_state.value=c.copy(videos=c.videos.map{it.copy(isSelected=false)})}
 fun updateFolder(folder:String){val c=_state.value as? AnalysisState.Ready?:return;_state.value=c.copy(folder=folder)}
 fun startDownload(settings:AppSettings,onEnqueued:()->Unit={}){val c=_state.value as? AnalysisState.Ready?:return;viewModelScope.launch{
  DownloadWorker.enqueueBatch(context,c.source,c.videos,c.folder,settings.toDownloadConfig())
  runCatching { knowledgeRepository.recordDownloadActivity(c.videos.filter { it.isSelected }, c.source.id) }
  onEnqueued()
 }}
 fun pauseDownload(){viewModelScope.launch{control.pause()}};fun resumeDownload(){viewModelScope.launch{control.resume()}};fun cancelDownload(){viewModelScope.launch{control.cancel()}}
 fun continueNextTask(){viewModelScope.launch{DownloadWorker.enqueueNext(context)}}
 private fun WorkInfo.toDownloadState(data:Data):DownloadState{
  val current=data.getInt(DownloadWorker.KEY_CURRENT,0);val total=data.getInt(DownloadWorker.KEY_TOTAL,0);val title=data.getString(DownloadWorker.KEY_TITLE).orEmpty()
  val taskIndex=data.getInt(DownloadWorker.KEY_TASK_INDEX,1);val totalTasks=data.getInt(DownloadWorker.KEY_TOTAL_TASKS,1)
  val saved=data.getInt(DownloadWorker.KEY_SAVED,0);val skipped=data.getInt(DownloadWorker.KEY_SKIPPED,0);val eta=data.getLong(DownloadWorker.KEY_ETA, -1L).takeIf{it>=0}
  val logs=data.getStringArray(DownloadWorker.KEY_LOGS)?.toList().orEmpty()
  val outputRelativePath=data.getString(DownloadWorker.KEY_OUTPUT_RELATIVE_PATH)?.takeIf{it.isNotBlank()}
  return when(data.getString(DownloadWorker.KEY_STATE)){
   "running"->DownloadState.Running(current,total,title,saved,skipped,logs,eta,taskIndex,totalTasks)
   "paused"->DownloadState.Paused(current,total,logs,eta,taskIndex,totalTasks)
   "done"->DownloadState.Done(saved,skipped,logs,outputRelativePath,taskIndex,totalTasks)
   "cancelled"->DownloadState.Cancelled(saved,logs,taskIndex,totalTasks)
   "error"->DownloadState.Error(saved,skipped,data.getString(DownloadWorker.KEY_MESSAGE).orEmpty(),logs,taskIndex,totalTasks)
   else->DownloadState.Idle
  }
 }
}
private fun AppSettings.toDownloadConfig()=DownloadConfig(languages,formats,preferManualSub,skipNoSub,outputDir,timestampMode,subtitleConcurrency,maxSubtitlesPerTask)