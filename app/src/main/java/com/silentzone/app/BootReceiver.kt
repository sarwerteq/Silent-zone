
package com.silentzone.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val c = context.applicationContext
        val pending = goAsync()

        LocationProtectionManager.invalidateAfterBoot(c)

        ZoneScheduleManager.scheduleAll(c)

        if (!LocationProtectionManager.canUseLocation(c)) {
            LocationProtectionManager.showWarning(c)
            pending.finish()
            return
        }

        ZoneGeofenceManager.registerSavedZones(c)

        val handler = Handler(Looper.getMainLooper())
        var attempts = 0

        fun checkLocation() {
            attempts++

            if (!LocationProtectionManager.canUseLocation(c)) {
                LocationProtectionManager.showWarning(c)
                pending.finish()
                return
            }

            PreciseZoneManager.checkCurrentLocation(c) { result ->
                Log.d("SilentZoneBoot", "Attempt $attempts: $result")

                if (LocationProtectionManager.isVerified(c)) {
                    pending.finish()
                } else if (attempts < 4) {
                    handler.postDelayed({ checkLocation() }, 30000)
                } else {
                    Log.w("SilentZoneBoot", "Location verification failed after retries")
                    pending.finish()
                }
            }
        }

        handler.postDelayed({ checkLocation() }, 15000)
    }
}
