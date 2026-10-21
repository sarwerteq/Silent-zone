package com.silentzone.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ZoneScheduleManager {
    const val ACTION_START = "com.silentzone.app.SCHEDULE_START"
    const val ACTION_END = "com.silentzone.app.SCHEDULE_END"
    private const val PREFS = "silent_zone_schedule_alarms"
    private const val KEY_CODES = "request_codes"

    fun isScheduleActive(zone: JSONObject, nowOverride: ZonedDateTime? = null): Boolean {
        if (!zone.optBoolean("schedule_enabled", false)) return true
        return try {
            val zoneId = ZoneId.of(zone.optString("timezone", "Asia/Kolkata"))
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
                else scheduleActive(JSONObject().put("start_time", start).put("end_time", end)
                    .put("days", JSONArray(listOf(1,2,3,4,5,6,7))), now)
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
        return if (start < end) {
            today in days && !time.isBefore(start) && time.isBefore(end)
        } else if (start > end) {
            (today in days && !time.isBefore(start)) || (yesterday in days && time.isBefore(end))
        } else {
            today in days // equal start/end = selected-day full day
        }
    }

    fun scheduleAll(context: Context) {
        cancelTracked(context)
        val codes = mutableSetOf<Int>()
        for (zone in ZoneStorageManager.getZones(context)) {
            if (!zone.optBoolean("is_active", true) || !zone.optBoolean("schedule_enabled", false)) continue
            val zoneIdText = zone.optString("id", "")
            if (zoneIdText.isBlank()) continue
            val tz = try { ZoneId.of(zone.optString("timezone", "Asia/Kolkata")) } catch (_: Exception) { ZoneId.of("Asia/Kolkata") }
            val schedules = effectiveSchedules(zone)
            for (i in 0 until schedules.length()) {
                val s = schedules.optJSONObject(i) ?: continue
                if (!s.optBoolean("is_active", true)) continue
                val start = parseTime(s.optString("start_time", "")) ?: continue
                val end = parseTime(s.optString("end_time", "")) ?: continue
                val sid = s.optString("id", "schedule-$i")
                for (day in readDays(s)) {
                    val startAt = nextOccurrence(tz, day, start)
                    val endDay = if (start >= end) (day % 7) + 1 else day
                    var endAt = nextOccurrence(tz, endDay, end)
                    if (!endAt.isAfter(startAt)) endAt = endAt.plusWeeks(1)
                    codes += scheduleAlarm(context, zoneIdText, sid, day, true, startAt.toInstant().toEpochMilli())
                    codes += scheduleAlarm(context, zoneIdText, sid, day, false, endAt.toInstant().toEpochMilli())
                }
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CODES, codes.joinToString(",")).apply()
    }

    fun reschedule(context: Context) = scheduleAll(context)

    private fun effectiveSchedules(zone: JSONObject): JSONArray {
        val arr = zone.optJSONArray("schedules")
        if (arr != null && arr.length() > 0) return arr
        val out = JSONArray()
        val start = zone.optString("schedule_start", "")
        val end = zone.optString("schedule_end", "")
        if (start.isNotBlank() && end.isNotBlank()) out.put(JSONObject()
            .put("id", "legacy").put("name", "Schedule").put("start_time", start)
            .put("end_time", end).put("is_active", true)
            .put("days", JSONArray(listOf(1,2,3,4,5,6,7))))
        return out
    }

    private fun readDays(s: JSONObject): Set<Int> {
        val arr = s.optJSONArray("days") ?: return (1..7).toSet()
        val set = mutableSetOf<Int>()
        for (i in 0 until arr.length()) arr.optInt(i).takeIf { it in 1..7 }?.let(set::add)
        return if (set.isEmpty()) (1..7).toSet() else set
    }

    private fun parseTime(value: String): LocalTime? = try { LocalTime.parse(value) } catch (_: Exception) { null }

    private fun nextOccurrence(zoneId: ZoneId, isoDay: Int, time: LocalTime): ZonedDateTime {
        val now = ZonedDateTime.now(zoneId)
        var date = LocalDate.now(zoneId)
        var delta = (isoDay - date.dayOfWeek.value + 7) % 7
        var target = ZonedDateTime.of(date.plusDays(delta.toLong()), time, zoneId)
        if (!target.isAfter(now)) target = target.plusWeeks(1)
        return target
    }

    private fun scheduleAlarm(context: Context, zoneId: String, scheduleId: String, day: Int, start: Boolean, millis: Long): Int {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val code = "$zoneId|$scheduleId|$day|${if (start) "S" else "E"}".hashCode()
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = if (start) ACTION_START else ACTION_END
            putExtra("zone_id", zoneId)
        }
        val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms())
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (_: Exception) { try { am.set(AlarmManager.RTC_WAKEUP, millis, pi) } catch (_: Exception) {} }
        return code
    }

    private fun cancelTracked(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CODES, "") ?: ""
        for (code in raw.split(',')) {
            val n = code.toIntOrNull() ?: continue
            val pi = PendingIntent.getBroadcast(context, n, Intent(context, ScheduleReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pi != null) { am.cancel(pi); pi.cancel() }
        }
        prefs.edit().remove(KEY_CODES).apply()
    }
}
