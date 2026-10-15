package com.silentzone.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager

class LocationStateReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        if (
            intent.action !=
            LocationManager.MODE_CHANGED_ACTION
        ) {
            return
        }

        val manager =
            context.getSystemService(
                Context.LOCATION_SERVICE
            ) as LocationManager

        if (
            manager.isLocationEnabled
        ) {

            ZoneGeofenceManager
                .registerSavedZones(
                    context
                )

            PreciseZoneManager
                .checkCurrentLocation(
                    context
                )
        }
    }
    }
