package com.subgrab.app.data
import com.subgrab.app.domain.*
import kotlinx.coroutines.delay
import kotlin.random.Random
class RequestPacer(private val settings:SettingsRepository,private val governor:RequestGovernor,private val database:SubGrabDatabase){
 suspend fun <T> execute(operation:RequestOperation,block:suspend()->T):T {
  val s=settings.current(); val api=operation.lane==RequestLane.DISCOVERY_API||operation.lane==RequestLane.API_METADATA
  val base=if(api)s.apiBaseDelayMs else s.subtitleBaseDelayMs; val mode=if(api)s.apiDelayMode else s.subtitleDelayMode
  val min=if(api)s.apiJitterMinMs else s.subtitleJitterMinMs; val max=if(api)s.apiJitterMaxMs else s.subtitleJitterMaxMs
  val user=if(mode=="AUTO") base+(if(max>=min)Random.nextLong(min,max+1) else 0) else 0
  val wait=user+governor.delay(operation.lane); if(wait>0)delay(wait)
  val started=System.currentTimeMillis(); var status:Int?=null
  try { val value=block(); val result=RequestResult(true,status,System.currentTimeMillis()-started,null); persist(operation,result);governor.observe(operation.lane,result);return value }
  catch(t:Throwable){status=(t as? HttpFailure)?.status;val result=RequestResult(false,status,System.currentTimeMillis()-started,FailureClassifier.classify(status,t));persist(operation,result);governor.observe(operation.lane,result);throw t}
 }
 private suspend fun persist(op:RequestOperation,r:RequestResult){
  database.requestMetricDao().insert(RequestMetricEntity(timestamp=System.currentTimeMillis(),lane=op.lane.name,operation=op.operation,durationMs=r.durationMs,httpStatus=r.httpStatus,success=r.success,failureType=r.failureType?.name))
  database.runtimeLogDao().insert(RuntimeLogEntity(timestamp=System.currentTimeMillis(),level=if(r.success)"INFO" else "ERROR",category=if(r.success)"REQUEST" else "FAILURE",lane=op.lane.name,operation=op.operation,message="success="+r.success+" status="+r.httpStatus+" durationMs="+r.durationMs+" failure="+r.failureType?.name))
  val cutoff=System.currentTimeMillis()-30L*24*60*60*1000;database.requestMetricDao().deleteOlderThan(cutoff);database.runtimeLogDao().deleteOlderThan(cutoff)
 }
}
class HttpFailure(val status:Int,val body:String=""):RuntimeException("HTTP $status")