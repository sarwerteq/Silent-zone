
package com.silentzone.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build

object LocationProtectionManager {
    private const val PREFS = "silent_zone_location_protection"
    private const val VERIFIED = "location_verified"
    private const val WARNING_SHOWN = "warning_shown"
    private const val CHANNEL = "silent_zone_location_warning"
    private const val NOTIFICATION_ID = 7001

    fun isLocationEnabled(c: Context): Boolean {
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lm.isLocationEnabled
        else try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { false }
    }

    fun markVerified(c: Context) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(VERIFIED, true).apply()
        cancelWarning(c)
    }

    fun markUnverified(c: Context) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(VERIFIED, false).apply()
    }

    fun isVerified(c: Context) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(VERIFIED, false)

    fun canUseLocation(c: Context): Boolean {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            isLocationEnabled(c)
    }

    fun showWarning(c: Context) {
        if (isLocationEnabled(c)) {
            cancelWarning(c)
            return
        }

        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(WARNING_SHOWN, false)) return

        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Silent Zone location alerts", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val builder = if (Build.VERSION.SDK_INT >= 26)
            android.app.Notification.Builder(c, CHANNEL)
        else android.app.Notification.Builder(c)

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Silent Zone protection limited")
            .setContentText("Location is turned off. Turn it on for automatic detection.")
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
        p.edit().putBoolean(WARNING_SHOWN, true).apply()
    }

    fun cancelWarning(c: Context) {
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIFICATION_ID)
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(WARNING_SHOWN, false).apply()
    }

    fun invalidateAfterBoot(c: Context) {
        markUnverified(c)
    }
}
