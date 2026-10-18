package com.silentzone.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduleReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        /*
         * Start OR End:
         *
         * Don't blindly Silent/Normal.
         *
         * Check current location +
         * current schedule again.
         */
        PreciseZoneManager
            .checkCurrentLocation(
                context
            )

        /*
         * Schedule next day's alarms.
         */
        ZoneScheduleManager
            .reschedule(
                context
            )
    }
    }
