package com.silentzone.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val pendingResult = goAsync()

        try {

            /*
             * Schedule time par fresh GPS ka wait nahi karenge.
             *
             * PreciseZoneManager / Geofence receiver already maintain:
             *
             * silent_zone_precise
             *   inside
             *   last_zone_id
             *
             * Isliye alarm fire hote hi cached physical-zone state
             * se schedule evaluate kiya ja sakta hai.
             */

            val prefs = context.getSharedPreferences(
                "silent_zone_precise",
                Context.MODE_PRIVATE
            )

            val isInside =
                prefs.getBoolean("inside", false)

            val zoneId =
                prefs.getString("last_zone_id", null)

            val zones =
                ZoneStorageManager.getZones(context)

            if (isInside && !zoneId.isNullOrBlank()) {

                val zone = zones.firstOrNull {
                    it.optString("id", "") == zoneId
                }

                if (zone != null &&
                    zone.optBoolean("is_active", true)
                ) {

                    val scheduleActive =
                        ZoneScheduleManager.isScheduleActive(zone)

                    if (scheduleActive) {

                        if (
                            !SilentZonePauseManager.isPaused(
                                context,
                                zoneId
                            )
                        ) {

                            SoundModeManager.apply(
                                context,
                                zone.optString(
                                    "sound_action",
                                    "SILENT"
                                )
                            )
                        }

                    } else {

                        /*
                         * User physically zone ke andar hai,
                         * lekin ab koi schedule active nahi.
                         *
                         * Overlapping schedules automatically safe hain,
                         * kyunki isScheduleActive() tab tak TRUE dega
                         * jab tak koi bhi schedule active hai.
                         */

                        SoundModeManager.restore(context)
                    }

                } else {

                    /*
                     * Cached zone remove/deactivate ho chuka hai.
                     */

                    SoundModeManager.restore(context)
                }

            } else {

                /*
                 * Saved state ke according user kisi zone me nahi hai.
                 */

                SoundModeManager.restore(context)
            }

        } catch (_: Exception) {

            /*
             * Receiver crash nahi hona chahiye.
             */

        } finally {

            /*
             * Next start/end alarms ko calculate kar do.
             */

            try {
                ZoneScheduleManager.reschedule(context)
            } catch (_: Exception) {
            }

            pendingResult.finish()
        }
    }
}
