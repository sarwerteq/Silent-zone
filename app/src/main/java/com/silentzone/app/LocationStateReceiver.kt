
package com.silentzone.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager

class LocationStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LocationManager.MODE_CHANGED_ACTION &&
            intent.action != LocationManager.PROVIDERS_CHANGED_ACTION) return

        val c = context.applicationContext

        if (!LocationProtectionManager.isLocationEnabled(c)) {
            LocationProtectionManager.markUnverified(c)
            LocationProtectionManager.showWarning(c)
            BootReceiver.resetRetries(c)
            return
        }

        LocationProtectionManager.markUnverified(c)
        LocationProtectionManager.cancelWarning(c)
        BootReceiver.resetRetries(c)
        ZoneGeofenceManager.registerSavedZones(c)

        val pending = goAsync()
        PreciseZoneManager.checkCurrentLocation(c) {
            if (!LocationProtectionManager.isVerified(c))
                BootReceiver.scheduleRetry(c)
            pending.finish()
        }
    }
}
