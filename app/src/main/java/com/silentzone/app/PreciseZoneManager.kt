
package com.silentzone.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object PreciseZoneManager {

    private const val PREFS = "silent_zone_precise"
    private const val KEY_INSIDE = "inside"
    private const val KEY_ZONE = "last_zone_id"

    private fun isUsable(location: Location?): Boolean {
        if (location == null) return false

        val age = System.currentTimeMillis() - location.time

        return location.time > 0 &&
                age in 0..120000L &&
                (!location.hasAccuracy() || location.accuracy <= 75f)
    }

    fun checkCurrentLocation(
        context: Context,
        fallback: Location? = null,
        callback: ((String) -> Unit)? = null
    ) {
        if (!LocationProtectionManager.isLocationEnabled(context)) {
            LocationProtectionManager.markUnverified(context)
            LocationProtectionManager.showWarning(context)
            callback?.invoke("Location is turned off")
            return
        }

        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            LocationProtectionManager.markUnverified(context)
            callback?.invoke("Fine location permission required")
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(context)
        val token = CancellationTokenSource()

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setMaxUpdateAgeMillis(10000)
            .setDurationMillis(15000)
            .build()

        try {
            client.getCurrentLocation(request, token.token)
                .addOnSuccessListener { fresh ->

                    val selected = when {
                        isUsable(fresh) -> fresh
                        isUsable(fallback) -> fallback
                        else -> fresh
                    }

                    Log.d(
                        "SilentZone",
                        "Fresh=${fresh?.accuracy}, Fallback=${fallback?.accuracy}, Selected=${selected?.accuracy}"
                    )

                    evaluate(context, selected, callback)
                }
                .addOnFailureListener { error ->
                    Log.e("SilentZone", "GPS request failed", error)

                    val selected = if (isUsable(fallback)) fallback else null
                    evaluate(context, selected, callback)
                }

        } catch (e: SecurityException) {
            LocationProtectionManager.markUnverified(context)
            Log.e("SilentZone", "Location permission error", e)
            callback?.invoke("Location permission required")
        }
    }

    fun evaluate(
        context: Context,
        location: Location?,
        callback: ((String) -> Unit)? = null
    ) {
        if (!LocationProtectionManager.isLocationEnabled(context)) {
            LocationProtectionManager.markUnverified(context)
            LocationProtectionManager.showWarning(context)
            callback?.invoke("Location is turned off")
            return
        }

        if (!isUsable(location)) {
            LocationProtectionManager.markUnverified(context)
            callback?.invoke("Waiting for accurate GPS location")
            Log.w("SilentZone", "No recent accurate location; keeping previous mode")
            return
        }

        LocationProtectionManager.markVerified(context)

        val zones = ZoneStorageManager.getZones(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        var best: org.json.JSONObject? = null
        var bestDistance = Double.MAX_VALUE

        for (zone in zones) {
            if (!zone.optBoolean("is_active", true)) continue

            val lat = zone.optDouble("latitude", Double.NaN)
            val lon = zone.optDouble("longitude", Double.NaN)
            val radius = zone.optDouble(
                "radius_m",
                zone.optDouble("radius_meters", 0.0)
            )

            if (!lat.isFinite() || !lon.isFinite() || radius <= 0) continue

            val result = FloatArray(1)

            Location.distanceBetween(
                location!!.latitude,
                location.longitude,
                lat,
                lon,
                result
            )

            val distance = result[0].toDouble()

            if (distance <= radius && distance < bestDistance) {
                best = zone
                bestDistance = distance
            }
        }

        if (best != null) {
            val id = best.optString("id", "")

            prefs.edit()
                .putBoolean(KEY_INSIDE, true)
                .putString(KEY_ZONE, id)
                .apply()

            Log.d("SilentZone", "INSIDE zone: $id")

            if (!ZoneScheduleManager.isScheduleActive(best)) {
                SilentZonePauseManager.resume(context)
                callback?.invoke(
                    SoundModeManager.restore(context) +
                            " • Inside zone, schedule inactive"
                )
                return
            }

            if (SilentZonePauseManager.isPaused(context, id)) {
                callback?.invoke("Silent Zone paused")
                return
            }

            callback?.invoke(
                SoundModeManager.apply(
                    context,
                    best.optString("sound_action", "SILENT")
                )
            )
            return
        }

        prefs.edit()
            .putBoolean(KEY_INSIDE, false)
            .remove(KEY_ZONE)
            .apply()

        Log.d("SilentZone", "OUTSIDE all zones")

        SilentZonePauseManager.resetOnExit(context)
        callback?.invoke(SoundModeManager.restore(context))
    }
}
