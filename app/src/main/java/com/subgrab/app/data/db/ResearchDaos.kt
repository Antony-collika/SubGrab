package com.subgrab.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VideoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: VideoEntity)

    @Query("UPDATE videos SET channelId = :channelId, updatedAt = :updatedAt WHERE videoId = :videoId")
    suspend fun updateChannel(videoId: String, channelId: String?, updatedAt: Long)

    @Query("SELECT * FROM videos WHERE videoId = :videoId LIMIT 1")
    suspend fun get(videoId: String): VideoEntity?

    @Query("SELECT * FROM videos ORDER BY updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE channelId = :channelId ORDER BY updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getByChannel(channelId: String, limit: Int, offset: Int): List<VideoEntity>
}

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ChannelEntity)

    @Query("UPDATE channels SET name = :name, updatedAt = :updatedAt WHERE channelId = :channelId")
    suspend fun update(channelId: String, name: String?, updatedAt: Long)

    @Query("SELECT * FROM channels WHERE channelId = :channelId LIMIT 1")
    suspend fun get(channelId: String): ChannelEntity?
}

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<PlaylistVideoCrossRef>)

    @Query("UPDATE playlists SET channelId = :channelId, title = :title, updatedAt = :updatedAt WHERE playlistId = :playlistId")
    suspend fun update(playlistId: String, channelId: String?, title: String, updatedAt: Long)

    @Query("SELECT * FROM playlist_video WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getVideos(playlistId: String): List<PlaylistVideoCrossRef>
}

@Dao
interface MetadataSnapshotDao {
    @Insert
    suspend fun insert(entity: VideoMetadataSnapshotEntity): Long

    @Query("SELECT * FROM video_metadata_snapshots WHERE videoId = :videoId ORDER BY fetchedAt DESC")
    suspend fun history(videoId: String): List<VideoMetadataSnapshotEntity>

    @Query("SELECT * FROM video_metadata_snapshots WHERE videoId = :videoId ORDER BY fetchedAt DESC LIMIT 1")
    suspend fun latest(videoId: String): VideoMetadataSnapshotEntity?
}

@Dao
interface SearchDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(entity: SearchSessionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemberships(refs: List<SearchSessionVideoCrossRef>)

    @Query("DELETE FROM video_search WHERE videoId = :videoId")
    suspend fun deleteDocument(videoId: String)

    @Insert
    suspend fun insertDocument(entity: VideoSearchEntity)

    @Query("SELECT videoId FROM video_search WHERE video_search MATCH :query LIMIT :limit")
    suspend fun searchVideoIds(query: String, limit: Int): List<String>

    @Query("SELECT * FROM search_sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: String): SearchSessionEntity?

    @Query("SELECT * FROM search_sessions WHERE query = :query ORDER BY fetchedAt DESC LIMIT 1")
    suspend fun getLatestSession(query: String): SearchSessionEntity?

    @Query("SELECT * FROM search_session_video WHERE searchSessionId = :sessionId ORDER BY position")
    suspend fun getSessionVideos(sessionId: String): List<SearchSessionVideoCrossRef>
}

@Dao
interface UserActivityDao {
    @Insert
    suspend fun insert(entity: UserActivityEntity)

    @Query("SELECT * FROM user_activities ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun recent(limit: Int, offset: Int): List<UserActivityEntity>
}

@Dao
interface TranscriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TranscriptEntity)

    @Query("SELECT * FROM transcripts WHERE videoId = :videoId LIMIT 1")
    suspend fun get(videoId: String): TranscriptEntity?
}

@Dao
interface CommentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThread(entity: CommentThreadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComments(entities: List<CommentEntity>)

    @Query("DELETE FROM comments WHERE videoId = :videoId")
    suspend fun deleteComments(videoId: String)

    @Query("DELETE FROM comment_threads WHERE videoId = :videoId")
    suspend fun deleteThreads(videoId: String)

    @Query("SELECT * FROM comment_threads WHERE videoId = :videoId ORDER BY fetchedAt DESC")
    suspend fun getThreads(videoId: String): List<CommentThreadEntity>

    @Query("SELECT * FROM comments WHERE threadId = :threadId ORDER BY publishedAt")
    suspend fun getComments(threadId: String): List<CommentEntity>
}
