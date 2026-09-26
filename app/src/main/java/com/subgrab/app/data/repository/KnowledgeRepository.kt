package com.subgrab.app.data.repository

import androidx.room.withTransaction
import com.subgrab.app.data.MetadataNormalizer
import com.subgrab.app.data.SubGrabDatabase
import com.subgrab.app.data.db.*
import com.subgrab.app.domain.Source
import com.subgrab.app.domain.VideoItem
import java.util.UUID

class KnowledgeRepository(private val database: SubGrabDatabase) {
    suspend fun saveAnalysis(
        source: Source,
        videos: List<VideoItem>,
        activityType: String = "ANALYZE_URL",
        metadataCacheHours: Long = 24,
        forceRefresh: Boolean = false
    ) {
        val normalized = videos.distinctBy { it.videoId }.map(MetadataNormalizer::normalize)
        val now = System.currentTimeMillis()
        database.withTransaction {
            normalized.forEachIndexed { index, metadata ->
                saveVideo(metadata, now)
                if (forceRefresh || shouldRefreshMetadata(metadata.videoId, now, metadataCacheHours)) {
                    if (forceRefresh || shouldRefreshMetadata(metadata.videoId, now, metadataCacheHours)) {
                    database.metadataSnapshotDao().insert(metadata.toSnapshot(now))
                }
                }
                database.searchDao().deleteDocument(metadata.videoId)
                database.searchDao().insertDocument(metadata.toSearchDocument())
            }
            persistContext(source, normalized, now)
            normalized.forEach { metadata ->
                database.userActivityDao().insert(
                    UserActivityEntity(
                        id = UUID.randomUUID().toString(),
                        type = activityType,
                        timestamp = now,
                        videoId = metadata.videoId,
                        channelId = metadata.channelId,
                        playlistId = playlistIdFrom(source.url),
                        searchSessionId = null,
                        query = null,
                        context = source.id
                    )
                )
            }
        }
    }

    suspend fun saveKeywordSearch(
        query: String,
        source: Source,
        videos: List<VideoItem>,
        metadataCacheHours: Long = 24,
        forceRefresh: Boolean = false
    ): String {
        val cleanQuery = query.trim()
        require(cleanQuery.isNotBlank())
        val normalized = videos.distinctBy { it.videoId }.map(MetadataNormalizer::normalize)
        val now = System.currentTimeMillis()
        val sessionId = UUID.randomUUID().toString()
        database.withTransaction {
            database.searchDao().insertSession(SearchSessionEntity(sessionId, cleanQuery, now))
            normalized.forEachIndexed { index, metadata ->
                saveVideo(metadata, now)
                database.metadataSnapshotDao().insert(metadata.toSnapshot(now))
                database.searchDao().deleteDocument(metadata.videoId)
                database.searchDao().insertDocument(metadata.toSearchDocument())
            }
            database.searchDao().insertMemberships(
                normalized.mapIndexed { index, metadata ->
                    SearchSessionVideoCrossRef(sessionId, metadata.videoId, index + 1)
                }
            )
            normalized.forEach { metadata ->
                database.userActivityDao().insert(
                    UserActivityEntity(
                        id = UUID.randomUUID().toString(),
                        type = "SEARCH",
                        timestamp = now,
                        videoId = metadata.videoId,
                        channelId = metadata.channelId,
                        playlistId = null,
                        searchSessionId = sessionId,
                        query = cleanQuery,
                        context = source.id
                    )
                )
            }
        }
        return sessionId
    }

    suspend fun recordDownloadActivity(videos: List<VideoItem>, context: String) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            videos.distinctBy { it.videoId }.forEach { video ->
                val metadata = MetadataNormalizer.normalize(video)
                database.userActivityDao().insert(
                    UserActivityEntity(
                        id = UUID.randomUUID().toString(),
                        type = "DOWNLOAD_SUBTITLE",
                        timestamp = now,
                        videoId = metadata.videoId,
                        channelId = metadata.channelId,
                        playlistId = null,
                        searchSessionId = null,
                        query = null,
                        context = context
                    )
                )
            }
        }
    }

    suspend fun recordActivity(type: String, videoId: String? = null, channelId: String? = null, playlistId: String? = null, searchSessionId: String? = null, query: String? = null, context: String? = null) {
        database.userActivityDao().insert(
            UserActivityEntity(UUID.randomUUID().toString(), type, System.currentTimeMillis(), videoId, channelId, playlistId, searchSessionId, query, context)
        )
    }

 
    suspend fun saveTranscript(
        videoId: String,
        content: String,
        language: String,
        overwrite: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.videoDao().insert(VideoEntity(videoId, null, now, now))
            val existing = database.transcriptDao().get(videoId)
            if (existing == null || overwrite) {
                database.transcriptDao().upsert(
                    TranscriptEntity(videoId, content, language, now, "SUCCESS", null)
                )
            }
        }
    }

    suspend fun saveComments(videoId: String, threads: List<com.subgrab.app.data.FetchedCommentThread>) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.videoDao().insert(VideoEntity(videoId, null, now, now))
            database.commentDao().deleteComments(videoId)
            database.commentDao().deleteThreads(videoId)
            threads.forEach { thread ->
                database.commentDao().upsertThread(
                    CommentThreadEntity(
                        threadId = thread.threadId,
                        videoId = videoId,
                        topLevelCommentId = thread.topLevel.id,
                        topLevelComment = thread.topLevel.text,
                        replyCount = thread.replyCount,
                        fetchedAt = now,
                        fetchState = "SUCCESS",
                        errorMessage = null
                    )
                )
                val comments = buildList {
                    add(thread.topLevel.toEntity(now))
                    thread.replies.forEach { add(it.toEntity(now)) }
                }.distinctBy { it.commentId }
                database.commentDao().upsertComments(comments)
            }
        }
    }

    private fun com.subgrab.app.data.FetchedComment.toEntity(now: Long) = CommentEntity(
        commentId = id,
        threadId = threadId,
        videoId = videoId,
        parentCommentId = parentId,
        text = text,
        author = author,
        likeCount = likeCount,
        publishedAt = publishedAt,
        updatedAt = updatedAt,
        fetchedAt = now
    )

    suspend fun getFreshCachedAnalysis(sourceUrl: String, cacheHours: Long): Pair<Source, List<VideoItem>>? {
        if (cacheHours <= 0L) return null
        val ids = when {
            com.subgrab.app.domain.YoutubeUrlParser.isVideoUrl(sourceUrl) ->
                listOf(com.subgrab.app.domain.YoutubeUrlParser.videoId(sourceUrl) ?: return null)
            com.subgrab.app.domain.YoutubeUrlParser.isPlaylistUrl(sourceUrl) -> {
                val playlistId = Regex("[?&]list=([^&]+)").find(sourceUrl)?.groupValues?.get(1) ?: return null
                database.playlistDao().getVideos(playlistId).map { it.videoId }
            }
            com.subgrab.app.domain.YoutubeUrlParser.isChannelUrl(sourceUrl) -> {
                val channelId = Regex("/channel/([^/?#]+)", RegexOption.IGNORE_CASE).find(sourceUrl)?.groupValues?.get(1)
                    ?: return null
                database.videoDao().getByChannel(channelId, 50, 0).map { it.videoId }
            }
            else -> return null
        }
        val videos = getFreshCachedVideos(ids, cacheHours) ?: return null
        if (videos.isEmpty()) return null
        val title = when {
            com.subgrab.app.domain.YoutubeUrlParser.isPlaylistUrl(sourceUrl) -> {
                val playlistId = Regex("[?&]list=([^&]+)").find(sourceUrl)?.groupValues?.get(1) ?: return null
                database.playlistDao().get(playlistId)?.title ?: return null
            }
            com.subgrab.app.domain.YoutubeUrlParser.isChannelUrl(sourceUrl) -> {
                val channelId = Regex("/channel/([^/?#]+)", RegexOption.IGNORE_CASE).find(sourceUrl)?.groupValues?.get(1) ?: return null
                database.channelDao().get(channelId)?.name ?: return null
            }
            else -> videos.first().title
        }
        return Source(sourceUrl, sourceUrl, title, videos.size) to videos
    }

    suspend fun getFreshCachedSearch(query: String, cacheHours: Long): Pair<Source, List<VideoItem>>? {
        if (cacheHours <= 0L) return null
        val session = database.searchDao().getLatestSession(query.trim()) ?: return null
        if (System.currentTimeMillis() - session.fetchedAt >= cacheHoursToMs(cacheHours)) return null
        val ids = database.searchDao().getSessionVideos(session.id).map { it.videoId }
        val videos = getFreshCachedVideos(ids, cacheHours) ?: return null
        if (videos.isEmpty()) return null
        val clean = query.trim()
        return Source("keyword:$clean", "https://www.youtube.com/results?search_query=" + android.net.Uri.encode(clean), clean, videos.size) to videos
    }

    private suspend fun getFreshCachedVideos(ids: List<String>, cacheHours: Long): List<VideoItem>? {
        val now = System.currentTimeMillis()
        val ttlMs = cacheHoursToMs(cacheHours)
        val snapshots = ids.distinct().mapNotNull { id -> database.metadataSnapshotDao().latest(id) }
        if (snapshots.size != ids.distinct().size) return null
        if (snapshots.any { now - it.fetchedAt >= ttlMs }) return null
        return snapshots.mapIndexed { index, snapshot ->
            VideoItem(
                index = index + 1,
                videoId = snapshot.videoId,
                title = snapshot.title,
                durationSec = (snapshot.durationSeconds ?: 0L).toInt(),
                availableSubs = emptyList(),
                channelTitle = snapshot.channelName.orEmpty(),
                publishedAt = snapshot.publishedAt.orEmpty(),
                viewCount = snapshot.viewCount,
                thumbnailUrl = snapshot.thumbnail.orEmpty(),
                description = snapshot.description,
                durationSeconds = snapshot.durationSeconds,
                likeCount = snapshot.likeCount,
                channelId = snapshot.channelId,
                subscriberCount = snapshot.subscriberCount,
                commentCount = snapshot.commentCount,
                tags = snapshot.tags,
                category = snapshot.category,
                topic = snapshot.topic
            )
        }
    }

    private fun cacheHoursToMs(hours: Long): Long =
        hours.coerceAtMost(Long.MAX_VALUE / (60L * 60L * 1000L)) * 60L * 60L * 1000L

    suspend fun getVideo(videoId: String) = database.videoDao().get(videoId)
    suspend fun getLatestSnapshot(videoId: String) = database.metadataSnapshotDao().latest(videoId)
    suspend fun getSnapshotHistory(videoId: String) = database.metadataSnapshotDao().history(videoId)
    suspend fun getRecentActivities(limit: Int = 50, offset: Int = 0) = database.userActivityDao().recent(limit, offset)
    suspend fun searchVideos(matchQuery: String, limit: Int = 100) = database.searchDao().searchVideoIds(matchQuery, limit)
    suspend fun getVideosByChannel(channelId: String, limit: Int = 100, offset: Int = 0) = database.videoDao().getByChannel(channelId, limit, offset)
    suspend fun getSearchSessionVideos(sessionId: String) = database.searchDao().getSessionVideos(sessionId)
    suspend fun getPlaylistVideos(playlistId: String) = database.playlistDao().getVideos(playlistId)
    suspend fun getTranscript(videoId: String) = database.transcriptDao().get(videoId)
    suspend fun getCommentThreads(videoId: String) = database.commentDao().getThreads(videoId)
    suspend fun getComments(threadId: String) = database.commentDao().getComments(threadId)

    private suspend fun shouldRefreshMetadata(videoId: String, now: Long, cacheHours: Long): Boolean {
        if (cacheHours <= 0L) return true
        val latest = database.metadataSnapshotDao().latest(videoId) ?: return true
        val ttlMs = cacheHours.coerceAtMost(Long.MAX_VALUE / (60L * 60L * 1000L)) * 60L * 60L * 1000L
        return now - latest.fetchedAt >= ttlMs
    }

    private suspend fun saveVideo(metadata: com.subgrab.app.data.NormalizedVideoMetadata, now: Long) {
        val existingVideo = database.videoDao().get(metadata.videoId)
        val effectiveChannelId = metadata.channelId ?: existingVideo?.channelId
        database.videoDao().insert(VideoEntity(metadata.videoId, effectiveChannelId, now, now))
        database.videoDao().updateChannel(metadata.videoId, effectiveChannelId, now)
        effectiveChannelId?.takeIf(String::isNotBlank)?.let { channelId ->
            val existingChannel = database.channelDao().get(channelId)
            val effectiveName = metadata.channelName ?: existingChannel?.name
            database.channelDao().insert(ChannelEntity(channelId, effectiveName, now, now))
            database.channelDao().update(channelId, effectiveName, now)
        }
    }

    private suspend fun persistContext(source: Source, normalized: List<com.subgrab.app.data.NormalizedVideoMetadata>, now: Long) {
        val playlistId = playlistIdFrom(source.url)
        if (playlistId != null) {
            database.playlistDao().insert(PlaylistEntity(playlistId, normalized.firstOrNull()?.channelId, source.title, now, now))
            database.playlistDao().update(playlistId, normalized.firstOrNull()?.channelId, source.title, now)
            database.playlistDao().insertCrossRefs(
                normalized.mapIndexed { index, metadata -> PlaylistVideoCrossRef(playlistId, metadata.videoId, index + 1) }
            )
        }
        val channelId = normalized.firstOrNull()?.channelId ?: channelIdFrom(source.url)
        if (channelId != null) {
            val name = normalized.firstOrNull()?.channelName
                ?: source.title.takeIf { com.subgrab.app.domain.YoutubeUrlParser.isChannelUrl(source.url) }
            database.channelDao().insert(ChannelEntity(channelId, name, now, now))
            database.channelDao().update(channelId, name, now)
        }
    }

    private fun playlistIdFrom(url: String): String? =
        com.subgrab.app.domain.YoutubeUrlParser.isPlaylistUrl(url)
            .let { isPlaylist -> if (isPlaylist) Regex("[?&]list=([^&]+)").find(url)?.groupValues?.get(1)?.takeIf(String::isNotBlank) else null }

    private fun channelIdFrom(url: String): String? =
        Regex("/channel/([^/?#]+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)

    private fun com.subgrab.app.data.NormalizedVideoMetadata.toSnapshot(now: Long) =
        VideoMetadataSnapshotEntity(0, videoId, now, title, description, publishedAt, durationSeconds, channelId, channelName, subscriberCount, viewCount, likeCount, commentCount, tags, category, topic, thumbnail)

    private fun com.subgrab.app.data.NormalizedVideoMetadata.toSearchDocument() =
        VideoSearchEntity(videoId, title, description.orEmpty(), channelName.orEmpty(), tags.joinToString(" "), category.orEmpty(), topic.joinToString(" "))
}
