package com.subgrab.app.data
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
@Database(entities=[RequestMetricEntity::class,RuntimeLogEntity::class],version=1,exportSchema=false)
abstract class SubGrabDatabase:RoomDatabase(){
 abstract fun requestMetricDao():RequestMetricDao
 abstract fun runtimeLogDao():RuntimeLogDao
 companion object {
  @Volatile private var instance:SubGrabDatabase?=null
  fun get(context:Context):SubGrabDatabase = instance ?: synchronized(this) {
   instance ?: Room.databaseBuilder(context.applicationContext,SubGrabDatabase::class.java,"subgrab_runtime.db").build().also{instance=it}
  }
 }
}