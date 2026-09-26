package com.subgrab.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.subgrab.app.data.db.*

@Database(
    entities = [
        RequestMetricEntity::class,
        RuntimeLogEntity::class,
        VideoEntity::class,
        ChannelEntity::class,
        PlaylistEntity::class,
        PlaylistVideoCrossRef::class,
        VideoMetadataSnapshotEntity::class,
        SearchSessionEntity::class,
        SearchSessionVideoCrossRef::class,
        UserActivityEntity::class,
        TranscriptEntity::class,
        CommentThreadEntity::class,
        CommentEntity::class,
        VideoSearchEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(ResearchConverters::class)
abstract class SubGrabDatabase : RoomDatabase() {
    abstract fun requestMetricDao(): RequestMetricDao
    abstract fun runtimeLogDao(): RuntimeLogDao
    abstract fun videoDao(): VideoDao
    abstract fun channelDao(): ChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun metadataSnapshotDao(): MetadataSnapshotDao
    abstract fun searchDao(): SearchDao
    abstract fun userActivityDao(): UserActivityDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun commentDao(): CommentDao
    abstract fun researchQueryDao(): ResearchQueryDao

    companion object {
        private const val DB_NAME = "subgrab_runtime.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS videos (videoId TEXT NOT NULL, channelId TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(videoId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS channels (channelId TEXT NOT NULL, name TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(channelId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS playlists (playlistId TEXT NOT NULL, channelId TEXT, title TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(playlistId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS playlist_video (playlistId TEXT NOT NULL, videoId TEXT NOT NULL, position INTEGER, PRIMARY KEY(playlistId, videoId), FOREIGN KEY(playlistId) REFERENCES playlists(playlistId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(videoId) REFERENCES videos(videoId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_video_videoId ON playlist_video(videoId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS video_metadata_snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, videoId TEXT NOT NULL, fetchedAt INTEGER NOT NULL, title TEXT NOT NULL, description TEXT, publishedAt TEXT, durationSeconds INTEGER, channelId TEXT, channelName TEXT, subscriberCount INTEGER, viewCount INTEGER, likeCount INTEGER, commentCount INTEGER, tags TEXT NOT NULL, category TEXT, topic TEXT NOT NULL, thumbnail TEXT, FOREIGN KEY(videoId) REFERENCES videos(videoId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_video_metadata_snapshots_videoId ON video_metadata_snapshots(videoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_video_metadata_snapshots_fetchedAt ON video_metadata_snapshots(fetchedAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS search_sessions (id TEXT NOT NULL, query TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_search_sessions_fetchedAt ON search_sessions(fetchedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_search_sessions_query ON search_sessions(query)")
                db.execSQL("CREATE TABLE IF NOT EXISTS search_session_video (searchSessionId TEXT NOT NULL, videoId TEXT NOT NULL, position INTEGER, PRIMARY KEY(searchSessionId, videoId), FOREIGN KEY(searchSessionId) REFERENCES search_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(videoId) REFERENCES videos(videoId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_search_session_video_videoId ON search_session_video(videoId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS user_activities (id TEXT NOT NULL, type TEXT NOT NULL, timestamp INTEGER NOT NULL, videoId TEXT, channelId TEXT, playlistId TEXT, searchSessionId TEXT, query TEXT, context TEXT, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_activities_timestamp ON user_activities(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_activities_type ON user_activities(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_activities_videoId ON user_activities(videoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_activities_channelId ON user_activities(channelId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_activities_playlistId ON user_activities(playlistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_activities_searchSessionId ON user_activities(searchSessionId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS transcripts (videoId TEXT NOT NULL, content TEXT, language TEXT, fetchedAt INTEGER, fetchState TEXT NOT NULL, errorMessage TEXT, PRIMARY KEY(videoId), FOREIGN KEY(videoId) REFERENCES videos(videoId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS comment_threads (threadId TEXT NOT NULL, videoId TEXT NOT NULL, topLevelCommentId TEXT, topLevelComment TEXT, replyCount INTEGER NOT NULL, fetchedAt INTEGER, fetchState TEXT NOT NULL, errorMessage TEXT, PRIMARY KEY(threadId), FOREIGN KEY(videoId) REFERENCES videos(videoId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comment_threads_videoId ON comment_threads(videoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comment_threads_fetchedAt ON comment_threads(fetchedAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS comments (commentId TEXT NOT NULL, threadId TEXT NOT NULL, videoId TEXT NOT NULL, parentCommentId TEXT, text TEXT NOT NULL, author TEXT, likeCount INTEGER, publishedAt TEXT, updatedAt TEXT, fetchedAt INTEGER, PRIMARY KEY(commentId), FOREIGN KEY(threadId) REFERENCES comment_threads(threadId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comments_threadId ON comments(threadId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comments_videoId ON comments(videoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comments_parentCommentId ON comments(parentCommentId)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS video_search USING FTS4(videoId UNINDEXED, title, description, channelName, tags, category, topic)")
            }
        }

        @Volatile private var instance: SubGrabDatabase? = null

        fun get(context: Context): SubGrabDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SubGrabDatabase::class.java,
                DB_NAME
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
