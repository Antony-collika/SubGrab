package com.subgrab.app.data
import com.subgrab.app.domain.VideoItem
class ApiDiscoveryClient(private val api:YouTubeDataApiClient):DiscoveryClient{
 override suspend fun discoverPlaylist(source:String)=api.listPlaylistItems(source).take(50)
 suspend fun discoverPlaylistWithSource(source:String):Pair<com.subgrab.app.domain.Source,List<VideoItem>>{
  val videos=api.listPlaylistItems(source).take(50)
  val title=api.getPlaylistTitle(source)
  return com.subgrab.app.domain.Source(source,source,title,videos.size) to videos
 }
 override suspend fun discoverKeyword(query:String)=api.searchKeyword(query).take(50)
 override suspend fun discoverVideo(source:String)=api.getVideoMetadata(listOf(videoId(source))).take(50)
 override suspend fun discoverChannel(source:String)=api.listChannelUploads(source).take(50)
 suspend fun discoverChannelWithSource(source:String):Pair<com.subgrab.app.domain.Source,List<VideoItem>>{
  val videos=api.listChannelUploads(source).take(50)
  val title=api.getChannelTitle(source)
  return com.subgrab.app.domain.Source(source,source,title,videos.size) to videos
 }
 suspend fun discoverVideoCollectionWithSource(sources:List<String>):Pair<com.subgrab.app.domain.Source,List<VideoItem>>{
  val ids=sources.mapNotNull{videoId(it)}.distinct().take(50)
  require(ids.size==sources.distinct().size) { "Chuỗi nhiều URL chỉ hỗ trợ URL video YouTube hợp lệ" }
  val videos=api.getVideoMetadataInOrder(ids)
  return com.subgrab.app.domain.Source("collection",sources.joinToString("\n"),"Collection",videos.size) to videos
 }
 private fun videoId(source:String):String = android.net.Uri.parse(source).getQueryParameter("v")
  ?: android.net.Uri.parse(source).pathSegments.lastOrNull()?.takeIf{it.isNotBlank()}
  ?: error("Không tìm thấy video id")
}