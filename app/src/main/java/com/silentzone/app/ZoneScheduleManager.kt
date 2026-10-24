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
                    val s = schedules.optJSONObject(i) ?: continue
                    if (s.optBoolean("is_active", true) && scheduleActive(s, now)) return true
                }
                false
            } else {
                val start = zone.optString("schedule_start", "")
                val end = zone.optString("schedule_end", "")
                if (start.isBlank() || end.isBlank()) false
                else scheduleActive(JSONObject().put("start_time", start).put("end_time", end).put("days", JSONArray(listOf(1,2,3,4,5,6,7))), now)
            }
        } catch (_: Exception) { false }
    }

    private fun scheduleActive(s: JSONObject, now: ZonedDateTime): Boolean {
        val start = parseTime(s.optString("start_time", "")) ?: return false
        val end = parseTime(s.optString("end_time", "")) ?: return false
        val days = readDays(s)
        val today = now.dayOfWeek.value
        val yesterday = if (today == 1) 7 else today - 1
        val time = now.toLocalTime()
        if (start < end) return today in days && !time.isBefore(start) && time.isBefore(end)
        if (start > end) return (today in days && !time.isBefore(start)) || (yesterday in days && time.isBefore(end))
        return today in days
    }

    fun scheduleAll(context: Context) {
        cancelTracked(context)
        val tracked = mutableListOf<String>()
        for (zone in ZoneStorageManager.getZones(context)) {
            if (!zone.optBoolean("is_active", true) || !zone.optBoolean("schedule_enabled", false)) continue
            val zoneId = zone.optString("id", "")
            if (zoneId.isBlank()) continue
            val tz = try { ZoneId.of(zone.optString("timezone", "Asia/Kolkata")) } catch (_: Exception) { ZoneId.of("Asia/Kolkata") }
            val schedules = effectiveSchedules(zone)
            for (i in 0 until schedules.length()) {
                val s = schedules.optJSONObject(i) ?: continue
                if (!s.optBoolean("is_active", true)) continue
                val start = parseTime(s.optString("start_time", "")) ?: continue
                val end = parseTime(s.optString("end_time", "")) ?: continue
                val scheduleId = s.optString("id", "schedule-$i")
                for (day in readDays(s)) {
                    val startAt = nextOccurrence(tz, day, start)
                    val endDay = if (start >= end) (day % 7) + 1 else day
                    var endAt = nextOccurrence(tz, endDay, end)
                    if (!endAt.isAfter(startAt)) endAt = endAt.plusWeeks(1)
                    tracked += scheduleAlarm(context, zoneId, scheduleId, day, true, startAt.toInstant().toEpochMilli())
                    tracked += scheduleAlarm(context, zoneId, scheduleId, day, false, endAt.toInstant().toEpochMilli())
                }
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ALARMS, tracked.joinToString(";")).apply()
    }

    fun reschedule(context: Context) = scheduleAll(context)

    fun scheduleNextAlarm(context: Context, zoneId: String, scheduleId: String, day: Int, startAlarm: Boolean) {
        val zone = ZoneStorageManager.getZones(context).firstOrNull { it.optString("id", "") == zoneId } ?: return
        if (!zone.optBoolean("is_active", true) || !zone.optBoolean("schedule_enabled", false)) return
        val tz = try { ZoneId.of(zone.optString("timezone", "Asia/Kolkata")) } catch (_: Exception) { ZoneId.of("Asia/Kolkata") }
        val schedules = effectiveSchedules(zone)
        var selected: JSONObject? = null
        for (i in 0 until schedules.length()) {
            val s = schedules.optJSONObject(i) ?: continue
            if (s.optString("id", "schedule-$i") == scheduleId) { selected = s; break }
        }
        val s = selected ?: return
        if (!s.optBoolean("is_active", true) || day !in readDays(s)) return
        val start = parseTime(s.optString("start_time", "")) ?: return
        val end = parseTime(s.optString("end_time", "")) ?: return
        val targetDay = if (startAlarm) day else if (start >= end) (day % 7) + 1 else day
        val targetTime = if (startAlarm) start else end
        val next = nextOccurrence(tz, targetDay, targetTime)
        scheduleAlarm(context, zoneId, scheduleId, day, startAlarm, next.toInstant().toEpochMilli())
    }

    private fun effectiveSchedules(zone: JSONObject): JSONArray {
        val schedules = zone.optJSONArray("schedules")
        if (schedules != null && schedules.length() > 0) return schedules
        val result = JSONArray()
        val start = zone.optString("schedule_start", "")
        val end = zone.optString("schedule_end", "")
        if (start.isNotBlank() && end.isNotBlank()) {
            result.put(JSONObject().put("id", "legacy").put("name", "Schedule").put("start_time", start).put("end_time", end).put("is_active", true).put("days", JSONArray(listOf(1,2,3,4,5,6,7))))
        }
        return result
    }

    private fun readDays(s: JSONObject): Set<Int> {
        val a = s.optJSONArray("days") ?: return (1..7).toSet()
        val result = mutableSetOf<Int>()
        for (i in 0 until a.length()) a.optInt(i).takeIf { it in 1..7 }?.let { result.add(it) }
        return if (result.isEmpty()) (1..7).toSet() else result
    }

    private fun parseTime(value: String): LocalTime? = try { LocalTime.parse(value) } catch (_: Exception) { null }

    private fun nextOccurrence(zoneId: ZoneId, day: Int, time: LocalTime): ZonedDateTime {
        val now = ZonedDateTime.now(zoneId)
        val today = LocalDate.now(zoneId)
        val delta = (day - today.dayOfWeek.value + 7) % 7
        var target = ZonedDateTime.of(today.plusDays(delta.toLong()), time, zoneId)
        if (!target.isAfter(now)) target = target.plusWeeks(1)
        return target
    }

    private fun scheduleAlarm(context: Context, zoneId: String, scheduleId: String, day: Int, start: Boolean, millis: Long): String {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val action = if (start) ACTION_START else ACTION_END
        val requestCode = "$zoneId|$scheduleId|$day|${if (start) "S" else "E"}".hashCode()
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            this.action = action
            putExtra("zone_id", zoneId)
            putExtra("schedule_id", scheduleId)
            putExtra("schedule_day", day)
            putExtra("schedule_start", start)
        }
        val pi = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (_: Exception) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi) }
            catch (_: Exception) { try { am.set(AlarmManager.RTC_WAKEUP, millis, pi) } catch (_: Exception) {} }
        }
        return "$requestCode,$action"
    }

    private fun cancelTracked(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ALARMS, "") ?: ""
        if (raw.isNotBlank()) {
            for (entry in raw.split(";")) {
                val parts = entry.split(",", limit = 2)
                if (parts.size != 2) continue
                val code = parts[0].toIntOrNull() ?: continue
                val intent = Intent(context, ScheduleReceiver::class.java).apply { action = parts[1] }
                val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                if (pi != null) {
                    try { am.cancel(pi) } catch (_: Exception) {}
                    try { pi.cancel() } catch (_: Exception) {}
                }
            }
        }
        val oldRaw = prefs.getString("request_codes", "") ?: ""
        if (oldRaw.isNotBlank()) for (value in oldRaw.split(",")) {
            val code = value.toIntOrNull() ?: continue
            cancelOldAlarm(context, am, code, ACTION_START)
            cancelOldAlarm(context, am, code, ACTION_END)
        }
        prefs.edit().remove(KEY_ALARMS).remove("request_codes").apply()
    }

    private fun cancelOldAlarm(context: Context, am: AlarmManager, code: Int, action: String) {
        val intent = Intent(context, ScheduleReceiver::class.java).apply { this.action = action }
        val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) {
            try { am.cancel(pi) } catch (_: Exception) {}
            try { pi.cancel() } catch (_: Exception) {}
        }
    }
}
