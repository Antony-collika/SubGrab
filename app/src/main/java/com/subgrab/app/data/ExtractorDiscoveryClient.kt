package com.subgrab.app.data
import com.subgrab.app.domain.*
class ExtractorDiscoveryClient(private val extractor:NewPipeExtractorClient,private val pacer:RequestPacer?=null):DiscoveryClient{
 override suspend fun discoverPlaylist(source:String):List<VideoItem>{return pacer?.execute(RequestOperation(RequestLane.DISCOVERY_EXTRACTOR,"playlist.discovery",source)){extractor.extractSource(source).getOrThrow().second.take(50)}?:extractor.extractSource(source).getOrThrow().second.take(50)}
 override suspend fun discoverKeyword(query:String):List<VideoItem>{return pacer?.execute(RequestOperation(RequestLane.DISCOVERY_EXTRACTOR,"keyword.discovery","https://www.youtube.com/results")){extractor.search(query).getOrThrow().second.take(50)}?:extractor.search(query).getOrThrow().second.take(50)}
}