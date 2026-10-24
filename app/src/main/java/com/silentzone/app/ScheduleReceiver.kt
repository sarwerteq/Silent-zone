package com.silentzone.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScheduleReceiver : BroadcastReceiver() {

    companion object { private const val TAG = "SilentZoneSchedule" }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        try {
            val action = intent.action
            val isStart = action == ZoneScheduleManager.ACTION_START
            val isEnd = action == ZoneScheduleManager.ACTION_END
            if (!isStart && !isEnd) return

            val prefs = context.getSharedPreferences("silent_zone_precise", Context.MODE_PRIVATE)
            val isInside = prefs.getBoolean("inside", false)
            val currentZoneId = prefs.getString("last_zone_id", null)
            val zones = ZoneStorageManager.getZones(context)

            if (isInside && !currentZoneId.isNullOrBlank()) {
                val zone = zones.firstOrNull { it.optString("id", "") == currentZoneId }

                if (zone != null && zone.optBoolean("is_active", true)) {
                    if (ZoneScheduleManager.isScheduleActive(zone)) {
                        if (!SilentZonePauseManager.isPaused(context, currentZoneId)) {
                            SoundModeManager.apply(context, zone.optString("sound_action", "SILENT"))
                            Log.d(TAG, "Schedule active -> apply sound mode")
                        }
                    } else {
                        SoundModeManager.restore(context)
                        Log.d(TAG, "No active schedule -> NORMAL")
                    }
                } else {
                    SoundModeManager.restore(context)
                    Log.d(TAG, "Zone unavailable -> NORMAL")
                }
            } else {
                SoundModeManager.restore(context)
                Log.d(TAG, "Outside zone -> NORMAL")
            }

            val zoneId = intent.getStringExtra("zone_id")
            val scheduleId = intent.getStringExtra("schedule_id")
            val day = intent.getIntExtra("schedule_day", -1)
            val startAlarm = intent.getBooleanExtra("schedule_start", isStart)

            if (!zoneId.isNullOrBlank() && !scheduleId.isNullOrBlank() && day in 1..7) {
                ZoneScheduleManager.scheduleNextAlarm(context, zoneId, scheduleId, day, startAlarm)
                Log.d(TAG, "Next ${if (startAlarm) "START" else "END"} alarm scheduled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Schedule receiver error", e)
        } finally {
            pendingResult.finish()
        }
    }
}
