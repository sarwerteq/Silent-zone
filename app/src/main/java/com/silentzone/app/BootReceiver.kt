
package com.silentzone.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val c = context.applicationContext
        val boot = intent.action == Intent.ACTION_BOOT_COMPLETED
        val retry = intent.action == ACTION_RETRY
        if (!boot && !retry) return

        val pending = goAsync()

        if (boot) {
            resetRetries(c)
            LocationProtectionManager.invalidateAfterBoot(c)
            ZoneScheduleManager.scheduleAll(c)
            ZoneGeofenceManager.registerSavedZones(c)
        }

        if (!LocationProtectionManager.isLocationEnabled(c)) {
            LocationProtectionManager.markUnverified(c)
            LocationProtectionManager.showWarning(c)
            pending.finish()
            return
        }

        if (!LocationProtectionManager.canUseLocation(c)) {
            LocationProtectionManager.markUnverified(c)
            pending.finish()
            return
        }

        PreciseZoneManager.checkCurrentLocation(c) { result ->
            Log.d("SilentZoneBoot", result)
            if (LocationProtectionManager.isVerified(c)) {
                resetRetries(c)
            } else {
                scheduleRetry(c)
            }
            pending.finish()
        }
    }

    companion object {
        private const val ACTION_RETRY = "com.silentzone.app.BOOT_LOCATION_RETRY"
        private const val PREFS = "silent_zone_boot_retry"
        private const val MAX_RETRIES = 5
        private const val REQUEST_CODE = 9201

        private fun pendingIntent(c: Context): PendingIntent {
            val i = Intent(c, BootReceiver::class.java).setAction(ACTION_RETRY)
            return PendingIntent.getBroadcast(
                c, REQUEST_CODE, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun resetRetries(c: Context) {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("count", 0).apply()
            try {
                (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                    .cancel(pendingIntent(c))
            } catch (_: Exception) {}
        }

        fun scheduleRetry(c: Context) {
            val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val count = p.getInt("count", 0)
            if (count >= MAX_RETRIES) return

            p.edit().putInt("count", count + 1).apply()

            try {
                val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 30000,
                    pendingIntent(c)
                )
            } catch (e: Exception) {
                Log.e("SilentZoneBoot", "Retry scheduling failed", e)
            }
        }
    }
}
