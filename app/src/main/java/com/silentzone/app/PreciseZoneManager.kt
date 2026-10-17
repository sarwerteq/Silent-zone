package com.silentzone.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONObject

object PreciseZoneManager {

    private const val EXIT_BUFFER_METERS =
        10.0

    private const val PRECISE_PREFS =
        "silent_zone_precise"

    private const val KEY_INSIDE =
        "inside_actual_zone"

    private const val KEY_LAST_ZONE_ID =
        "last_actual_zone_id"

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

    /*
     * First try LAST LOCATION.
     *
     * Ye normally bahut fast milta hai.
     */
    try {

        client.lastLocation
            .addOnSuccessListener { lastLocation ->

                if (lastLocation != null) {

                    /*
                     * Immediately check zone
                     * using available location.
                     */
                    evaluate(
                        context,
                        lastLocation,
                        callback
                    )

                    /*
                     * Saath mein fresh GPS location
                     * bhi request karenge.
                     */
                    requestFreshLocation(
                        context,
                        callback
                    )

                } else {

                    /*
                     * Cached location nahi mili.
                     * Fresh location request karo.
                     */
                    requestFreshLocation(
                        context,
                        callback
                    )
                }
            }
            .addOnFailureListener {

                requestFreshLocation(
                    context,
                    callback
                )
            }

    } catch (_: SecurityException) {

        callback?.invoke(
            "Location permission error"
        )
    }
    }

    private fun requestFreshLocation(
    context: Context,
    callback: ((String) -> Unit)?
) {

    if (
        context.checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val client =
        LocationServices
            .getFusedLocationProviderClient(
                context
            )

    val cancellationTokenSource =
        CancellationTokenSource()

    /*
     * Maximum 15 seconds wait.
     *
     * Iske baad GPS request cancel.
     */
    android.os.Handler(
        android.os.Looper.getMainLooper()
    ).postDelayed({

        cancellationTokenSource.cancel()

    }, 15000)

    try {

        client.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        )
            .addOnSuccessListener { location ->

                if (location != null) {

                    /*
                     * Fresh GPS result mil gaya.
                     * Final precise zone check.
                     */
                    evaluate(
                        context,
                        location,
                        callback
                    )
                }
            }
            .addOnFailureListener {

                /*
                 * Last location already evaluate
                 * ho chuki ho sakti hai.
                 *
                 * Isliye yahan sound mode ko
                 * change nahi karenge.
                 */
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

        var insideZone: JSONObject? =
            null

        var insideDistance =
            Double.MAX_VALUE

        var nearestDistance =
            Double.MAX_VALUE

        var nearestExitRadius =
            0.0

        /*
         * Find current actual zone.
         */
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
             * User is actually inside this
             * zone's admin radius.
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

        val prefs =
            context.getSharedPreferences(
                PRECISE_PREFS,
                Context.MODE_PRIVATE
            )

        val wasInside =
            prefs.getBoolean(
                KEY_INSIDE,
                false
            )

        val previousZoneId =
            prefs.getString(
                KEY_LAST_ZONE_ID,
                null
            )

        /*
         * ---------------------------------
         * CURRENTLY INSIDE A ZONE
         * ---------------------------------
         */

        if (insideZone != null) {

            val currentZone =
                insideZone

            val zoneId =
                currentZone.optString(
                    "id",
                    ""
                )

            val name =
                currentZone.optString(
                    "name",
                    "Silent Zone"
                )

            val action =
                currentZone.optString(
                    "sound_action",
                    "SILENT"
                ).uppercase()

            /*
             * We moved from Zone A
             * directly into Zone B.
             *
             * Pause belonging to Zone A
             * must not affect Zone B.
             */
            if (
                previousZoneId != null &&
                previousZoneId != zoneId
            ) {

                SilentZonePauseManager
                    .resetOnExit(
                        context
                    )
            }

            /*
             * Save current actual zone.
             */
            prefs.edit()
                .putBoolean(
                    KEY_INSIDE,
                    true
                )
                .putString(
                    KEY_LAST_ZONE_ID,
                    zoneId
                )
                .apply()

            /*
             * Check pause ONLY for
             * this particular zone.
             */
            if (
                SilentZonePauseManager
                    .isPaused(
                        context,
                        zoneId
                    )
            ) {

                callback?.invoke(
                    "$name • ${insideDistance.toInt()}m • Silent Zone temporarily paused"
                )

                return
            }

            /*
             * Not paused.
             * Apply admin-selected mode.
             */
            val soundResult =
                SoundModeManager.apply(
                    context,
                    action
                )

            callback?.invoke(
                "$name • ${insideDistance.toInt()}m • $soundResult"
            )

            return
        }

        /*
         * ---------------------------------
         * CURRENTLY OUTSIDE ACTUAL ZONE
         * ---------------------------------
         */

        if (
            wasInside &&
            nearestDistance >
            nearestExitRadius
        ) {

            /*
             * First clear pause.
             *
             * Therefore next zone can
             * automatically become Silent.
             */
            SilentZonePauseManager
                .resetOnExit(
                    context
                )

            val soundResult =
                SoundModeManager.restore(
                    context
                )

            prefs.edit()
                .putBoolean(
                    KEY_INSIDE,
                    false
                )
                .remove(
                    KEY_LAST_ZONE_ID
                )
                .apply()

            callback?.invoke(
                "Outside actual zone • $soundResult"
            )

            return
        }

        /*
         * Not inside any zone.
         *
         * If old paused zone is no longer
         * the current zone, clear its pause.
         */
        if (
            previousZoneId != null &&
            !wasInside
        ) {

            SilentZonePauseManager
                .resetOnExit(
                    context
                )

            prefs.edit()
                .remove(
                    KEY_LAST_ZONE_ID
                )
                .apply()
        }

        callback?.invoke(
            "Outside actual Silent radius"
        )
    }
}
