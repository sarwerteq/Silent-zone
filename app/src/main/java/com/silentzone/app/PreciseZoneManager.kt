package com.silentzone.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object PreciseZoneManager{
    private const val PREFS="silent_zone_precise"
    private const val KEY_INSIDE="inside"
    private const val KEY_ZONE="last_zone_id"
    private const val EXIT_BUFFER=10.0

    fun checkCurrentLocation(context:Context,fallback:Location?=null,callback:((String)->Unit)?=null){
        if(!LocationProtectionManager.isLocationEnabled(context)){
            LocationProtectionManager.markUnverified(context)
            LocationProtectionManager.showWarning(context)
            callback?.invoke("Location is turned off")
            return
        }

        if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            LocationProtectionManager.markUnverified(context)
            callback?.invoke("Fine location permission required")
            return
        }

        val client=LocationServices.getFusedLocationProviderClient(context)
        val token=CancellationTokenSource()
        val request=CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setMaxUpdateAgeMillis(0)
            .setDurationMillis(15000)
            .build()

        try{
            client.getCurrentLocation(request,token.token)
                .addOnSuccessListener{evaluate(context,it?:fallback,callback)}
                .addOnFailureListener{evaluate(context,fallback,callback)}
        }catch(_:SecurityException){
            LocationProtectionManager.markUnverified(context)
            callback?.invoke("Location permission required")
        }
    }

    fun evaluate(context:Context,location:Location?,callback:((String)->Unit)?=null){
        if(!LocationProtectionManager.isLocationEnabled(context)){
            LocationProtectionManager.markUnverified(context)
            LocationProtectionManager.showWarning(context)
            callback?.invoke("Location is turned off")
            return
        }

        if(location==null){
            LocationProtectionManager.markUnverified(context)
            callback?.invoke("Location unavailable")
            return
        }

        if(location.hasAccuracy()&&location.accuracy>75f){
            LocationProtectionManager.markUnverified(context)
            callback?.invoke("Waiting for better GPS • Accuracy ±${location.accuracy.toInt()}m")
            return
        }

        LocationProtectionManager.markVerified(context)

        val zones=ZoneStorageManager.getZones(context)
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val wasInside=prefs.getBoolean(KEY_INSIDE,false)
        val previousId=prefs.getString(KEY_ZONE,null)

        var best:org.json.JSONObject?=null
        var bestDistance=Double.MAX_VALUE
        var physicalInside=false

        for(zone in zones){
            if(!zone.optBoolean("is_active",true))continue
            val lat=zone.optDouble("latitude",Double.NaN)
            val lon=zone.optDouble("longitude",Double.NaN)
            val radius=zone.optDouble("radius_m",zone.optDouble("radius_meters",0.0))
            if(!lat.isFinite()||!lon.isFinite()||radius<=0)continue

            val r=FloatArray(1)
            Location.distanceBetween(location.latitude,location.longitude,lat,lon,r)
            val d=r[0].toDouble()

            if(d<=radius&&d<bestDistance){
                physicalInside=true
                best=zone
                bestDistance=d
            }
        }

        if(physicalInside&&best!=null){
            val id=best.optString("id","")
            prefs.edit().putBoolean(KEY_INSIDE,true).putString(KEY_ZONE,id).apply()

            if(!ZoneScheduleManager.isScheduleActive(best)){
                SilentZonePauseManager.resume(context)
                callback?.invoke(SoundModeManager.restore(context)+" • Inside zone, schedule inactive")
                return
            }

            if(SilentZonePauseManager.isPaused(context,id)){
                callback?.invoke("Silent Zone paused")
                return
            }

            callback?.invoke(SoundModeManager.apply(context,best.optString("sound_action","SILENT")))
            return
        }

        if(wasInside){
            val old=zones.firstOrNull{it.optString("id","")==previousId}
            if(old!=null){
                val r=FloatArray(1)
                Location.distanceBetween(location.latitude,location.longitude,old.optDouble("latitude"),old.optDouble("longitude"),r)
                val radius=old.optDouble("radius_m",old.optDouble("radius_meters",0.0))

                if(r[0]<=radius+EXIT_BUFFER){
                    callback?.invoke("Near zone boundary")
                    return
                }
            }
        }

        prefs.edit().putBoolean(KEY_INSIDE,false).remove(KEY_ZONE).apply()
        SilentZonePauseManager.resetOnExit(context)
        callback?.invoke(SoundModeManager.restore(context))
    }
}
