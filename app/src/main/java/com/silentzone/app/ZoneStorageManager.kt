package com.silentzone.app
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
object ZoneStorageManager {
    private const val PREFS = "silent_zone_geofence"
    private const val KEY_ZONES = "saved_zones"
    fun saveZones(context: Context, zones: List<JSONObject>) {
        val a = JSONArray(); zones.forEach { a.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ZONES, a.toString()).apply()
    }
    fun getZones(context: Context): List<JSONObject> = try {
        val a = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ZONES, "[]") ?: "[]")
        List(a.length()) { a.getJSONObject(it) }
    } catch (_: Exception) { emptyList() }
}
