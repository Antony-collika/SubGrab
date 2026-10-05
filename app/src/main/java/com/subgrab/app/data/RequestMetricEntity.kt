package com.subgrab.app.data
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName="request_metrics")
data class RequestMetricEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val timestamp:Long,val lane:String,val operation:String,val durationMs:Long,val httpStatus:Int?,val success:Boolean,val failureType:String?)