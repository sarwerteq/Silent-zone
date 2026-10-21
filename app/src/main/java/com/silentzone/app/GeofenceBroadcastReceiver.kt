package com.silentzone.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null || event.hasError()) { pending.finish(); return }
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER,
            Geofence.GEOFENCE_TRANSITION_EXIT -> PreciseZoneManager.checkCurrentLocation(context, event.triggeringLocation) { pending.finish() }
            else -> pending.finish()
        }
    }
}
