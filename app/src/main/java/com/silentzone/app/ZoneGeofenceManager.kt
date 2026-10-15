package com.silentzone.app

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

object ZoneGeofenceManager {

    private const val OUTER_RADIUS = 100f

    fun registerSavedZones(
        context: Context
    ) {

        if (
            context.checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (
            Build.VERSION.SDK_INT >= 29 &&
            context.checkSelfPermission(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val zones =
            ZoneStorageManager.getZones(
                context
            )

        if (zones.isEmpty()) {
            return
        }

        val geofences =
            mutableListOf<Geofence>()

        /*
         * 2 geofences per zone.
         * Android maximum = 100.
         * Therefore maximum 50 zones here.
         */
        for (zone in zones.take(50)) {

            val id =
                zone.optString(
                    "id",
                    ""
                )

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

            val actualRadius =
                zone.optDouble(
                    "radius_m",
                    zone.optDouble(
                        "radius_meters",
                        30.0
                    )
                ).toFloat()

            if (
                id.isBlank() ||
                !latitude.isFinite() ||
                !longitude.isFinite()
            ) {
                continue
            }

            /*
             * OUTER geofence
             */
            val outerRadius =
                maxOf(
                    OUTER_RADIUS,
                    actualRadius
                )

            geofences.add(
                Geofence.Builder()
                    .setRequestId(
                        "OUTER:$id"
                    )
                    .setCircularRegion(
                        latitude,
                        longitude,
                        outerRadius
                    )
                    .setExpirationDuration(
                        Geofence.NEVER_EXPIRE
                    )
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or
                            Geofence.GEOFENCE_TRANSITION_EXIT
                    )
                    .setNotificationResponsiveness(
                        30000
                    )
                    .build()
            )

            /*
             * INNER geofence
             *
             * This is the real admin radius:
             * 20m / 30m / 40m / etc.
             */
            geofences.add(
                Geofence.Builder()
                    .setRequestId(
                        "INNER:$id"
                    )
                    .setCircularRegion(
                        latitude,
                        longitude,
                        actualRadius
                    )
                    .setExpirationDuration(
                        Geofence.NEVER_EXPIRE
                    )
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or
                            Geofence.GEOFENCE_TRANSITION_EXIT
                    )
                    .setNotificationResponsiveness(
                        10000
                    )
                    .build()
            )
        }

        if (geofences.isEmpty()) {
            return
        }

        val intent =
            Intent(
                context,
                GeofenceBroadcastReceiver::class.java
            )

        var flags =
            PendingIntent.FLAG_UPDATE_CURRENT

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            flags =
                flags or
                    PendingIntent.FLAG_MUTABLE
        }

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                intent,
                flags
            )

        val request =
            GeofencingRequest.Builder()
                .setInitialTrigger(
                    GeofencingRequest.INITIAL_TRIGGER_ENTER
                )
                .addGeofences(
                    geofences
                )
                .build()

        val client =
            LocationServices
                .getGeofencingClient(
                    context
                )

        try {

            client.removeGeofences(
                pendingIntent
            )
                .addOnCompleteListener {

                    try {

                        client.addGeofences(
                            request,
                            pendingIntent
                        )

                    } catch (_: SecurityException) {
                    }
                }

        } catch (_: SecurityException) {
        }
    }
}
