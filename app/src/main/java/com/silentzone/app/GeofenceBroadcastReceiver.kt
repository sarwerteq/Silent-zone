package com.silentzone.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofenceStatusCodes

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        val event =
            GeofencingEvent.fromIntent(intent)

        if (event == null) {

            showNotification(
                context,
                "GEOFENCE ERROR: Event is null"
            )

            return
        }

        if (event.hasError()) {

            val errorMessage =
                try {

                    GeofenceStatusCodes
                        .getStatusCodeString(
                            event.errorCode
                        )

                } catch (_: Exception) {

                    "Unknown geofence error: ${event.errorCode}"
                }

            showNotification(
                context,
                "GEOFENCE ERROR: $errorMessage"
            )

            return
        }

        val transition =
            event.geofenceTransition

        if (
            transition !=
                Geofence.GEOFENCE_TRANSITION_ENTER &&
            transition !=
                Geofence.GEOFENCE_TRANSITION_EXIT
        ) {

            showNotification(
                context,
                "GEOFENCE EVENT: Invalid transition $transition"
            )

            return
        }

        val transitionName =
            if (
                transition ==
                Geofence.GEOFENCE_TRANSITION_ENTER
            ) {
                "ENTER"
            } else {
                "EXIT"
            }

        val ids =
            event.triggeringGeofences
                ?.map {
                    it.requestId
                }
                ?: emptyList()

        if (ids.isEmpty()) {

            showNotification(
                context,
                "GEOFENCE $transitionName received, but no zone ID found."
            )

            return
        }

        /*
         * Examples:
         *
         * OUTER:123
         * INNER:123
         */

        val hasInner =
            ids.any {
                it.startsWith(
                    "INNER:"
                )
            }

        val hasOuter =
            ids.any {
                it.startsWith(
                    "OUTER:"
                )
            }

        /*
         * IMPORTANT:
         *
         * Geofence khud Silent/Normal decide
         * nahi karega.
         *
         * Fresh high-accuracy location lekar
         * PreciseZoneManager actual admin
         * radius check karega.
         */

        val reason =
            when {

                hasInner &&
                    transition ==
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {

                    "INNER ENTER - checking actual radius"
                }

                hasInner &&
                    transition ==
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {

                    "INNER EXIT - checking actual radius"
                }

                hasOuter &&
                    transition ==
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {

                    "OUTER ENTER - checking actual radius"
                }

                hasOuter &&
                    transition ==
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {

                    "OUTER EXIT - checking actual radius"
                }

                else -> {

                    "Geofence event - checking actual radius"
                }
            }

        /*
         * Fresh GPS verification.
         */

        PreciseZoneManager.checkCurrentLocation(
            context
        ) { result ->

            /*
             * Testing notification abhi ON rakhenge.
             * Baad mein ise remove kar denge.
             */

            val zoneIds =
                ids.joinToString(
                    ", "
                )

            val message =
                "GEOFENCE $transitionName\n" +
                "Triggered: $zoneIds\n" +
                "$reason\n" +
                "Result: $result"

            showNotification(
                context,
                message
            )
        }
    }

    /*
     * Diagnostic notification.
     *
     * Abhi testing ke liye rakha hai.
     * Final app mein baad mein hata denge.
     */

    private fun showNotification(
        context: Context,
        message: String
    ) {

        if (
            Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val channelId =
            "silent_zone_events"

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Silent Zone Events",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val notification =
            NotificationCompat.Builder(
                context,
                channelId
            )
                .setSmallIcon(
                    android.R.drawable.ic_lock_silent_mode
                )
                .setContentTitle(
                    "Silent Zone"
                )
                .setContentText(
                    message.replace(
                        "\n",
                        " • "
                    )
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(
                            message
                        )
                )
                .setAutoCancel(
                    true
                )
                .build()

        manager.notify(
            System.currentTimeMillis()
                .toInt(),
            notification
        )
    }
}
