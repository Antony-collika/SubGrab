package com.subgrab.app.data
import com.subgrab.app.domain.VideoItem
class ExtractorDiscoveryClient(private val extractor:NewPipeExtractorClient):DiscoveryClient{
 override suspend fun discoverPlaylist(source:String)=extractor.extractSource(source).getOrThrow().second.take(50)
 override suspend fun discoverKeyword(query:String)=extractor.search(query).getOrThrow().second.take(50)
}