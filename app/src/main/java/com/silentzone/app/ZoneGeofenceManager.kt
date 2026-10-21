package com.silentzone.app
import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.location.*
object ZoneGeofenceManager {
    private const val MIN_OUTER = 100f
    fun registerSavedZones(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= 29 && context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val list = mutableListOf<Geofence>()
        for (z in ZoneStorageManager.getZones(context).take(50)) {
            val id=z.optString("id",""); val lat=z.optDouble("latitude",Double.NaN); val lon=z.optDouble("longitude",Double.NaN)
            val radius=z.optDouble("radius_m",z.optDouble("radius_meters",100.0)).toFloat()
            if(id.isBlank()||!lat.isFinite()||!lon.isFinite()) continue
            list += Geofence.Builder().setRequestId("OUTER:$id").setCircularRegion(lat,lon,maxOf(MIN_OUTER,radius)).setExpirationDuration(Geofence.NEVER_EXPIRE).setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT).setNotificationResponsiveness(30000).build()
            list += Geofence.Builder().setRequestId("INNER:$id").setCircularRegion(lat,lon,radius).setExpirationDuration(Geofence.NEVER_EXPIRE).setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT).setNotificationResponsiveness(10000).build()
        }
        val intent=Intent(context,GeofenceBroadcastReceiver::class.java)
        var flags=PendingIntent.FLAG_UPDATE_CURRENT
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S) flags=flags or PendingIntent.FLAG_MUTABLE
        val pi=PendingIntent.getBroadcast(context,0,intent,flags)
        val client=LocationServices.getGeofencingClient(context)
        try {
            client.removeGeofences(pi).addOnCompleteListener {
                if(list.isEmpty()) return@addOnCompleteListener
                val req=GeofencingRequest.Builder().setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER).addGeofences(list).build()
                try { client.addGeofences(req,pi) } catch (_:SecurityException){}
            }
        } catch (_:SecurityException){}
    }
}
