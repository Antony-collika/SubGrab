package com.subgrab.app.ui
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.subgrab.app.data.*
import com.subgrab.app.data.repository.KnowledgeRepository
import com.subgrab.app.domain.*
import com.subgrab.app.service.DownloadWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface AnalysisState{
 data object Idle:AnalysisState
 /** loaded > 0: đã lấy xong trang đầu và đang lấy các trang kế tiếp (hiện thanh tiến trình + nút Dừng). */
 data class Loading(val loaded:Int=0,val total:Int?=null):AnalysisState
 data class Ready(
  val source:Source,
  val videos:List<VideoItem>,
  val folder:String,
  val persistenceWarning:String?=null,
  /** Tổng số video mà nguồn báo cho biết (null nếu không biết). */
  val total:Int?=null,
  /** Còn video chưa lấy (có thể bấm Tải thêm). */
  val hasMore:Boolean=false,
  val loadingMore:Boolean=false,
  /** Kết quả lấy từ bộ nhớ đệm: không biết còn nữa hay không, cần Làm mới để lấy đầy đủ. */
  val fromCache:Boolean=false,
  val isKeyword:Boolean=false,
  /** Cảnh báo chi phí cần xác nhận trước khi Tải thêm (từ khóa dùng API). */
  val loadMoreCost:String?=null,
  /** Thông báo kèm theo (ví dụ: dừng giữa chừng vì lỗi). */
  val notice:String?=null
 ):AnalysisState
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
 private var loadJob:Job?=null
 private var moreLoader:(suspend ()->DiscoveryPage)?=null
 @Volatile private var stopRequested=false
 /** Dừng việc lấy thêm trang đang chạy (giữ nguyên phần đã lấy). */
 fun stopLoading(){stopRequested=true}
 fun openRecent(item: RecentItem) {
  lastUrl = if (item.kind == RecentKind.KEYWORD) null else item.input
  lastKeyword = if (item.kind == RecentKind.KEYWORD) item.input else null
  loadJob?.cancel()
  moreLoader = null
  stopRequested = false
  loadJob = viewModelScope.launch {
   _state.value = AnalysisState.Loading()
   runCatching { knowledgeRepository.loadRecent(item) }
    .onSuccess { loaded ->
      if (loaded == null) _state.value = AnalysisState.Error("Dữ liệu của mục Gần đây không còn trong thư viện", item.input)
      else {
       val (source, videos) = loaded
       _state.value = AnalysisState.Ready(source, videos, source.title, total = videos.size, hasMore = false, fromCache = true, isKeyword = item.kind == RecentKind.KEYWORD)
      }
    }
    .onFailure { _state.value = AnalysisState.Error(it.message ?: "Không thể mở dữ liệu đã lưu", item.input) }
  }
 }
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
   _state.value=AnalysisState.Loading()
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
  startDiscovery(url,forceRefresh)
 }
 private fun startDiscovery(url:String,forceRefresh:Boolean){
  loadJob?.cancel()
  stopRequested=false
  moreLoader=null
  _state.value=AnalysisState.Loading()
  loadJob=viewModelScope.launch{
   val s=settingsRepository.current()
   val isPlaylist=YoutubeUrlParser.isPlaylistUrl(url)
   val isChannel=YoutubeUrlParser.isChannelUrl(url)
   val paged=isPlaylist||isChannel
   // Kênh/playlist luôn lấy lại trang đầu để biết còn nữa hay không; bộ nhớ đệm chỉ dùng cho video lẻ.
   val cached=if(!forceRefresh&&!paged) knowledgeRepository.getFreshCachedAnalysis(url,s.metadataCacheHours) else null
   val result:Result<Pair<Source,DiscoveryPage>> = cached?.let{Result.success(it.first to DiscoveryPage(it.second))} ?: runCatching{
    when{
     s.useYouTubeDataApi&&isPlaylist->apiDiscovery.discoverPlaylistPaged(url)
     s.useYouTubeDataApi&&isChannel->apiDiscovery.discoverChannelPaged(url)
     s.useYouTubeDataApi->apiDiscovery.discoverVideo(url).let{v->Source(url,url,v.firstOrNull()?.title?:"YouTube video",v.size) to DiscoveryPage(v)}
     isPlaylist->extractorClient.discoverPlaylistPaged(url).getOrThrow()
     isChannel->extractorClient.discoverChannelPaged(url).getOrThrow()
     else->extractorClient.extractSource(url).getOrThrow().let{(src,v)->src to DiscoveryPage(v)}
    }
   }
   result.onSuccess{(source,first)->
    deliver(source,first,autoLimit=s.videosPerSource,autoPage=paged,s=s,folder=source.title,keyword=null,forceRefresh=forceRefresh)
   }.onFailure{_state.value=AnalysisState.Error(it.message?:"Không thể phân tích link",url)}
  }
 }
 /** Gom thêm trang (nếu cần), lưu vào máy rồi chuyển sang màn kết quả. */
 private suspend fun deliver(source:Source,first:DiscoveryPage,autoLimit:Int,autoPage:Boolean,s:AppSettings,folder:String,keyword:String?,forceRefresh:Boolean,fromCache:Boolean=false){
  var latest=first
  var notice:String?=null
  if(autoPage&&first.hasMore&&(autoLimit<=0||first.videos.size<autoLimit)){
   _state.value=AnalysisState.Loading(first.videos.size,first.total)
   try{
    latest=first.collectUntil(
     limit=autoLimit,
     shouldStop={stopRequested},
     onPage={page->
      latest=page
      _state.value=AnalysisState.Loading(page.videos.size,page.total)
     }
    )
   }catch(e:CancellationException){throw e}
   catch(e:Throwable){notice="Dừng ở ${latest.videos.size} video: "+(e.message?:"lỗi không xác định")}
  }
  moreLoader=latest.loadNext
  val videos=latest.videos
  val finalSource=source.copy(originalTotalVideos=videos.size)
  val saveError=runCatching{
   if(keyword!=null) knowledgeRepository.saveKeywordSearch(keyword,finalSource,videos,s.metadataCacheHours,forceRefresh)
   else knowledgeRepository.saveAnalysis(finalSource,videos,"ANALYZE_URL",s.metadataCacheHours,forceRefresh)
  }.exceptionOrNull()
  val prefix=if(keyword!=null)"Tìm kiếm" else "Phân tích"
  _state.value=AnalysisState.Ready(
   source=finalSource,
   videos=videos,
   folder=folder,
   persistenceWarning=saveError?.let{"$prefix thành công nhưng chưa lưu được dữ liệu vào database: "+(it.message?:"lỗi không xác định")},
   total=latest.total,
   hasMore=latest.hasMore,
   fromCache=fromCache,
   isKeyword=keyword!=null,
   loadMoreCost=if(keyword!=null&&s.useYouTubeDataApi)"Mỗi lần tải thêm kết quả tìm kiếm tốn khoảng 100 đơn vị hạn mức YouTube API (mặc định 10.000 đơn vị/ngày)." else null,
   notice=notice
  )
 }
 /** Lấy thêm video: kênh/playlist theo cài đặt "Video mỗi nguồn"; từ khóa luôn đúng 1 trang. */
 fun loadMore(){
  val c=_state.value as? AnalysisState.Ready?:return
  val loader=moreLoader?:return
  if(c.loadingMore)return
  stopRequested=false
  _state.value=c.copy(loadingMore=true,notice=null)
  loadJob=viewModelScope.launch{
   val s=settingsRepository.current()
   val want=when{c.isKeyword->1;s.videosPerSource<=0->0;else->s.videosPerSource}
   var latest=DiscoveryPage(emptyList(),c.total,loader)
   var notice:String?=null
   try{
    latest=latest.collectUntil(
     limit=want,
     shouldStop={stopRequested},
     onPage={page->
      latest=page
      appendFetched(page)
     }
    )
   }catch(e:CancellationException){throw e}
   catch(e:Throwable){notice="Dừng khi tải thêm: "+(e.message?:"lỗi không xác định")}
   moreLoader=latest.loadNext
   val added=latest.videos
   val saveError=if(added.isEmpty())null else runCatching{
    knowledgeRepository.saveAnalysis(c.source,added,"ANALYZE_URL",s.metadataCacheHours,false)
   }.exceptionOrNull()
   if(saveError!=null)notice=(notice?.plus("\n")?:"")+"Đã lấy thêm nhưng chưa lưu được dữ liệu: "+(saveError.message?:"lỗi không xác định")
   _state.update{cur->
    val ready=(cur as? AnalysisState.Ready)?:return@update cur
    ready.copy(loadingMore=false,hasMore=latest.hasMore,total=latest.total?:ready.total,notice=notice)
   }
  }
 }
 /** Nối các video mới vào danh sách đang hiển thị, giữ nguyên các video đã được chọn. */
 private fun appendFetched(page:DiscoveryPage){
  _state.update{cur->
   val ready=(cur as? AnalysisState.Ready)?:return@update cur
   val known=ready.videos.map{it.videoId}.toHashSet()
   val fresh=page.videos.filter{it.videoId !in known}.mapIndexed{i,v->v.copy(index=ready.videos.size+i+1)}
   // Trước đây: ready.videos+fresh dùng toán tử "+" giữa 2 List, luôn cấp phát một mảng mới
   // chứa bản sao của TOÀN BỘ phần tử cũ cộng phần tử mới. Bây giờ: dựng sẵn ArrayList đúng
   // kích thước cần, rồi addAll một lần — vẫn ra đúng kết quả nhưng đỡ tốn cấp phát hơn khi
   // danh sách đã có sẵn vài trăm video.
   val merged=ArrayList<VideoItem>(ready.videos.size+fresh.size)
   merged.addAll(ready.videos)
   merged.addAll(fresh)
   ready.copy(videos=merged,total=page.total?:ready.total,hasMore=page.hasMore)
  }
 }
 fun searchKeyword(keyword:String, forceRefresh:Boolean=false){
  lastKeyword=keyword
  loadJob?.cancel()
  stopRequested=false
  moreLoader=null
  _state.value=AnalysisState.Loading()
  loadJob=viewModelScope.launch{
   val s=settingsRepository.current()
   val clean=keyword.trim()
   val cached=if(!forceRefresh) knowledgeRepository.getFreshCachedSearch(keyword,s.metadataCacheHours) else null
   if(cached!=null){
    val (source,v)=cached
    // Dữ liệu lưu tạm: không biết còn nữa hay không, nên không hiện nút Tải thêm (bấm Làm mới để lấy lại).
    deliver(source,DiscoveryPage(v),autoLimit=0,autoPage=false,s=s,folder=source.title,keyword=clean,forceRefresh=forceRefresh,fromCache=true)
    return@launch
   }
   // Từ khóa: chỉ lấy 1 trang đầu, không tự lật trang (mỗi lần tìm bằng API tốn khoảng 100 đơn vị hạn mức).
   val result:Result<Pair<Source,DiscoveryPage>> = runCatching{
    if(s.useYouTubeDataApi) apiDiscovery.discoverKeywordPaged(clean)
    else extractorClient.searchPaged(clean).getOrThrow()
   }
   result.onSuccess{(source,page)->
    deliver(source,page,autoLimit=0,autoPage=false,s=s,folder=clean,keyword=clean,forceRefresh=forceRefresh)
   }.onFailure{_state.value=AnalysisState.Error(it.message?:"Không thể tìm video",null)}
  }
 }
 private fun isPlaylist(url:String)=url.contains("playlist",true)||url.contains("list=",true)
 private fun isChannel(url:String)=url.contains("/channel/",true)||url.contains("/c/",true)||url.contains("/@",true)
 fun retryAnalysis(){lastUrl?.let(::analyze)?:lastKeyword?.let(::searchKeyword)}
 fun refreshMetadata(){lastUrl?.let{analyze(it,true)}?:lastKeyword?.let{searchKeyword(it,true)}};fun resetAnalysis(){loadJob?.cancel();moreLoader=null;_state.value=AnalysisState.Idle}
 // Trước đây: c.videos.map{...} tạo lại TOÀN BỘ danh sách (có thể vài trăm phần tử) mỗi lần
 // người dùng tick chọn dù chỉ 1 video thay đổi. Bây giờ: chỉ dựng list mới bằng cách sửa đúng
 // vị trí cần đổi (toMutableList + set tại index), tránh việc chép lại toàn bộ danh sách liên tục.
 // Kết quả hiển thị cho người dùng giống hệt như trước.
 fun toggle(index:Int){
  val c=_state.value as? AnalysisState.Ready?:return
  val pos=c.videos.indexOfFirst{it.index==index}
  if(pos<0)return
  val target=c.videos[pos]
  if(!target.canSelect)return
  val updated=c.videos.toMutableList()
  updated[pos]=target.copy(isSelected=!target.isSelected)
  _state.value=c.copy(videos=updated)
 }
 fun selectAll(){
  val c=_state.value as? AnalysisState.Ready?:return
  val updated=c.videos.toMutableList()
  for(i in updated.indices){if(updated[i].canSelect&&!updated[i].isSelected)updated[i]=updated[i].copy(isSelected=true)}
  _state.value=c.copy(videos=updated)
 }
 fun clearSelection(){
  val c=_state.value as? AnalysisState.Ready?:return
  val updated=c.videos.toMutableList()
  for(i in updated.indices){if(updated[i].isSelected)updated[i]=updated[i].copy(isSelected=false)}
  _state.value=c.copy(videos=updated)
 }
 fun updateFolder(folder:String){val c=_state.value as? AnalysisState.Ready?:return;_state.value=c.copy(folder=folder)}
 fun startDownload(config: DownloadConfig, onEnqueued:()->Unit={}){
  val c=_state.value as? AnalysisState.Ready?:return
  viewModelScope.launch{
   DownloadWorker.enqueueBatch(context,c.source.copy(originalTotalVideos=c.videos.size),c.videos,c.folder,config)
   onEnqueued()
  }
 }
 fun pauseDownload(){viewModelScope.launch{control.pause()}};fun resumeDownload(){viewModelScope.launch{control.resume()}};fun cancelDownload(){viewModelScope.launch{control.cancel()}}
 fun continueNextTask(){viewModelScope.launch{DownloadWorker.enqueueNext(context)}}
 private fun WorkInfo.toDownloadState(data:Data):DownloadState{
  val current=data.getInt(DownloadWorker.KEY_CURRENT,0);val total=data.getInt(DownloadWorker.KEY_TOTAL,0);val title=data.getString(DownloadWorker.KEY_TITLE).orEmpty()
  val taskIndex=data.getInt(DownloadWorker.KEY_TASK_INDEX,1);val totalTasks=data.getInt(DownloadWorker.KEY_TOTAL_TASKS,1)
  val saved=data.getInt(DownloadWorker.KEY_SAVED,0);val skipped=data.getInt(DownloadWorker.KEY_SKIPPED,0);val failed=data.getInt(DownloadWorker.KEY_FAILED,0)
  val historyId=data.getString(DownloadWorker.KEY_HISTORY_ID)?.takeIf{it.isNotBlank()};val eta=data.getLong(DownloadWorker.KEY_ETA, -1L).takeIf{it>=0}
  val logs=data.getStringArray(DownloadWorker.KEY_LOGS)?.toList().orEmpty()
  val outputRelativePath=data.getString(DownloadWorker.KEY_OUTPUT_RELATIVE_PATH)?.takeIf{it.isNotBlank()}
  return when(data.getString(DownloadWorker.KEY_STATE)){
   "running"->DownloadState.Running(current,total,title,saved,skipped,logs,eta,taskIndex,totalTasks,failed)
   "paused"->DownloadState.Paused(current,total,logs,eta,taskIndex,totalTasks,saved,skipped,failed)
   "done"->DownloadState.Done(saved,skipped,logs,outputRelativePath,taskIndex,totalTasks,failed,historyId)
   "cancelled"->DownloadState.Cancelled(saved,logs,taskIndex,totalTasks,skipped,failed,historyId)
   "error"->DownloadState.Error(saved,skipped,data.getString(DownloadWorker.KEY_MESSAGE).orEmpty(),logs,taskIndex,totalTasks,failed,historyId)
   else->DownloadState.Idle
  }
 }
}
private fun AppSettings.toDownloadConfig()=DownloadConfig(languages,formats,preferManualSub,skipNoSub,outputDir,timestampMode,subtitleConcurrency,maxSubtitlesPerTask)