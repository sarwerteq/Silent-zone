package com.silentzone.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build

object LocationProtectionManager{
    private const val PREFS="silent_zone_location_protection"
    private const val VERIFIED="location_verified"
    private const val CHANNEL="silent_zone_location_warning"
    private const val NOTIFICATION_ID=7001

    fun isLocationEnabled(c:Context):Boolean{
        val lm=c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P) lm.isLocationEnabled
        else try{lm.isProviderEnabled(LocationManager.GPS_PROVIDER)||lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)}catch(_:Exception){false}
    }

    fun markVerified(c:Context){
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(VERIFIED,true).apply()
        cancelWarning(c)
    }

    fun markUnverified(c:Context){
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(VERIFIED,false).apply()
    }

    fun isVerified(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(VERIFIED,false)

    fun canUseLocation(c:Context):Boolean{
        if(c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return false
        return isLocationEnabled(c)
    }

    fun showWarning(c:Context){
        val nm=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            nm.createNotificationChannel(NotificationChannel(CHANNEL,"Silent Zone location alerts",NotificationManager.IMPORTANCE_HIGH))
        }
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val n=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
            android.app.Notification.Builder(c,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Silent Zone protection limited")
                .setContentText("Location is turned off. Turn it on for automatic Silent Zone detection.")
                .setStyle(android.app.Notification.BigTextStyle().bigText("Location is turned off. Silent Zone cannot safely determine whether you are inside or outside a zone. Turn on Location to restore automatic protection."))
                .setAutoCancel(false).setOngoing(true).build()
        else
            android.app.Notification.Builder(c)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Silent Zone protection limited")
                .setContentText("Location is turned off. Turn it on for automatic Silent Zone detection.")
                .setAutoCancel(false).setOngoing(true).build()
        nm.notify(NOTIFICATION_ID,n)
    }

    fun cancelWarning(c:Context){
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }

    fun invalidateAfterBoot(c:Context){
        markUnverified(c)
    }
}
