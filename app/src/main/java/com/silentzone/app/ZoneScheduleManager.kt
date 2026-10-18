package com.silentzone.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ZoneScheduleManager {

    private const val ACTION_START =
        "com.silentzone.app.SCHEDULE_START"

    private const val ACTION_END =
        "com.silentzone.app.SCHEDULE_END"

    /*
     * Check whether this zone is allowed
     * to change sound RIGHT NOW.
     *
     * Schedule OFF = always active.
     */
    fun isScheduleActive(
        zone: JSONObject
    ): Boolean {

        val enabled =
            zone.optBoolean(
                "schedule_enabled",
                false
            )

        if (!enabled) {
            return true
        }

        val startText =
            zone.optString(
                "schedule_start",
                ""
            )

        val endText =
            zone.optString(
                "schedule_end",
                ""
            )

        if (
            startText.isBlank() ||
            endText.isBlank()
        ) {
            return false
        }

        return try {

            val timezone =
                zone.optString(
                    "timezone",
                    "Asia/Kolkata"
                )

            val zoneId =
                ZoneId.of(timezone)

            val now =
                ZonedDateTime.now(
                    zoneId
                ).toLocalTime()

            val start =
                LocalTime.parse(
                    startText
                )

            val end =
                LocalTime.parse(
                    endText
                )

            /*
             * Normal schedule:
             * 13:00 → 14:00
             */
            if (start < end) {

                !now.isBefore(start) &&
                    now.isBefore(end)

            } else {

                /*
                 * Overnight schedule:
                 * 23:00 → 01:00
                 */
                !now.isBefore(start) ||
                    now.isBefore(end)
            }

        } catch (_: Exception) {

            false
        }
    }

    /*
     * Call after successful zone sync.
     */
    fun scheduleAll(
        context: Context
    ) {

        val zones =
            ZoneStorageManager.getZones(
                context
            )

        for (zone in zones) {

            if (
                !zone.optBoolean(
                    "is_active",
                    true
                )
            ) {
                continue
            }

            if (
                !zone.optBoolean(
                    "schedule_enabled",
                    false
                )
            ) {
                continue
            }

            scheduleZone(
                context,
                zone
            )
        }
    }

    private fun scheduleZone(
        context: Context,
        zone: JSONObject
    ) {

        val id =
            zone.optString(
                "id",
                ""
            )

        if (id.isBlank()) {
            return
        }

        val start =
            zone.optString(
                "schedule_start",
                ""
            )

        val end =
            zone.optString(
                "schedule_end",
                ""
            )

        if (
            start.isBlank() ||
            end.isBlank()
        ) {
            return
        }

        val timezone =
            zone.optString(
                "timezone",
                "Asia/Kolkata"
            )

        val startMillis =
            nextTimeMillis(
                start,
                timezone
            )

        val endMillis =
            nextTimeMillis(
                end,
                timezone
            )

        scheduleAlarm(
            context,
            id,
            true,
            startMillis
        )

        scheduleAlarm(
            context,
            id,
            false,
            endMillis
        )
    }

    private fun nextTimeMillis(
        timeText: String,
        timezone: String
    ): Long {

        val zoneId =
            ZoneId.of(
                timezone
            )

        val time =
            LocalTime.parse(
                timeText
            )

        val now =
            ZonedDateTime.now(
                zoneId
            )

        var target =
            ZonedDateTime.of(
                LocalDate.now(
                    zoneId
                ),
                time,
                zoneId
            )

        if (
            !target.isAfter(now)
        ) {

            target =
                target.plusDays(1)
        }

        return target
            .toInstant()
            .toEpochMilli()
    }

    private fun scheduleAlarm(
        context: Context,
        zoneId: String,
        isStart: Boolean,
        timeMillis: Long
    ) {

        val alarmManager =
            context.getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager

        val intent =
            Intent(
                context,
                ScheduleReceiver::class.java
            ).apply {

                action =
                    if (isStart) {
                        ACTION_START
                    } else {
                        ACTION_END
                    }

                putExtra(
                    "zone_id",
                    zoneId
                )
            }

        val requestCode =
            (
                zoneId +
                    if (isStart) {
                        "_START"
                    } else {
                        "_END"
                    }
                ).hashCode()

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S
            ) {

                if (
                    alarmManager
                        .canScheduleExactAlarms()
                ) {

                    alarmManager
                        .setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            timeMillis,
                            pendingIntent
                        )

                } else {

                    /*
                     * Permission not granted yet.
                     * Use normal alarm as fallback.
                     */
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        timeMillis,
                        pendingIntent
                    )
                }

            } else {

                alarmManager
                    .setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        timeMillis,
                        pendingIntent
                    )
            }

        } catch (_: Exception) {

            try {

                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    timeMillis,
                    pendingIntent
                )

            } catch (_: Exception) {
            }
        }
    }

    /*
     * Alarm fired.
     * Schedule tomorrow's alarms again.
     */
    fun reschedule(
        context: Context
    ) {

        scheduleAll(
            context
        )
    }
}
