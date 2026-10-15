package com.silentzone.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ZoneStorageManager {

    private const val PREFS = "silent_zone_geofence"
    private const val KEY_ZONES = "saved_zones"

    fun saveZones(
        context: Context,
        zones: List<JSONObject>
    ) {

        val array = JSONArray()

        zones.forEach {
            array.put(it)
        }

        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_ZONES,
                array.toString()
            )
            .apply()
    }

    fun getZones(
        context: Context
    ): List<JSONObject> {

        return try {

            val text =
                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                    .getString(
                        KEY_ZONES,
                        "[]"
                    ) ?: "[]"

            val array =
                JSONArray(text)

            val result =
                mutableListOf<JSONObject>()

            for (i in 0 until array.length()) {

                result.add(
                    array.getJSONObject(i)
                )
            }

            result

        } catch (_: Exception) {

            emptyList()
        }
    }
}
