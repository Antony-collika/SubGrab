package com.subgrab.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subgrab.app.data.SubGrabDatabase
import org.junit.After
import org.junit.Assert.assertEquals
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
            .addMigrations(SubGrabDatabase.MIGRATION_1_2)
            .build()

        database!!.openHelper.writableDatabase
        val videos = kotlinx.coroutines.runBlocking { database!!.videoDao().getPage(10, 0) }
        assertEquals(0, videos.size)
    }

    companion object {
        private const val DB_NAME = "subgrab_migration_test.db"
    }
}
