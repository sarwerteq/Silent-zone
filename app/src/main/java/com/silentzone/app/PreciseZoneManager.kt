package com.silentzone.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object PreciseZoneManager {

    private const val EXIT_BUFFER_METERS =
        10.0

    fun checkCurrentLocation(
        context: Context,
        callback: ((String) -> Unit)? = null
    ) {

        if (
            context.checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            callback?.invoke(
                "Location permission missing"
            )

            return
        }

        val zones =
            ZoneStorageManager.getZones(
                context
            )

        if (zones.isEmpty()) {

            callback?.invoke(
                "No saved zones"
            )

            return
        }

        val client =
            LocationServices
                .getFusedLocationProviderClient(
                    context
                )

        try {

            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            )
                .addOnSuccessListener { location ->

                    if (location == null) {

                        callback?.invoke(
                            "Location unavailable"
                        )

                        return@addOnSuccessListener
                    }

                    evaluate(
                        context,
                        location,
                        callback
                    )
                }
                .addOnFailureListener {

                    callback?.invoke(
                        "Location check failed"
                    )
                }

        } catch (_: SecurityException) {

            callback?.invoke(
                "Location permission error"
            )
        }
    }

    private fun evaluate(
        context: Context,
        location: Location,
        callback: ((String) -> Unit)?
    ) {

        val zones =
            ZoneStorageManager.getZones(
                context
            )

        var insideZone:
            org.json.JSONObject? = null

        var insideDistance =
            Double.MAX_VALUE

        var nearestDistance =
            Double.MAX_VALUE

        var nearestExitRadius =
            0.0

        for (zone in zones) {

            if (
                !zone.optBoolean(
                    "is_active",
                    true
                )
            ) {
                continue
            }

            val latitude =
                zone.optDouble(
                    "latitude",
                    Double.NaN
                )

            val longitude =
                zone.optDouble(
                    "longitude",
                    Double.NaN
                )

            if (
                !latitude.isFinite() ||
                !longitude.isFinite()
            ) {
                continue
            }

            val radius =
                zone.optDouble(
                    "radius_m",
                    zone.optDouble(
                        "radius_meters",
                        30.0
                    )
                )

            val result =
                FloatArray(1)

            Location.distanceBetween(
                location.latitude,
                location.longitude,
                latitude,
                longitude,
                result
            )

            val distance =
                result[0].toDouble()

            if (
                distance <
                nearestDistance
            ) {

                nearestDistance =
                    distance

                nearestExitRadius =
                    radius +
                        EXIT_BUFFER_METERS
            }

            /*
             * Actual Silent radius
             */
            if (
                distance <= radius &&
                distance < insideDistance
            ) {

                insideDistance =
                    distance

                insideZone =
                    zone
            }
        }

        /*
         * User manually paused Silent Zone
         */
        if (
            SilentZonePauseManager
                .isPaused(context)
        ) {

            callback?.invoke(
                "Silent Zone paused by user"
            )

            return
        }

        /*
         * Actually inside admin radius
         */
        if (insideZone != null) {

            val action =
                insideZone.optString(
                    "sound_action",
                    "SILENT"
                ).uppercase()

            val name =
                insideZone.optString(
                    "name",
                    "Silent Zone"
                )

            val result =
                SoundModeManager.apply(
                    context,
                    action
                )

            context.getSharedPreferences(
                "silent_zone_precise",
                Context.MODE_PRIVATE
            )
                .edit()
                .putBoolean(
                    "inside_actual_zone",
                    true
                )
                .apply()

            callback?.invoke(
                "$name • ${insideDistance.toInt()}m • $result"
            )

            return
        }

        val prefs =
            context.getSharedPreferences(
                "silent_zone_precise",
                Context.MODE_PRIVATE
            )

        val wasInside =
            prefs.getBoolean(
                "inside_actual_zone",
                false
            )

        /*
         * Only restore after crossing radius + 10m.
         */
        if (
            wasInside &&
            nearestDistance >
            nearestExitRadius
        ) {

            val result =
                SoundModeManager.restore(
                    context
                )

            SilentZonePauseManager
                .resetOnExit(
                    context
                )

            prefs.edit()
                .putBoolean(
                    "inside_actual_zone",
                    false
                )
                .apply()

            callback?.invoke(
                "Outside actual zone • $result"
            )

        } else {

            callback?.invoke(
                "Outside actual Silent radius"
            )
        }
    }
}
