package com.subgrab.app.data

import com.subgrab.app.domain.VideoItem

data class NormalizedVideoMetadata(
    val videoId: String,
    val title: String,
    val description: String?,
    val publishedAt: String?,
    val durationSeconds: Long?,
    val channelId: String?,
    val channelName: String?,
    val subscriberCount: Long?,
    val viewCount: Long?,
    val likeCount: Long?,
    val commentCount: Long?,
    val tags: List<String>,
    val category: String?,
    val topic: List<String>,
    val thumbnail: String?
)

object MetadataNormalizer {
    fun normalize(video: VideoItem): NormalizedVideoMetadata = NormalizedVideoMetadata(
        videoId = video.videoId,
        title = video.title,
        description = video.description,
        publishedAt = video.publishedAt.takeIf(String::isNotBlank),
        durationSeconds = video.durationSeconds ?: video.durationSec.toLong().takeIf { it > 0 },
        channelId = video.channelId,
        channelName = video.channelTitle.takeIf(String::isNotBlank),
        subscriberCount = video.subscriberCount,
        viewCount = video.viewCount,
        likeCount = video.likeCount,
        commentCount = video.commentCount,
        tags = video.tags,
        category = video.category,
        topic = video.topic,
        thumbnail = video.thumbnailUrl.takeIf(String::isNotBlank)
    )
}
