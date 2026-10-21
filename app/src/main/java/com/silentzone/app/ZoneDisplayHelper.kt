package com.silentzone.app

import org.json.JSONObject

object ZoneDisplayHelper {
    private val names = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
    fun scheduleText(zone: JSONObject): String {
        if (!zone.optBoolean("schedule_enabled", false)) return "Always active while inside zone"
        val arr = zone.optJSONArray("schedules")
        if (arr == null || arr.length() == 0) {
            val s = zone.optString("schedule_start", "").take(5)
            val e = zone.optString("schedule_end", "").take(5)
            return if (s.isNotBlank() && e.isNotBlank()) "Every day • $s–$e" else "No active schedule"
        }
        val lines = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val x = arr.optJSONObject(i) ?: continue
            if (!x.optBoolean("is_active", true)) continue
            val days = mutableListOf<Int>()
            val da = x.optJSONArray("days")
            if (da == null) days.addAll(1..7) else for (j in 0 until da.length()) da.optInt(j).takeIf { it in 1..7 }?.let(days::add)
            val dayText = if (days.distinct().size == 7) "Every day" else days.distinct().sorted().joinToString(", ") { names[it] ?: it.toString() }
            lines += "${x.optString("name", "Schedule")} • $dayText • ${x.optString("start_time", "").take(5)}–${x.optString("end_time", "").take(5)}"
        }
        return if (lines.isEmpty()) "No active schedule" else lines.joinToString("\n")
    }
}
