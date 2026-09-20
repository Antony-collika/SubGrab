package com.subgrab.app.data
import com.subgrab.app.domain.*
class RequestGovernor {
 private data class LaneState(var state:GovernorState=GovernorState.NORMAL,var delay:Long=0,var negative:Int=0,var stable:Int=0)
 private val states=RequestLane.entries.associateWith{LaneState()}
 @Synchronized fun delay(lane:RequestLane)=states.getValue(lane).delay
 @Synchronized fun state(lane:RequestLane)=states.getValue(lane).state
 @Synchronized fun observe(lane:RequestLane,result:RequestResult):Boolean {
  val s=states.getValue(lane); val benign=FailureClassifier.benign(result.failureType ?: FailureType.UNKNOWN)
  val slowdown=result.httpStatus==429 || (result.httpStatus==403 && result.failureType in setOf(FailureType.ACCESS_DENIED,FailureType.BOT_DETECTION)) ||
   (!result.success && !benign && result.failureType in setOf(FailureType.TIMEOUT,FailureType.CONNECTION_ERROR,FailureType.SERVER_ERROR,FailureType.HTTP_403,FailureType.HTTP_429,FailureType.BOT_DETECTION,FailureType.ACCESS_DENIED,FailureType.PARSE_ERROR))
  if(slowdown){s.negative++;s.stable=0}else if(result.success||benign){s.negative=0;s.stable++}else{s.stable=0}
  var changed=false
  if((slowdown && s.negative>=2)||result.httpStatus==429){val old=s.delay;s.delay=(if(s.delay==0L)1500L else s.delay+1500L).coerceAtMost(10000L);s.state=GovernorState.SLOWDOWN;changed=old!=s.delay}
  if(s.stable>=5&&s.state==GovernorState.SLOWDOWN){s.delay=0;s.state=GovernorState.NORMAL;s.negative=0;changed=true}
  return changed
 }
}