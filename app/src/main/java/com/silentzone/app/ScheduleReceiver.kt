package com.silentzone.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScheduleReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val pending=goAsync()
        try{
            val isStart=intent.action==ZoneScheduleManager.ACTION_START
            val isEnd=intent.action==ZoneScheduleManager.ACTION_END
            if(!isStart&&!isEnd){pending.finish();return}

            val alarmZone=intent.getStringExtra("zone_id")
            val sid=intent.getStringExtra("schedule_id")
            val day=intent.getIntExtra("schedule_day",-1)
            val prefs=context.getSharedPreferences("silent_zone_precise",Context.MODE_PRIVATE)
            val inside=prefs.getBoolean("inside",false)
            val currentZone=prefs.getString("last_zone_id",null)
            val zones=ZoneStorageManager.getZones(context)

            if(inside&&!currentZone.isNullOrBlank()){
                val zone=zones.firstOrNull{it.optString("id","")==currentZone}
                if(zone!=null&&zone.optBoolean("is_active",true)){
                    if(ZoneScheduleManager.isScheduleActive(zone)){
                        if(!SilentZonePauseManager.isPaused(context,currentZone))
                            SoundModeManager.apply(context,zone.optString("sound_action","SILENT"))
                    }else SoundModeManager.restore(context)
                }else SoundModeManager.restore(context)
            }else SoundModeManager.restore(context)

            if(!alarmZone.isNullOrBlank()&&!sid.isNullOrBlank()&&day in 1..7){
                if(isStart){
                    ZoneScheduleManager.ensureEndAfterStart(context,alarmZone,sid,day)
                    ZoneScheduleManager.scheduleNext(context,alarmZone,sid,day,true)
                }else{
                    ZoneScheduleManager.scheduleNext(context,alarmZone,sid,day,false)
                }
            }
        }catch(e:Exception){
            Log.e("SilentZone","Schedule alarm failed",e)
        }finally{pending.finish()}
    }
}
