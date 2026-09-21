package com.subgrab.app.data

import com.subgrab.app.domain.VideoItem

object DownloadTaskPlanner {
    fun plan(videos: List<VideoItem>): List<List<VideoItem>> =
        videos.filter { it.isSelected }.take(MAX_VIDEOS).chunked(TASK_SIZE).ifEmpty { listOf(emptyList()) }

    private const val MAX_VIDEOS = 50
    private const val TASK_SIZE = 10
}
