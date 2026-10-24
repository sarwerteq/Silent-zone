package com.silentzone.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ZoneScheduleManager {

    const val ACTION_START = "com.silentzone.app.SCHEDULE_START"
    const val ACTION_END = "com.silentzone.app.SCHEDULE_END"
    private const val PREFS = "silent_zone_schedule_alarms"
    private const val KEY_ALARMS = "scheduled_alarms"

    fun isScheduleActive(zone: JSONObject, nowOverride: ZonedDateTime? = null): Boolean {
        if (!zone.optBoolean("schedule_enabled", false)) return true
        return try {
            val zoneId = try { ZoneId.of(zone.optString("timezone", "Asia/Kolkata")) } catch (_: Exception) { ZoneId.of("Asia/Kolkata") }
            val now = nowOverride?.withZoneSameInstant(zoneId) ?: ZonedDateTime.now(zoneId)
            val schedules = zone.optJSONArray("schedules")

            if (schedules != null && schedules.length() > 0) {
                for (i in 0 until schedules.length()) {
                    val schedule = schedules.optJSONObject(i) ?: continue
                    if (!schedule.optBoolean("is_active", true)) continue
                    if (scheduleActive(schedule, now)) return true
                }
                false
            } else {
                val start = zone.optString("schedule_start", "")
                val end = zone.optString("schedule_end", "")
                if (start.isBlank() || end.isBlank()) false
                else {
                    val legacy = JSONObject()
                        .put("start_time", start)
                        .put("end_time", end)
                        .put("days", JSONArray(listOf(1, 2, 3, 4, 5, 6, 7)))
                    scheduleActive(legacy, now)
                }
            }
        } catch (_: Exception) { false }
    }

    private fun scheduleActive(schedule: JSONObject, now: ZonedDateTime): Boolean {
        val start = parseTime(schedule.optString("start_time", "")) ?: return false
        val end = parseTime(schedule.optString("end_time", "")) ?: return false
        val days = readDays(schedule)
        val today = now.dayOfWeek.value
        val yesterday = if (today == 1) 7 else today - 1
        val currentTime = now.toLocalTime()

        if (start < end) return today in days && !currentTime.isBefore(start) && currentTime.isBefore(end)
        if (start > end) return (today in days && !currentTime.isBefore(start)) || (yesterday in days && currentTime.isBefore(end))
        return today in days
    }

    fun scheduleAll(context: Context) {
        cancelTracked(context)
        val tracked = mutableListOf<String>()
        val zones = ZoneStorageManager.getZones(context)

        for (zone in zones) {
            if (!zone.optBoolean("is_active", true)) continue
            if (!zone.optBoolean("schedule_enabled", false)) continue

            val zoneIdText = zone.optString("id", "")
            if (zoneIdText.isBlank()) continue

            val timezone = try { ZoneId.of(zone.optString("timezone", "Asia/Kolkata")) } catch (_: Exception) { ZoneId.of("Asia/Kolkata") }
            val schedules = effectiveSchedules(zone)

            for (i in 0 until schedules.length()) {
                val schedule = schedules.optJSONObject(i) ?: continue
                if (!schedule.optBoolean("is_active", true)) continue

                val start = parseTime(schedule.optString("start_time", "")) ?: continue
                val end = parseTime(schedule.optString("end_time", "")) ?: continue
                val scheduleId = schedule.optString("id", "schedule-$i")
                val days = readDays(schedule)

                for (day in days) {
                    val startAt = nextOccurrence(timezone, day, start)
                    val endDay = if (start >= end) (day % 7) + 1 else day
                    var endAt = nextOccurrence(timezone, endDay, end)
                    if (!endAt.isAfter(startAt)) endAt = endAt.plusWeeks(1)

                    tracked += scheduleAlarm(context, zoneIdText, scheduleId, day, true, startAt.toInstant().toEpochMilli())
                    tracked += scheduleAlarm(context, zoneIdText, scheduleId, day, false, endAt.toInstant().toEpochMilli())
                }
            }
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ALARMS, tracked.joinToString(";"))
            .apply()
    }

    fun reschedule(context: Context) = scheduleAll(context)

    private fun effectiveSchedules(zone: JSONObject): JSONArray {
        val schedules = zone.optJSONArray("schedules")
        if (schedules != null && schedules.length() > 0) return schedules

        val result = JSONArray()
        val start = zone.optString("schedule_start", "")
        val end = zone.optString("schedule_end", "")

        if (start.isNotBlank() && end.isNotBlank()) {
            result.put(
                JSONObject()
                    .put("id", "legacy")
                    .put("name", "Schedule")
                    .put("start_time", start)
                    .put("end_time", end)
                    .put("is_active", true)
                    .put("days", JSONArray(listOf(1, 2, 3, 4, 5, 6, 7)))
            )
        }
        return result
    }

    private fun readDays(schedule: JSONObject): Set<Int> {
        val array = schedule.optJSONArray("days") ?: return (1..7).toSet()
        val result = mutableSetOf<Int>()
        for (i in 0 until array.length()) {
            val day = array.optInt(i)
            if (day in 1..7) result.add(day)
        }
        return if (result.isEmpty()) (1..7).toSet() else result
    }

    private fun parseTime(value: String): LocalTime? = try { LocalTime.parse(value) } catch (_: Exception) { null }

    private fun nextOccurrence(zoneId: ZoneId, isoDay: Int, time: LocalTime): ZonedDateTime {
        val now = ZonedDateTime.now(zoneId)
        val today = LocalDate.now(zoneId)
        val delta = (isoDay - today.dayOfWeek.value + 7) % 7
        var target = ZonedDateTime.of(today.plusDays(delta.toLong()), time, zoneId)
        if (!target.isAfter(now)) target = target.plusWeeks(1)
        return target
    }

    private fun scheduleAlarm(context: Context, zoneId: String, scheduleId: String, day: Int, start: Boolean, millis: Long): String {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val action = if (start) ACTION_START else ACTION_END
        val requestCode = "$zoneId|$scheduleId|$day|${if (start) "S" else "E"}".hashCode()

        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            this.action = action
            putExtra("zone_id", zoneId)
            putExtra("schedule_id", scheduleId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
            }
        } catch (_: Exception) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
            } catch (_: Exception) {
                try { alarmManager.set(AlarmManager.RTC_WAKEUP, millis, pendingIntent) } catch (_: Exception) {}
            }
        }

        return "$requestCode,$action"
    }

    private fun cancelTracked(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ALARMS, "") ?: ""

        if (raw.isNotBlank()) {
            for (entry in raw.split(";")) {
                if (entry.isBlank()) continue
                val parts = entry.split(",", limit = 2)
                if (parts.size != 2) continue

                val requestCode = parts[0].toIntOrNull() ?: continue
                val action = parts[1]
                val intent = Intent(context, ScheduleReceiver::class.java).apply { this.action = action }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )

                if (pendingIntent != null) {
                    try { alarmManager.cancel(pendingIntent) } catch (_: Exception) {}
                    try { pendingIntent.cancel() } catch (_: Exception) {}
                }
            }
        }

        val oldRaw = prefs.getString("request_codes", "") ?: ""

        if (oldRaw.isNotBlank()) {
            for (value in oldRaw.split(",")) {
                val requestCode = value.toIntOrNull() ?: continue
                cancelOldAlarm(context, alarmManager, requestCode, ACTION_START)
                cancelOldAlarm(context, alarmManager, requestCode, ACTION_END)
            }
        }

        prefs.edit().remove(KEY_ALARMS).remove("request_codes").apply()
    }

    private fun cancelOldAlarm(context: Context, alarmManager: AlarmManager, requestCode: Int, action: String) {
        val intent = Intent(context, ScheduleReceiver::class.java).apply { this.action = action }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            try { alarmManager.cancel(pendingIntent) } catch (_: Exception) {}
            try { pendingIntent.cancel() } catch (_: Exception) {}
        }
    }
}
