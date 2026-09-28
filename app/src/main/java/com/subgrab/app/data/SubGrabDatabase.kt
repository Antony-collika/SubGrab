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
    version = 3,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE video_metadata_snapshots ADD COLUMN publishedAtEpochMs INTEGER")
                db.execSQL("ALTER TABLE video_metadata_snapshots ADD COLUMN publishedAtIsApproximate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    UPDATE video_metadata_snapshots
                    SET publishedAtEpochMs = CAST(strftime('%s', publishedAt) AS INTEGER) * 1000
                    WHERE publishedAtEpochMs IS NULL
                      AND publishedAt IS NOT NULL
                      AND TRIM(publishedAt) <> ''
                      AND strftime('%s', publishedAt) IS NOT NULL
                """.trimIndent())
                db.execSQL("""
                    UPDATE video_metadata_snapshots
                    SET publishedAtEpochMs = fetchedAt - (
                        CAST(substr(TRIM(publishedAt), 1, instr(TRIM(publishedAt), ' ') - 1) AS INTEGER) *
                        CASE
                            WHEN lower(TRIM(publishedAt)) LIKE '% minute ago'
                              OR lower(TRIM(publishedAt)) LIKE '% minutes ago'
                              OR TRIM(publishedAt) LIKE '% phút trước' THEN 60000
                            WHEN lower(TRIM(publishedAt)) LIKE '% hour ago'
                              OR lower(TRIM(publishedAt)) LIKE '% hours ago'
                              OR TRIM(publishedAt) LIKE '% giờ trước' THEN 3600000
                            WHEN lower(TRIM(publishedAt)) LIKE '% day ago'
                              OR lower(TRIM(publishedAt)) LIKE '% days ago'
                              OR TRIM(publishedAt) LIKE '% ngày trước' THEN 86400000
                            WHEN lower(TRIM(publishedAt)) LIKE '% week ago'
                              OR lower(TRIM(publishedAt)) LIKE '% weeks ago'
                              OR TRIM(publishedAt) LIKE '% tuần trước' THEN 604800000
                            WHEN lower(TRIM(publishedAt)) LIKE '% month ago'
                              OR lower(TRIM(publishedAt)) LIKE '% months ago'
                              OR TRIM(publishedAt) LIKE '% tháng trước' THEN 2592000000
                            WHEN lower(TRIM(publishedAt)) LIKE '% year ago'
                              OR lower(TRIM(publishedAt)) LIKE '% years ago'
                              OR TRIM(publishedAt) LIKE '% năm trước' THEN 31536000000
                            ELSE NULL
                        END
                    ),
                    publishedAtIsApproximate = 1
                    WHERE publishedAtEpochMs IS NULL
                      AND publishedAt IS NOT NULL
                      AND instr(TRIM(publishedAt), ' ') > 0
                      AND (lower(TRIM(publishedAt)) LIKE '% ago' OR TRIM(publishedAt) LIKE '% trước')
                """.trimIndent())
            }
        }

        @Volatile private var instance: SubGrabDatabase? = null

        fun get(context: Context): SubGrabDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SubGrabDatabase::class.java,
                DB_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        val prefs = context.applicationContext.getSharedPreferences("subgrab_database_state", Context.MODE_PRIVATE)
                        if (prefs.getInt("search_index_version", 0) < 3) {
                            SearchIndexRebuilder.rebuild(db)
                            prefs.edit().putInt("search_index_version", 3).commit()
                        }
                    }
                })
                .build().also { instance = it }
        }
    }
}


private object SearchTextNormalizer {
    fun normalize(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('đ', 'd')
}

private object SearchIndexRebuilder {
    fun rebuild(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM video_search")
            db.query("""
                SELECT m.videoId, m.title, m.description, m.channelName, m.tags, m.category, m.topic
                FROM video_metadata_snapshots m
                WHERE m.id = (
                    SELECT ms.id FROM video_metadata_snapshots ms
                    WHERE ms.videoId = m.videoId
                    ORDER BY ms.fetchedAt DESC LIMIT 1
                )
            """.trimIndent()).use { cursor ->
                val videoId = cursor.getColumnIndexOrThrow("videoId")
                val title = cursor.getColumnIndexOrThrow("title")
                val description = cursor.getColumnIndexOrThrow("description")
                val channelName = cursor.getColumnIndexOrThrow("channelName")
                val tags = cursor.getColumnIndexOrThrow("tags")
                val category = cursor.getColumnIndexOrThrow("category")
                val topic = cursor.getColumnIndexOrThrow("topic")
                while (cursor.moveToNext()) {
                    val values = android.content.ContentValues().apply {
                        put("videoId", cursor.getString(videoId))
                        put("title", SearchTextNormalizer.normalize(cursor.getString(title)))
                        put("description", SearchTextNormalizer.normalize(if (cursor.isNull(description)) "" else cursor.getString(description)))
                        put("channelName", SearchTextNormalizer.normalize(if (cursor.isNull(channelName)) "" else cursor.getString(channelName)))
                        put("tags", SearchTextNormalizer.normalize(cursor.getString(tags)))
                        put("category", SearchTextNormalizer.normalize(if (cursor.isNull(category)) "" else cursor.getString(category)))
                        put("topic", SearchTextNormalizer.normalize(cursor.getString(topic)))
                    }
                    db.insert("video_search", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
