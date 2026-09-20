package com.subgrab.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subgrab.app.data.SettingsRepository
import com.subgrab.app.domain.*
import kotlinx.coroutines.launch
@Composable fun SettingsScreen(repository:SettingsRepository,onBack:()->Unit,onDiagnostics:()->Unit={}){
 val scope=rememberCoroutineScope();val stored by repository.settings.collectAsState(initial=AppSettings());var value by remember(stored){mutableStateOf(stored)}
 fun save(v:AppSettings){value=v;scope.launch{repository.update(v)}}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("Thiết lập phụ đề và thư mục tải xuống",style=MaterialTheme.typography.bodyMedium)
  Text("Ngôn ngữ phụ đề",style=MaterialTheme.typography.titleMedium)
  LanguageToggle("Tiếng Việt","vi",value.languages){save(value.copy(languages=it))};LanguageToggle("English","en",value.languages){save(value.copy(languages=it))}
  Text("Định dạng",style=MaterialTheme.typography.titleMedium)
  FormatToggle("TXT",OutputFormat.TXT,value.formats){save(value.copy(formats=it))};FormatToggle("SRT",OutputFormat.SRT,value.formats){save(value.copy(formats=it))}
  if(OutputFormat.SRT in value.formats){Text("Timestamp",style=MaterialTheme.typography.titleMedium);TimestampToggle("Có timestamp",SubtitleTimestampMode.WITH_TIMESTAMP,value.timestampMode){save(value.copy(timestampMode=it))};TimestampToggle("Không timestamp",SubtitleTimestampMode.WITHOUT_TIMESTAMP,value.timestampMode){save(value.copy(timestampMode=it))}}
  HorizontalDivider();Button(onClick=onDiagnostics,modifier=Modifier.fillMaxWidth()){Text("DIAGNOSTICS")}\n  Text("YouTube Data API",style=MaterialTheme.typography.titleMedium)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Bật API");Switch(checked=value.useYouTubeDataApi,onCheckedChange={save(value.copy(useYouTubeDataApi=it))})}
  OutlinedTextField(value=value.youtubeDataApiKey,onValueChange={save(value.copy(youtubeDataApiKey=it))},label={Text("YouTube Data API key")},singleLine=true,modifier=Modifier.fillMaxWidth())
  PacingSection("Subtitle Requests",value.subtitleDelayMode,value.subtitleBaseDelayMs,value.subtitleJitterMinMs,value.subtitleJitterMaxMs,value.subtitleConcurrency,
   {save(value.copy(subtitleDelayMode=it))},{save(value.copy(subtitleBaseDelayMs=it))},{save(value.copy(subtitleJitterMinMs=it))},{save(value.copy(subtitleJitterMaxMs=it))},{save(value.copy(subtitleConcurrency=it.coerceAtLeast(1)))},true)
  PacingSection("API Requests",value.apiDelayMode,value.apiBaseDelayMs,value.apiJitterMinMs,value.apiJitterMaxMs,null,
   {save(value.copy(apiDelayMode=it))},{save(value.copy(apiBaseDelayMs=it))},{save(value.copy(apiJitterMinMs=it))},{save(value.copy(apiJitterMaxMs=it))},{},false)
  HorizontalDivider()
  OutlinedTextField(value=value.outputDir,onValueChange={save(value.copy(outputDir=it))},label={Text("Thư mục trong Downloads")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Ưu tiên phụ đề chính thức");Switch(checked=value.preferManualSub,onCheckedChange={save(value.copy(preferManualSub=it))})}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Bỏ qua video không có sub");Switch(checked=value.skipNoSub,onCheckedChange={save(value.copy(skipNoSub=it))})}
 }
}
@Composable private fun PacingSection(title:String,mode:String,base:Long,jmin:Long,jmax:Long,concurrency:Int?,setMode:(String)->Unit,setBase:(Long)->Unit,setMin:(Long)->Unit,setMax:(Long)->Unit,setConcurrency:(Int)->Unit,auto:Boolean){
 Text(title,style=MaterialTheme.typography.titleMedium)
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=mode=="NONE",onClick={setMode("NONE")},label={Text("None")});if(auto)FilterChip(selected=mode=="AUTO",onClick={setMode("AUTO")},label={Text("Auto")})}
 NumberField("BaseDelay",base,setBase);NumberField("Jitter min",jmin,setMin);NumberField("Jitter max",jmax,setMax)
 concurrency?.let{NumberField("Concurrency",it.toLong(),{setConcurrency(it.toIntOrNull()?:1)})}
}
@Composable private fun NumberField(label:String,value:Long,onChange:(Long)->Unit){OutlinedTextField(value=if(value==0L)"" else value.toString(),onValueChange={it.toLongOrNull()?.takeIf{n->n>=0}?.let(onChange)},label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth())}
@Composable private fun LanguageToggle(label:String,code:String,selected:List<String>,onChange:(List<String>)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label);Checkbox(checked=code in selected,onCheckedChange={checked->val n=if(checked)(selected+code).distinct()else selected-code;if(n.isNotEmpty())onChange(n)})}}
@Composable private fun TimestampToggle(label:String,mode:SubtitleTimestampMode,selected:SubtitleTimestampMode,onChange:(SubtitleTimestampMode)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label);RadioButton(selected=mode==selected,onClick={onChange(mode)})}}
@Composable private fun FormatToggle(label:String,format:OutputFormat,selected:Set<OutputFormat>,onChange:(Set<OutputFormat>)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(".$label");Checkbox(checked=format in selected,onCheckedChange={checked -> val n=if(checked)selected+format else selected-format;if(n.isNotEmpty())onChange(n)})}}