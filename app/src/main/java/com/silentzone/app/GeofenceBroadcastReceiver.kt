
package com.silentzone.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()

        try {
            val event = GeofencingEvent.fromIntent(intent)

            if (event == null) {
                Log.e("SilentZone", "Geofence event is NULL")
                pending.finish()
                return
            }

            if (event.hasError()) {
                Log.e("SilentZone", "Geofence error: ${event.errorCode}")
                pending.finish()
                return
            }

            val transition = when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
                Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
                else -> "UNKNOWN"
            }

            Log.d("SilentZone", "Geofence transition: $transition")

            if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER &&
                event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT) {
                pending.finish()
                return
            }

            Log.d(
                "SilentZone",
                "Triggered IDs: ${event.triggeringGeofences?.joinToString { it.requestId }}"
            )

            PreciseZoneManager.checkCurrentLocation(
                context.applicationContext,
                event.triggeringLocation
            ) {
                Log.d("SilentZone", "Geofence result: $it")
                pending.finish()
            }

        } catch (e: Exception) {
            Log.e("SilentZone", "Receiver failed", e)
            pending.finish()
        }
    }
}
