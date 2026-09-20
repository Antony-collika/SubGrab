package com.subgrab.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subgrab.app.data.*
import com.subgrab.app.domain.RequestLane
import kotlinx.coroutines.launch
@Composable fun DiagnosticsScreen(database:SubGrabDatabase,onBack:()->Unit){
 val scope=rememberCoroutineScope();var metrics by remember{mutableStateOf<List<RequestMetricEntity>>(emptyList())};var logs by remember{mutableStateOf<List<RuntimeLogEntity>>(emptyList())}
 fun refresh(){scope.launch{val cutoff=System.currentTimeMillis()-30L*24*60*60*1000;database.requestMetricDao().deleteOlderThan(cutoff);database.runtimeLogDao().deleteOlderThan(cutoff);metrics=database.requestMetricDao().all();logs=database.runtimeLogDao().latest(200)}}
 LaunchedEffect(Unit){refresh()}
 Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={refresh()},modifier=Modifier.weight(1f)){Text("Làm mới")};OutlinedButton(onClick=onBack,modifier=Modifier.weight(1f)){Text("Quay lại")}}
  LazyColumn(Modifier.fillMaxWidth().weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
   item{Text("Request metrics",style=MaterialTheme.typography.titleLarge)}
   items(RequestLane.entries){lane->
    val rows=metrics.filter{it.lane==lane.name};val d=rows.map{it.durationMs}.sorted()
    val benign=rows.count{it.failureType in setOf("NO_SUBTITLE","LANGUAGE_UNAVAILABLE","VIDEO_UNAVAILABLE")}
    val negative=rows.count{!it.success&&it.failureType !in setOf("NO_SUBTITLE","LANGUAGE_UNAVAILABLE","VIDEO_UNAVAILABLE")}
    Text("${lane.name}: total=${rows.size}, success=${rows.count{it.success}}, benign=$benign, negative=$negative, avg=${if(d.isEmpty())0 else d.average().toLong()}ms, p50=${percentile(d,.50)}ms, p95=${percentile(d,.95)}ms")
   }
   item{Text("Runtime log",style=MaterialTheme.typography.titleLarge)}
   items(logs){log->Text("${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.US).format(java.util.Date(log.timestamp))} [${log.category}] ${log.message}",style=MaterialTheme.typography.bodySmall)}
  }
 }
}
private fun percentile(values:List<Long>,p:Double):Long {
 if(values.isEmpty()) return 0
 if(values.size == 1) return values[0]
 val position = (values.size - 1) * p.coerceIn(0.0, 1.0)
 val lower = position.toInt()
 val upper = kotlin.math.ceil(position).toInt().coerceAtMost(values.lastIndex)
 if(lower == upper) return values[lower]
 val fraction = position - lower
 return (values[lower] + (values[upper] - values[lower]) * fraction).toLong()
}
