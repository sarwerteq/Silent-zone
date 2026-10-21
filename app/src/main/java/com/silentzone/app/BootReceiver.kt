package com.silentzone.app
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        ZoneGeofenceManager.registerSavedZones(context)
        ZoneScheduleManager.scheduleAll(context)
        PreciseZoneManager.checkCurrentLocation(context) { pending.finish() }
    }
}
