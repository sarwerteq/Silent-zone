package com.silentzone.app
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        PreciseZoneManager.checkCurrentLocation(context) {
            ZoneScheduleManager.reschedule(context)
            pending.finish()
        }
    }
}
