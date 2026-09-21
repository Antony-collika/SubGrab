package com.subgrab.app.data
import com.subgrab.app.domain.VideoItem
class ApiDiscoveryClient(private val api:YouTubeDataApiClient):DiscoveryClient{
 override suspend fun discoverPlaylist(source:String)=api.listPlaylistItems(source).take(50)
 override suspend fun discoverKeyword(query:String)=api.searchKeyword(query).take(50)
 override suspend fun discoverVideo(source:String)=api.getVideoMetadata(listOf(videoId(source))).take(50)
 private fun videoId(source:String):String = android.net.Uri.parse(source).getQueryParameter("v")
  ?: android.net.Uri.parse(source).pathSegments.lastOrNull()?.takeIf{it.isNotBlank()}
  ?: error("Không tìm thấy video id")
}