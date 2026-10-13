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
import org.json.JSONObject

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

        val prefs =
            context.getSharedPreferences(
                "silent_zone_geofence",
                Context.MODE_PRIVATE
            )

        val actions =
            try {
                JSONObject(
                    prefs.getString(
                        "zone_actions",
                        "{}"
                    ) ?: "{}"
                )
            } catch (_: Exception) {
                JSONObject()
            }

        val active =
            prefs.getStringSet(
                "active_zone_ids",
                emptySet()
            )?.toMutableSet()
                ?: mutableSetOf()

        val ids =
            event.triggeringGeofences
                ?.map { it.requestId }
                ?: emptyList()

        if (ids.isEmpty()) {

            showNotification(
                context,
                "GEOFENCE $transitionName received, but no zone ID found."
            )

            return
        }

        /*
         * Update active zones
         */

        for (id in ids) {

            if (
                transition ==
                    Geofence.GEOFENCE_TRANSITION_ENTER
            ) {
                active.add(id)
            } else {
                active.remove(id)
            }
        }

        prefs.edit()
            .putStringSet(
                "active_zone_ids",
                active
            )
            .apply()

        /*
         * Select highest-priority action
         */

        val priorities =
            mapOf(
                "DND" to 3,
                "SILENT" to 2,
                "VIBRATE" to 1
            )

        val selectedAction =
            active
                .mapNotNull { id ->

                    actions
                        .optString(
                            id,
                            ""
                        )
                        .uppercase()
                        .takeIf {
                            it in priorities
                        }
                }
                .maxByOrNull {
                    priorities[it] ?: 0
                }

        /*
         * Apply / restore sound mode
         */

        val modeMessage =
            if (active.isEmpty()) {

                SoundModeManager.restore(
                    context
                )

            } else if (selectedAction != null) {

                SoundModeManager.apply(
                    context,
                    selectedAction
                )

            } else {

                "Zone event received, but no valid sound action found"
            }

        /*
         * Diagnostic information
         */

        val zoneIds =
            ids.joinToString(", ")

        val activeZones =
            if (active.isEmpty()) {
                "none"
            } else {
                active.joinToString(", ")
            }

        val selected =
            selectedAction ?: "NONE"

        val message =
            "GEOFENCE $transitionName\n" +
            "Triggered zone: $zoneIds\n" +
            "Active zones: $activeZones\n" +
            "Selected action: $selected\n" +
            "Sound result: $modeMessage"

        showNotification(
            context,
            message
        )
    }

    private fun showNotification(
        context: Context,
        message: String
    ) {

        if (
            Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
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
                    message.replace("\n", " • ")
                )
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message)
                )
                .setAutoCancel(true)
                .build()

        manager.notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }
}
