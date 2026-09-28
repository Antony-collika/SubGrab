package com.subgrab.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subgrab.app.data.SubGrabDatabase
import com.subgrab.app.data.repository.ResearchRepository
import com.subgrab.app.domain.ResearchFilters
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DataFoundationMigrationTest {
    private var database: SubGrabDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun migratesRuntimeDatabaseFromVersionOne() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DB_NAME)
        val file: File = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()

        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        legacy.execSQL("CREATE TABLE request_metrics (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, lane TEXT NOT NULL, operation TEXT NOT NULL, durationMs INTEGER NOT NULL, httpStatus INTEGER, success INTEGER NOT NULL, failureType TEXT)")
        legacy.execSQL("CREATE TABLE runtime_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, level TEXT NOT NULL, category TEXT NOT NULL, lane TEXT, operation TEXT, message TEXT NOT NULL)")
        legacy.execSQL("PRAGMA user_version = 1")
        legacy.close()

        database = Room.databaseBuilder(context, SubGrabDatabase::class.java, DB_NAME)
            .addMigrations(SubGrabDatabase.MIGRATION_1_2, SubGrabDatabase.MIGRATION_2_3)
            .build()

        database!!.openHelper.writableDatabase
        val videos = kotlinx.coroutines.runBlocking { database!!.videoDao().getPage(10, 0) }
        assertEquals(0, videos.size)
    }

    @Test
    fun migratesPublishedDateAndRebuildsDiacriticInsensitiveSearchIndex() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DB_NAME)
        val file: File = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()

        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        legacy.execSQL("CREATE TABLE request_metrics (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, lane TEXT NOT NULL, operation TEXT NOT NULL, durationMs INTEGER NOT NULL, httpStatus INTEGER, success INTEGER NOT NULL, failureType TEXT)")
        legacy.execSQL("CREATE TABLE runtime_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, level TEXT NOT NULL, category TEXT NOT NULL, lane TEXT, operation TEXT, message TEXT NOT NULL)")
        legacy.execSQL("PRAGMA user_version = 1")
        createVersionTwoSchema(legacy)
        legacy.execSQL("INSERT INTO videos(videoId, channelId, createdAt, updatedAt) VALUES ('v1', NULL, 1000, 1000)")
        legacy.execSQL("INSERT INTO video_metadata_snapshots(videoId, fetchedAt, title, description, publishedAt, tags, topic) VALUES ('v1', 1700000000000, 'Cà phê Việt Nam', 'Hướng dẫn cơ bản', '3 days ago', '', '')")
        legacy.execSQL("INSERT INTO video_search(videoId, title, description, channelName, tags, category, topic) VALUES ('v1', 'Cà phê Việt Nam', 'Hướng dẫn cơ bản', '', '', '', '')")
        legacy.execSQL("PRAGMA user_version = 2")
        legacy.close()

        database = Room.databaseBuilder(context, SubGrabDatabase::class.java, DB_NAME)
            .addMigrations(SubGrabDatabase.MIGRATION_2_3)
            .build()
        database!!.openHelper.writableDatabase

        val cursor = database!!.openHelper.writableDatabase.query(
            "SELECT publishedAtEpochMs, publishedAtIsApproximate FROM video_metadata_snapshots WHERE videoId = 'v1'"
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertTrue(it.getLong(0) > 0)
            assertEquals(1, it.getInt(1))
        }

        com.subgrab.app.data.SearchIndexRebuilder.rebuild(database!!.openHelper.writableDatabase)
        val ids = kotlinx.coroutines.runBlocking {
            database!!.searchDao().searchVideoIds("ca* AND phe*", 10)
        }
        assertEquals(listOf("v1"), ids)

        val publishedAt = database!!.openHelper.writableDatabase.query(
            "SELECT publishedAtEpochMs FROM video_metadata_snapshots WHERE videoId = 'v1'"
        ).use { it.moveToFirst(); it.getLong(0) }
        val filtered = kotlinx.coroutines.runBlocking {
            ResearchRepository(database!!).search(
                query = "",
                filters = ResearchFilters(
                    publishedFrom = publishedAt - 24L * 60L * 60L * 1000L,
                    publishedTo = publishedAt + 24L * 60L * 60L * 1000L
                )
            )
        }
        assertEquals(listOf("v1"), filtered.first.map { it.videoId })
    }

    private fun createVersionTwoSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE videos (videoId TEXT NOT NULL, channelId TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(videoId))")
        db.execSQL("CREATE TABLE channels (channelId TEXT NOT NULL, name TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(channelId))")
        db.execSQL("CREATE TABLE playlists (playlistId TEXT NOT NULL, channelId TEXT, title TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(playlistId))")
        db.execSQL("CREATE TABLE playlist_video (playlistId TEXT NOT NULL, videoId TEXT NOT NULL, position INTEGER, PRIMARY KEY(playlistId, videoId), FOREIGN KEY(playlistId) REFERENCES playlists(playlistId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(videoId) REFERENCES videos(videoId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_playlist_video_videoId ON playlist_video(videoId)")
        db.execSQL("CREATE TABLE video_metadata_snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, videoId TEXT NOT NULL, fetchedAt INTEGER NOT NULL, title TEXT NOT NULL, description TEXT, publishedAt TEXT, durationSeconds INTEGER, channelId TEXT, channelName TEXT, subscriberCount INTEGER, viewCount INTEGER, likeCount INTEGER, commentCount INTEGER, tags TEXT NOT NULL, category TEXT, topic TEXT NOT NULL, thumbnail TEXT, FOREIGN KEY(videoId) REFERENCES videos(videoId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_video_metadata_snapshots_videoId ON video_metadata_snapshots(videoId)")
        db.execSQL("CREATE INDEX index_video_metadata_snapshots_fetchedAt ON video_metadata_snapshots(fetchedAt)")
        db.execSQL("CREATE TABLE search_sessions (id TEXT NOT NULL, query TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX index_search_sessions_fetchedAt ON search_sessions(fetchedAt)")
        db.execSQL("CREATE INDEX index_search_sessions_query ON search_sessions(query)")
        db.execSQL("CREATE TABLE search_session_video (searchSessionId TEXT NOT NULL, videoId TEXT NOT NULL, position INTEGER, PRIMARY KEY(searchSessionId, videoId), FOREIGN KEY(searchSessionId) REFERENCES search_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(videoId) REFERENCES videos(videoId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_search_session_video_videoId ON search_session_video(videoId)")
        db.execSQL("CREATE TABLE user_activities (id TEXT NOT NULL, type TEXT NOT NULL, timestamp INTEGER NOT NULL, videoId TEXT, channelId TEXT, playlistId TEXT, searchSessionId TEXT, query TEXT, context TEXT, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX index_user_activities_timestamp ON user_activities(timestamp)")
        db.execSQL("CREATE INDEX index_user_activities_type ON user_activities(type)")
        db.execSQL("CREATE INDEX index_user_activities_videoId ON user_activities(videoId)")
        db.execSQL("CREATE INDEX index_user_activities_channelId ON user_activities(channelId)")
        db.execSQL("CREATE INDEX index_user_activities_playlistId ON user_activities(playlistId)")
        db.execSQL("CREATE INDEX index_user_activities_searchSessionId ON user_activities(searchSessionId)")
        db.execSQL("CREATE TABLE transcripts (videoId TEXT NOT NULL, content TEXT, language TEXT, fetchedAt INTEGER, fetchState TEXT NOT NULL, errorMessage TEXT, PRIMARY KEY(videoId), FOREIGN KEY(videoId) REFERENCES videos(videoId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE comment_threads (threadId TEXT NOT NULL, videoId TEXT NOT NULL, topLevelCommentId TEXT, topLevelComment TEXT, replyCount INTEGER NOT NULL, fetchedAt INTEGER, fetchState TEXT NOT NULL, errorMessage TEXT, PRIMARY KEY(threadId), FOREIGN KEY(videoId) REFERENCES videos(videoId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_comment_threads_videoId ON comment_threads(videoId)")
        db.execSQL("CREATE INDEX index_comment_threads_fetchedAt ON comment_threads(fetchedAt)")
        db.execSQL("CREATE TABLE comments (commentId TEXT NOT NULL, threadId TEXT NOT NULL, videoId TEXT NOT NULL, parentCommentId TEXT, text TEXT NOT NULL, author TEXT, likeCount INTEGER, publishedAt TEXT, updatedAt TEXT, fetchedAt INTEGER, PRIMARY KEY(commentId), FOREIGN KEY(threadId) REFERENCES comment_threads(threadId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_comments_threadId ON comments(threadId)")
        db.execSQL("CREATE INDEX index_comments_videoId ON comments(videoId)")
        db.execSQL("CREATE INDEX index_comments_parentCommentId ON comments(parentCommentId)")
        db.execSQL("CREATE VIRTUAL TABLE video_search USING FTS4(videoId UNINDEXED, title, description, channelName, tags, category, topic)")
    }

    companion object {
        private const val DB_NAME = "subgrab_migration_test.db"
    }
}
