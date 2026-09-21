package com.subgrab.app.data

import com.subgrab.app.domain.VideoItem

class ExtractorDiscoveryClient(
    private val extractor: NewPipeExtractorClient
) : DiscoveryClient {
    suspend fun discoverPlaylistWithSource(source: String): Pair<com.subgrab.app.domain.Source, List<VideoItem>> =
        extractor.extractSource(source).getOrThrow().let { it.first to it.second.take(50) }

    suspend fun discoverChannelWithSource(source: String): Pair<com.subgrab.app.domain.Source, List<VideoItem>> =
        extractor.extractSource(source).getOrThrow().let { it.first to it.second.take(50) }

    override suspend fun discoverPlaylist(source: String): List<VideoItem> =
        discoverPlaylistWithSource(source).second

    override suspend fun discoverKeyword(query: String): List<VideoItem> =
        extractor.search(query).getOrThrow().second.take(50)

    override suspend fun discoverVideo(source: String): List<VideoItem> =
        extractor.extractSource(source).getOrThrow().second.take(50)

    override suspend fun discoverChannel(source: String): List<VideoItem> =
        extractor.extractSource(source).getOrThrow().second.take(50)
}
