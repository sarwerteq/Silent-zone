
package com.silentzone.app

import android.Manifest
import android.app.BroadcastReceiver
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import org.json.JSONObject

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val transition = event.geofenceTransition

        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER &&
            transition != Geofence.GEOFENCE_TRANSITION_EXIT
        ) return

        val prefs = context.getSharedPreferences(
            "silent_zone_geofence",
            Context.MODE_PRIVATE
        )

        val actions = try {
            JSONObject(prefs.getString("zone_actions", "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }

        val active = prefs.getStringSet(
            "active_zone_ids",
            emptySet()
        )?.toMutableSet() ?: mutableSetOf()

        val ids = event.triggeringGeofences
            ?.map { it.requestId }
            ?: emptyList()

        for (id in ids) {
            if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                active.add(id)
            } else {
                active.remove(id)
            }
        }

        prefs.edit()
            .putStringSet("active_zone_ids", active)
            .apply()

        // Choose a deterministic action if zones overlap.
        val priorities = mapOf(
            "DND" to 3,
            "SILENT" to 2,
            "VIBRATE" to 1
        )

        val selectedAction = active
            .mapNotNull { id -> actions.optString(id, "")
                .uppercase()
                .takeIf { it in priorities }
            }
            .maxByOrNull { priorities[it] ?: 0 }

        val message = if (
            transition == Geofence.GEOFENCE_TRANSITION_ENTER
        ) {
            "Entered a Silent Zone"
        } else {
            "Exited a Silent Zone"
        }

        val modeMessage = if (active.isEmpty()) {
            SoundModeManager.restore(context)
        } else if (selectedAction != null) {
            SoundModeManager.apply(context, selectedAction)
        } else {
            "Zone event received"
        }

        showNotification(context, "$message • $modeMessage")
    }

    private fun showNotification(
        context: Context,
        message: String
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        val channelId = "silent_zone_events"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Silent Zone Events",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val notification = NotificationCompat.Builder(
            context,
            channelId
        )
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setContentTitle("Silent Zone")
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(message)
            )
            .setAutoCancel(true)
            .build()

        manager.notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }
}
