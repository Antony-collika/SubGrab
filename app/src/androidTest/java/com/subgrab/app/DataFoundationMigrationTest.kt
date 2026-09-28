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
        SubGrabDatabase.MIGRATION_1_2.migrate(legacy)
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
            database!!.searchDao().searchVideoIds("\"ca\"* AND \"phe\"*", 10)
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

    companion object {
        private const val DB_NAME = "subgrab_migration_test.db"
    }
}
