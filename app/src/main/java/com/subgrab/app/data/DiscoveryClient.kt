package com.subgrab.app.data
import com.subgrab.app.domain.VideoItem
interface DiscoveryClient { suspend fun discoverPlaylist(source:String):List<VideoItem>; suspend fun discoverKeyword(query:String):List<VideoItem>; suspend fun discoverVideo(source:String):List<VideoItem> }