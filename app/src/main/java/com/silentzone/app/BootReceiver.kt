package com.silentzone.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action!=Intent.ACTION_BOOT_COMPLETED)return
        val c=context.applicationContext
        val pending=goAsync()

        LocationProtectionManager.invalidateAfterBoot(c)
        ZoneScheduleManager.scheduleAll(c)

        if(!LocationProtectionManager.canUseLocation(c)){
            LocationProtectionManager.showWarning(c)
            pending.finish()
            return
        }

        ZoneGeofenceManager.registerSavedZones(c)

        PreciseZoneManager.checkCurrentLocation(c){
            pending.finish()
        }
    }
}
