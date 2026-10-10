
package com.silentzone.app

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager

object SoundModeManager {

    private const val PREFS = "silent_zone_sound_state"
    private const val KEY_SAVED = "original_mode_saved"
    private const val KEY_RINGER = "original_ringer_mode"
    private const val KEY_FILTER = "original_dnd_filter"

    fun apply(context: Context, action: String): String {
        val audio = context.getSystemService(
            Context.AUDIO_SERVICE
        ) as AudioManager

        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        val mode = action.uppercase()

        if (mode !in listOf("SILENT", "VIBRATE", "DND")) {
            return "Unsupported sound action: $mode"
        }

        if (mode == "DND" &&
            !notificationManager.isNotificationPolicyAccessGranted
        ) {
            return "DND access required in Android Settings"
        }

        // Save the original phone mode only once.
        if (!prefs.getBoolean(KEY_SAVED, false)) {
            prefs.edit()
                .putInt(KEY_RINGER, audio.ringerMode)
                .putInt(
                    KEY_FILTER,
                    notificationManager.currentInterruptionFilter
                )
                .putBoolean(KEY_SAVED, true)
                .apply()
        }

        return try {
            when (mode) {
                "SILENT" ->
                    audio.ringerMode = AudioManager.RINGER_MODE_SILENT

                "VIBRATE" ->
                    audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE

                "DND" ->
                    notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_NONE
                    )
            }

            "$mode mode requested"
        } catch (e: SecurityException) {
            "Permission required: ${e.message ?: "Check Android Settings"}"
        } catch (e: Exception) {
            "Unable to change mode: ${e.message ?: "Unknown error"}"
        }
    }

    fun restore(context: Context): String {
        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        if (!prefs.getBoolean(KEY_SAVED, false)) {
            return "No saved phone mode to restore"
        }

        val audio = context.getSystemService(
            Context.AUDIO_SERVICE
        ) as AudioManager

        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        return try {
            audio.ringerMode = prefs.getInt(
                KEY_RINGER,
                AudioManager.RINGER_MODE_NORMAL
            )

            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(
                    prefs.getInt(
                        KEY_FILTER,
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                )
            }

            prefs.edit()
                .remove(KEY_SAVED)
                .remove(KEY_RINGER)
                .remove(KEY_FILTER)
                .apply()

            "Original phone mode restored"
        } catch (e: Exception) {
            "Restore failed: ${e.message ?: "Check permissions"}"
        }
    }
}
