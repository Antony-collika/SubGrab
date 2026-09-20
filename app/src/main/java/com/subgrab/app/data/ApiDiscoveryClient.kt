package com.subgrab.app.data
import com.subgrab.app.domain.VideoItem
class ApiDiscoveryClient(private val api:YouTubeDataApiClient):DiscoveryClient{
 override suspend fun discoverPlaylist(source:String)=api.listPlaylistItems(source).take(50)
 override suspend fun discoverKeyword(query:String)=api.searchKeyword(query).take(50)
}