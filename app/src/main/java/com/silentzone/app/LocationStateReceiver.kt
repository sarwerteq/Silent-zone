package com.silentzone.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager

class LocationStateReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action!=LocationManager.MODE_CHANGED_ACTION)return
        val c=context.applicationContext

        if(!LocationProtectionManager.isLocationEnabled(c)){
            LocationProtectionManager.markUnverified(c)
            LocationProtectionManager.showWarning(c)
            return
        }

        val pending=goAsync()
        LocationProtectionManager.markUnverified(c)
        LocationProtectionManager.cancelWarning(c)

        ZoneGeofenceManager.registerSavedZones(c)

        PreciseZoneManager.checkCurrentLocation(c){
            pending.finish()
        }
    }
}
