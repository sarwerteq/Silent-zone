package com.silentzone.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        if (
            intent.action ==
            Intent.ACTION_BOOT_COMPLETED
        ) {

            /*
             * Re-register locally saved zones.
             * Internet required nahi.
             */
            ZoneGeofenceManager
                .registerSavedZones(
                    context
                )

            /*
             * Re-register locally saved
             * schedule alarms.
             */
            ZoneScheduleManager
                .scheduleAll(
                    context
                )

            /*
             * Check current zone.
             */
            PreciseZoneManager
                .checkCurrentLocation(
                    context
                )
        }
    }
    }
