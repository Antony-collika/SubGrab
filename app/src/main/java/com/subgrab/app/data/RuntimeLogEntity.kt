package com.subgrab.app.data
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName="runtime_logs")
data class RuntimeLogEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val timestamp:Long,val level:String,val category:String,val lane:String?,val operation:String?,val message:String)