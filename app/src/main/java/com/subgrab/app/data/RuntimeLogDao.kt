package com.subgrab.app.data
import androidx.room.*
@Dao interface RuntimeLogDao {
 @Insert suspend fun insert(value:RuntimeLogEntity)
 @Query("DELETE FROM runtime_logs WHERE timestamp < :cutoff") suspend fun deleteOlderThan(cutoff:Long)
 @Query("SELECT * FROM runtime_logs ORDER BY timestamp DESC LIMIT :limit") suspend fun latest(limit:Int):List<RuntimeLogEntity>
}