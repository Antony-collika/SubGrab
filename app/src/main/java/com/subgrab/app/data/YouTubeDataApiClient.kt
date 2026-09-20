package com.subgrab.app.data
import android.net.Uri
import com.subgrab.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class YouTubeDataApiClient(private val settings:SettingsRepository,private val pacer:RequestPacer){
 private suspend fun get(path:String,params:Map<String,String>):JSONObject=withContext(Dispatchers.IO){
  val key=settings.current().youtubeDataApiKey.trim();require(key.isNotEmpty()){"YouTube Data API đã bật nhưng API key đang trống"}
  val query=(params+("key" to key)).entries.joinToString("&"){Uri.encode(it.key)+"="+Uri.encode(it.value)}
  val safeUrl="https://www.googleapis.com/youtube/v3/$path"
  pacer.execute(RequestOperation(if(path=="search")RequestLane.DISCOVERY_API else if(path=="playlistItems")RequestLane.DISCOVERY_API else RequestLane.API_METADATA,path,safeUrl)){
   val c=(URL("$safeUrl?$query").openConnection() as HttpURLConnection).apply{requestMethod="GET";connectTimeout=15000;readTimeout=30000;setRequestProperty("Accept","application/json")}
   val code=c.responseCode;val body=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
   c.disconnect();if(code !in 200..299)throw HttpFailure(code,body.take(500));JSONObject(body)
  }
 }
 suspend fun searchKeyword(query:String):List<VideoItem>{
  val json=get("search",mapOf("part" to "snippet","type" to "video","maxResults" to "50","q" to query))
  val ids=buildList{val a=json.optJSONArray("items")?:return@buildList;for(i in 0 until a.length())a.optJSONObject(i)?.optString("videoId")?.takeIf{it.isNotBlank()}?.let(::add)}
  return getVideoMetadata(ids)
 }
 suspend fun listPlaylistItems(source:String):List<VideoItem>{
  val id=Uri.parse(source).getQueryParameter("list")?:Regex("[?&]list=([^&]+)").find(source)?.groupValues?.get(1) ?: error("Không tìm thấy playlist id")
  val json=get("playlistItems",mapOf("part" to "snippet","maxResults" to "50","playlistId" to id))
  val a=json.optJSONArray("items")?:return emptyList()
  val videoIds=buildList<String>{for(i in 0 until a.length()){a.optJSONObject(i)?.optJSONObject("snippet")?.optJSONObject("resourceId")?.optString("videoId")?.takeIf{it.isNotBlank()}?.let{add(it)}}}
  return getVideoMetadata(videoIds)
 }
 suspend fun getVideoMetadata(ids:List<String>):List<VideoItem>{
  if(ids.isEmpty())return emptyList()
  val out=mutableListOf<VideoItem>()
  ids.take(50).chunked(50).forEach{chunk->
   val json=get("videos",mapOf("part" to "snippet,contentDetails,statistics","id" to chunk.joinToString(",")))
   val a=json.optJSONArray("items")?:return@forEach
   for(i in 0 until a.length()){val x=a.getJSONObject(i);val sn=x.getJSONObject("snippet");val st=x.optJSONObject("statistics");val cd=x.optJSONObject("contentDetails")
    val dur=parseDuration(cd?.optString("duration").orEmpty());out+=VideoItem(out.size+1,x.getString("id"),sn.optString("title"),dur.toInt(),emptyList(),channelTitle=sn.optString("channelTitle"),publishedAt=sn.optString("publishedAt"),viewCount=st?.optString("viewCount")?.toLongOrNull(),thumbnailUrl=sn.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url").orEmpty(),description=sn.optString("description").takeIf{it.isNotBlank()},durationSeconds=dur,likeCount=st?.optString("likeCount")?.toLongOrNull())
   }
  }
  return out.take(50)
 }
 private fun parseDuration(v:String):Long=Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").matchEntire(v)?.let{m->(m.groupValues[1].toLongOrNull()?:0)*3600+(m.groupValues[2].toLongOrNull()?:0)*60+(m.groupValues[3].toLongOrNull()?:0)}?:0
}