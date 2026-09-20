package com.subgrab.app.data
import androidx.room.*
@Dao interface RequestMetricDao {
 @Insert suspend fun insert(value:RequestMetricEntity)
 @Query("DELETE FROM request_metrics WHERE timestamp < :cutoff") suspend fun deleteOlderThan(cutoff:Long)
 @Query("SELECT * FROM request_metrics WHERE lane=:lane ORDER BY timestamp DESC") suspend fun byLane(lane:String):List<RequestMetricEntity>
 @Query("SELECT * FROM request_metrics ORDER BY timestamp DESC") suspend fun all():List<RequestMetricEntity>
}