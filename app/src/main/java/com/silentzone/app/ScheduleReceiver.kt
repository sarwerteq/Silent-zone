
package com.silentzone.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.applicationContext
        val pending = goAsync()

        try {
            val isStart = intent.action == ZoneScheduleManager.ACTION_START
            val isEnd = intent.action == ZoneScheduleManager.ACTION_END
            if (!isStart && !isEnd) {
                pending.finish()
                return
            }

            val zid = intent.getStringExtra("zone_id")
            val sid = intent.getStringExtra("schedule_id")
            val day = intent.getIntExtra("schedule_day", -1)

            if (!zid.isNullOrBlank() && !sid.isNullOrBlank() && day in 1..7) {
                if (isStart) {
                    ZoneScheduleManager.ensureEndAfterStart(c, zid, sid, day)
                    ZoneScheduleManager.scheduleNext(c, zid, sid, day, true)
                } else {
                    ZoneScheduleManager.scheduleNext(c, zid, sid, day, false)
                }
            }

            if (!LocationProtectionManager.canUseLocation(c)) {
                LocationProtectionManager.markUnverified(c)

                if (!LocationProtectionManager.isLocationEnabled(c))
                    LocationProtectionManager.showWarning(c)

                if (isEnd) SoundModeManager.restore(c)

                pending.finish()
                return
            }

            PreciseZoneManager.checkCurrentLocation(c) {
                if (!LocationProtectionManager.isVerified(c))
                    BootReceiver.scheduleRetry(c)
                pending.finish()
            }

        } catch (e: Exception) {
            Log.e("SilentZone", "Schedule alarm failed", e)
            pending.finish()
        }
    }
}
