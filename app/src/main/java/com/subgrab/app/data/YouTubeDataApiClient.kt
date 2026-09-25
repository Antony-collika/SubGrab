package com.subgrab.app.data
import android.net.Uri
import com.subgrab.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class YouTubeDataApiClient(private val settings:SettingsRepository,private val pacer:RequestPacer){
 private suspend fun get(path:String,params:Map<String,String>,lane:RequestLane?=null):JSONObject=withContext(Dispatchers.IO){
  val key=settings.current().youtubeDataApiKey.trim()
  if(key.isEmpty()) {
   pacer.logConfigurationError(RequestOperation(lane ?: if(path=="search" || path=="playlistItems" || path=="channels" || path=="playlists") RequestLane.DISCOVERY_API else RequestLane.API_METADATA, path, "https://www.googleapis.com/youtube/v3/$path"), "YouTube Data API đã bật nhưng API key đang trống")
   throw IllegalStateException("YouTube Data API đã bật nhưng API key đang trống")
  }
  val query=(params+("key" to key)).entries.joinToString("&"){Uri.encode(it.key)+"="+Uri.encode(it.value)}
  val safeUrl="https://www.googleapis.com/youtube/v3/$path"
  val requestLane=lane ?: if(path=="search" || path=="playlistItems" || path=="channels" || path=="playlists") RequestLane.DISCOVERY_API else RequestLane.API_METADATA
  pacer.execute(RequestOperation(requestLane,path,safeUrl)){
   val c=(URL("$safeUrl?$query").openConnection() as HttpURLConnection).apply{requestMethod="GET";connectTimeout=15000;readTimeout=30000;setRequestProperty("Accept","application/json")}
   val code=c.responseCode;val body=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
   c.disconnect();if(code !in 200..299)throw HttpFailure(code,body.take(500));PacedHttpResult(JSONObject(body),code)
  }.value
 }
 suspend fun searchKeyword(query:String):List<VideoItem>{
  val json=get("search",mapOf("part" to "snippet","type" to "video","maxResults" to "50","q" to query))
  val itemCount=json.optJSONArray("items")?.length()?:0
  pacer.logDiagnostic(RequestLane.DISCOVERY_API,"search","SEARCH_RESPONSE items="+itemCount)
  val ids=buildList{val a=json.optJSONArray("items")?:return@buildList;for(i in 0 until a.length())a.optJSONObject(i)?.optJSONObject("id")?.optString("videoId")?.takeIf{it.isNotBlank()}?.let(::add)}
  pacer.logDiagnostic(RequestLane.DISCOVERY_API,"search","SEARCH_VIDEO_IDS extracted="+ids.size)
  pacer.logDiagnostic(RequestLane.API_METADATA,"videos","METADATA_REQUEST preparing ids="+ids.size)
  return getVideoMetadata(ids)
 }
 suspend fun getChannelTitle(source:String):String{
  return getChannelResource(source,"snippet").optJSONArray("items")?.optJSONObject(0)
   ?.optJSONObject("snippet")?.optString("title").orEmpty()
   .ifBlank { error("Không tìm thấy tên channel") }
 }

 private suspend fun getChannelResource(source:String,part:String):JSONObject{
  val uri=Uri.parse(source)
  val segments=uri.pathSegments
  val channelIndex=segments.indexOfFirst{it.equals("channel",true)}
  val customIndex=segments.indexOfFirst{it.equals("c",true)}
  val handle=segments.firstOrNull{it.startsWith("@")}
  val params=when {
   channelIndex >= 0 -> mapOf("part" to part,"id" to (segments.getOrNull(channelIndex+1) ?: error("Không tìm thấy channel id")))
   handle != null -> mapOf("part" to part,"forHandle" to handle.removePrefix("@"))
   customIndex >= 0 -> mapOf("part" to part,"forUsername" to (segments.getOrNull(customIndex+1) ?: error("Không tìm thấy channel username")))
   else -> error("URL channel chưa được API hỗ trợ")
  }
  return get("channels",params)
 }

 suspend fun listChannelUploads(source:String):List<VideoItem>{
  val channelJson=getChannelResource(source,"contentDetails")
  val uploads=channelJson.optJSONArray("items")?.optJSONObject(0)?.optJSONObject("contentDetails")?.optJSONObject("relatedPlaylists")?.optString("uploads").orEmpty()
  require(uploads.isNotBlank()) { "Không tìm thấy uploads playlist của channel" }
  return listPlaylistItems("https://www.youtube.com/playlist?list=$uploads")
 }
 suspend fun getPlaylistTitle(source:String):String{
  val id=Uri.parse(source).getQueryParameter("list")
   ?: Regex("[?&]list=([^&]+)").find(source)?.groupValues?.get(1)
   ?: error("Không tìm thấy playlist id")
  return get("playlists",mapOf("part" to "snippet","id" to id)).optJSONArray("items")?.optJSONObject(0)
   ?.optJSONObject("snippet")?.optString("title").orEmpty()
   .ifBlank { error("Không tìm thấy tên playlist") }
 }

 suspend fun listPlaylistItems(source:String):List<VideoItem>{
  val id=Uri.parse(source).getQueryParameter("list")?:Regex("[?&]list=([^&]+)").find(source)?.groupValues?.get(1) ?: error("Không tìm thấy playlist id")
  val json=get("playlistItems",mapOf("part" to "snippet","maxResults" to "50","playlistId" to id))
  val a=json.optJSONArray("items")?:return emptyList()
  val videoIds=buildList<String>{for(i in 0 until a.length()){a.optJSONObject(i)?.optJSONObject("snippet")?.optJSONObject("resourceId")?.optString("videoId")?.takeIf{it.isNotBlank()}?.let{add(it)}}}
  return getVideoMetadata(videoIds)
 }
 suspend fun getVideoMetadataInOrder(ids:List<String>):List<VideoItem>{
  val uniqueIds=ids.map(String::trim).filter(String::isNotBlank).distinct().take(50)
  if(uniqueIds.isEmpty())return emptyList()
  val fetched=getVideoMetadata(uniqueIds)
  val byId=fetched.associateBy{it.videoId}
  require(byId.size==uniqueIds.size) {
   "Không thể lấy metadata của một hoặc nhiều video trong Collection"
  }
  return uniqueIds.mapIndexed{index,id->byId.getValue(id).copy(index=index+1)}
 }
 suspend fun getVideoMetadata(ids:List<String>):List<VideoItem>{
  if(ids.isEmpty())return emptyList()
  val out=mutableListOf<VideoItem>()
  ids.map(String::trim).filter(String::isNotBlank).distinct().take(50).chunked(50).forEach{chunk->
   val json=get("videos",mapOf("part" to "snippet,contentDetails,statistics","id" to chunk.joinToString(",")))
   val metadataCount=json.optJSONArray("items")?.length()?:0
   pacer.logDiagnostic(RequestLane.API_METADATA,"videos","METADATA_RESPONSE items="+metadataCount+" requested="+chunk.size)
   val a=json.optJSONArray("items")?:return@forEach
   for(i in 0 until a.length()){
    val x=a.getJSONObject(i);val sn=x.getJSONObject("snippet");val st=x.optJSONObject("statistics");val cd=x.optJSONObject("contentDetails")
    val dur=parseDuration(cd?.optString("duration").orEmpty())
    val tags=buildList{val tagsArray=sn.optJSONArray("tags");if(tagsArray!=null)for(j in 0 until tagsArray.length())tagsArray.optString(j).takeIf{it.isNotBlank()}?.let(::add)}
    val topics=buildList{val topicsArray=sn.optJSONArray("topicIds");if(topicsArray!=null)for(j in 0 until topicsArray.length())topicsArray.optString(j).takeIf{it.isNotBlank()}?.let(::add)}
    out+=VideoItem(out.size+1,x.getString("id"),sn.optString("title"),dur.toInt(),emptyList(),channelTitle=sn.optString("channelTitle"),publishedAt=sn.optString("publishedAt"),viewCount=st?.optString("viewCount")?.toLongOrNull(),thumbnailUrl=sn.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url").orEmpty(),description=sn.optString("description").takeIf{it.isNotBlank()},durationSeconds=dur,likeCount=st?.optString("likeCount")?.toLongOrNull(),channelId=sn.optString("channelId").takeIf{it.isNotBlank()},commentCount=st?.optString("commentCount")?.toLongOrNull(),tags=tags,category=sn.optString("categoryId").takeIf{it.isNotBlank()},topic=topics)
   }
  }
  val channelIds=out.mapNotNull{it.channelId}.distinct()
  val subscribers=getSubscriberCounts(channelIds)
  return out.map{it.copy(subscriberCount=it.channelId?.let(subscribers::get))}.take(50)
 }
 private suspend fun getSubscriberCounts(channelIds:List<String>):Map<String,Long?>{
  if(channelIds.isEmpty()) return emptyMap()
  val json=get("channels",mapOf("part" to "statistics","id" to channelIds.joinToString(",")),RequestLane.API_METADATA)
  val a=json.optJSONArray("items") ?: return emptyMap()
  return buildMap{
   for(i in 0 until a.length()){
    val item=a.optJSONObject(i) ?: continue
    val id=item.optString("id").takeIf{it.isNotBlank()} ?: continue
    put(id,item.optJSONObject("statistics")?.optString("subscriberCount")?.toLongOrNull())
   }
  }
 }
 private fun parseDuration(v:String):Long=Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").matchEntire(v)?.let{m->(m.groupValues[1].toLongOrNull()?:0)*3600+(m.groupValues[2].toLongOrNull()?:0)*60+(m.groupValues[3].toLongOrNull()?:0)}?:0
}