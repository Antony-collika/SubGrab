package com.subgrab.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val videoId: String,
    val channelId: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val channelId: String,
    val name: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val playlistId: String,
    val channelId: String?,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "playlist_video",
    primaryKeys = ["playlistId", "videoId"],
    indices = [Index("videoId")],
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["playlistId"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = VideoEntity::class, parentColumns = ["videoId"], childColumns = ["videoId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class PlaylistVideoCrossRef(
    val playlistId: String,
    val videoId: String,
    val position: Int?
)

@Entity(
    tableName = "video_metadata_snapshots",
    indices = [Index("videoId"), Index("fetchedAt")],
    foreignKeys = [ForeignKey(entity = VideoEntity::class, parentColumns = ["videoId"], childColumns = ["videoId"], onDelete = ForeignKey.CASCADE)]
)
data class VideoMetadataSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val fetchedAt: Long,
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

@Entity(tableName = "search_sessions", indices = [Index("fetchedAt"), Index("query")])
data class SearchSessionEntity(
    @PrimaryKey val id: String,
    val query: String,
    val fetchedAt: Long
)

@Entity(
    tableName = "search_session_video",
    primaryKeys = ["searchSessionId", "videoId"],
    indices = [Index("videoId")],
    foreignKeys = [
        ForeignKey(entity = SearchSessionEntity::class, parentColumns = ["id"], childColumns = ["searchSessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = VideoEntity::class, parentColumns = ["videoId"], childColumns = ["videoId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class SearchSessionVideoCrossRef(
    val searchSessionId: String,
    val videoId: String,
    val position: Int?
)

@Entity(
    tableName = "user_activities",
    indices = [
        Index("timestamp"),
        Index("type"),
        Index("videoId"),
        Index("channelId"),
        Index("playlistId"),
        Index("searchSessionId")
    ]
)
data class UserActivityEntity(
    @PrimaryKey val id: String,
    val type: String,
    val timestamp: Long,
    val videoId: String?,
    val channelId: String?,
    val playlistId: String?,
    val searchSessionId: String?,
    val query: String?,
    val context: String?
)

@Entity(
    tableName = "transcripts",
    foreignKeys = [ForeignKey(entity = VideoEntity::class, parentColumns = ["videoId"], childColumns = ["videoId"], onDelete = ForeignKey.CASCADE)]
)
data class TranscriptEntity(
    @PrimaryKey val videoId: String,
    val content: String?,
    val language: String?,
    val fetchedAt: Long?,
    val fetchState: String,
    val errorMessage: String?
)

@Entity(
    tableName = "comment_threads",
    indices = [Index("videoId"), Index("fetchedAt")],
    foreignKeys = [ForeignKey(entity = VideoEntity::class, parentColumns = ["videoId"], childColumns = ["videoId"], onDelete = ForeignKey.CASCADE)]
)
data class CommentThreadEntity(
    @PrimaryKey val threadId: String,
    val videoId: String,
    val topLevelCommentId: String?,
    val topLevelComment: String?,
    val replyCount: Int,
    val fetchedAt: Long?,
    val fetchState: String,
    val errorMessage: String?
)

@Entity(
    tableName = "comments",
    indices = [Index("threadId"), Index("videoId"), Index("parentCommentId")],
    foreignKeys = [ForeignKey(entity = CommentThreadEntity::class, parentColumns = ["threadId"], childColumns = ["threadId"], onDelete = ForeignKey.CASCADE)]
)
data class CommentEntity(
    @PrimaryKey val commentId: String,
    val threadId: String,
    val videoId: String,
    val parentCommentId: String?,
    val text: String,
    val author: String?,
    val likeCount: Long?,
    val publishedAt: String?,
    val updatedAt: String?,
    val fetchedAt: Long?
)
