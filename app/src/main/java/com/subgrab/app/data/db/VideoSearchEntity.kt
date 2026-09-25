package com.subgrab.app.data.db

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "video_search")
@Fts4
data class VideoSearchEntity(
    val videoId: String,
    val title: String,
    val description: String,
    val channelName: String,
    val tags: String,
    val category: String,
    val topic: String
)
