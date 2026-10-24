package com.silentzone.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ZoneScheduleManager {
    const val ACTION_START = "com.silentzone.app.SCHEDULE_START"
    const val ACTION_END = "com.silentzone.app.SCHEDULE_END"
    private const val PREFS = "silent_zone_schedule_alarms"
    private const val KEY_ALARMS = "scheduled_alarms"

    fun isScheduleActive(zone: JSONObject, nowOverride: ZonedDateTime? = null): Boolean {
        if (!zone.optBoolean("schedule_enabled", false)) return true
        return try {
            val tz = try { ZoneId.of(zone.optString("timezone","Asia/Kolkata")) } catch (_:Exception){ ZoneId.of("Asia/Kolkata") }
            val now = nowOverride?.withZoneSameInstant(tz) ?: ZonedDateTime.now(tz)
            val arr = effectiveSchedules(zone)
            for(i in 0 until arr.length()){
                val s=arr.optJSONObject(i)?:continue
                if(s.optBoolean("is_active",true) && active(s,now)) return true
            }
            false
        } catch(_:Exception){ false }
    }

    private fun active(s:JSONObject, now:ZonedDateTime):Boolean{
        val start=time(s.optString("start_time",""))?:return false
        val end=time(s.optString("end_time",""))?:return false
        val days=days(s); val today=now.dayOfWeek.value
        val yesterday=if(today==1) 7 else today-1
        val t=now.toLocalTime()
        if(start<end) return today in days && !t.isBefore(start) && t.isBefore(end)
        if(start>end) return (today in days && !t.isBefore(start)) || (yesterday in days && t.isBefore(end))
        return today in days
    }

    fun scheduleAll(context:Context){
        cancelAll(context)
        val tracked=mutableListOf<String>()
        for(zone in ZoneStorageManager.getZones(context)){
            if(!zone.optBoolean("is_active",true)||!zone.optBoolean("schedule_enabled",false)) continue
            val zid=zone.optString("id",""); if(zid.isBlank()) continue
            val tz=try{ZoneId.of(zone.optString("timezone","Asia/Kolkata"))}catch(_:Exception){ZoneId.of("Asia/Kolkata")}
            val arr=effectiveSchedules(zone)
            for(i in 0 until arr.length()){
                val s=arr.optJSONObject(i)?:continue
                if(!s.optBoolean("is_active",true)) continue
                val st=time(s.optString("start_time",""))?:continue
                val en=time(s.optString("end_time",""))?:continue
                val sid=s.optString("id","schedule-$i")
                for(day in days(s)){
                    val startAt=next(tz,day,st)
                    val endDay=if(st>=en) (day%7)+1 else day
                    var endAt=next(tz,endDay,en)
                    if(!endAt.isAfter(startAt)) endAt=endAt.plusWeeks(1)
                    tracked+=set(context,zid,sid,day,true,startAt.toInstant().toEpochMilli())
                    tracked+=set(context,zid,sid,day,false,endAt.toInstant().toEpochMilli())
                }
            }
        }
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString(KEY_ALARMS,tracked.joinToString(";")).apply()
    }

    fun scheduleNext(context:Context,zid:String,sid:String,day:Int,startAlarm:Boolean){
        val zone=ZoneStorageManager.getZones(context).firstOrNull{it.optString("id","")==zid}?:return
        if(!zone.optBoolean("is_active",true)||!zone.optBoolean("schedule_enabled",false)) return
        val tz=try{ZoneId.of(zone.optString("timezone","Asia/Kolkata"))}catch(_:Exception){ZoneId.of("Asia/Kolkata")}
        val arr=effectiveSchedules(zone)
        var s:JSONObject?=null
        for(i in 0 until arr.length()){
            val x=arr.optJSONObject(i)?:continue
            if(x.optString("id","schedule-$i")==sid){s=x;break}
        }
        val sch=s?:return
        if(!sch.optBoolean("is_active",true)||day !in days(sch)) return
        val st=time(sch.optString("start_time",""))?:return
        val en=time(sch.optString("end_time",""))?:return
        val targetDay=if(startAlarm) day else if(st>=en) (day%7)+1 else day
        val targetTime=if(startAlarm) st else en
        set(context,zid,sid,day,startAlarm,next(tz,targetDay,targetTime).toInstant().toEpochMilli())
    }

    fun ensureEndAfterStart(context:Context,zid:String,sid:String,day:Int){
        val zone=ZoneStorageManager.getZones(context).firstOrNull{it.optString("id","")==zid}?:return
        val tz=try{ZoneId.of(zone.optString("timezone","Asia/Kolkata"))}catch(_:Exception){ZoneId.of("Asia/Kolkata")}
        val arr=effectiveSchedules(zone)
        var s:JSONObject?=null
        for(i in 0 until arr.length()){
            val x=arr.optJSONObject(i)?:continue
            if(x.optString("id","schedule-$i")==sid){s=x;break}
        }
        val sch=s?:return
        val st=time(sch.optString("start_time",""))?:return
        val en=time(sch.optString("end_time",""))?:return
        val now=ZonedDateTime.now(tz)
        var endDate=now.toLocalDate()
        if(st>=en && !now.toLocalTime().isBefore(st)) endDate=endDate.plusDays(1)
        var endAt=ZonedDateTime.of(endDate,en,tz)
        if(!endAt.isAfter(now)) endAt=endAt.plusDays(1)
        set(context,zid,sid,day,false,endAt.toInstant().toEpochMilli())
    }

    private fun effectiveSchedules(zone:JSONObject):JSONArray{
        val a=zone.optJSONArray("schedules")
        if(a!=null&&a.length()>0)return a
        val r=JSONArray(); val st=zone.optString("schedule_start",""); val en=zone.optString("schedule_end","")
        if(st.isNotBlank()&&en.isNotBlank()) r.put(JSONObject().put("id","legacy").put("start_time",st).put("end_time",en)
            .put("is_active",true).put("days",JSONArray(listOf(1,2,3,4,5,6,7))))
        return r
    }

    private fun days(s:JSONObject):Set<Int>{
        val a=s.optJSONArray("days")?:return (1..7).toSet()
        val r=mutableSetOf<Int>()
        for(i in 0 until a.length()){val d=a.optInt(i);if(d in 1..7)r.add(d)}
        return if(r.isEmpty())(1..7).toSet() else r
    }

    private fun time(v:String)=try{LocalTime.parse(v)}catch(_:Exception){null}

    private fun next(tz:ZoneId,day:Int,t:LocalTime):ZonedDateTime{
        val now=ZonedDateTime.now(tz); val today=LocalDate.now(tz)
        val delta=(day-today.dayOfWeek.value+7)%7
        var x=ZonedDateTime.of(today.plusDays(delta.toLong()),t,tz)
        if(!x.isAfter(now))x=x.plusWeeks(1)
        return x
    }

    private fun set(c:Context,zid:String,sid:String,day:Int,start:Boolean,millis:Long):String{
        val am=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val action=if(start)ACTION_START else ACTION_END
        val code="$zid|$sid|$day|${if(start)"S" else "E"}".hashCode()
        val i=Intent(c,ScheduleReceiver::class.java).apply{
            this.action=action;putExtra("zone_id",zid);putExtra("schedule_id",sid)
            putExtra("schedule_day",day);putExtra("schedule_start",start)
        }
        val pi=PendingIntent.getBroadcast(c,code,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try{
            if(Build.VERSION.SDK_INT<Build.VERSION_CODES.S||am.canScheduleExactAlarms())
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,millis,pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,millis,pi)
        }catch(_:Exception){
            try{am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,millis,pi)}
            catch(_:Exception){am.set(AlarmManager.RTC_WAKEUP,millis,pi)}
        }
        return "$code,$action"
    }

    private fun cancelAll(c:Context){
        val am=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val raw=p.getString(KEY_ALARMS,"")?:""
        for(e in raw.split(";")){
            val x=e.split(",",limit=2);if(x.size!=2)continue
            val code=x[0].toIntOrNull()?:continue
            val i=Intent(c,ScheduleReceiver::class.java).apply{action=x[1]}
            val pi=PendingIntent.getBroadcast(c,code,i,PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if(pi!=null){try{am.cancel(pi)}catch(_:Exception){};try{pi.cancel()}catch(_:Exception){}}
        }
        p.edit().remove(KEY_ALARMS).apply()
    }
}
