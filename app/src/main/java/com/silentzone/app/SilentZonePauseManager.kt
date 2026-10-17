package com.silentzone.app

import android.content.Context

object SilentZonePauseManager {

    private const val PREFS =
        "silent_zone_pause"

    private const val KEY_PAUSED_ZONE_ID =
        "paused_zone_id"

    /*
     * Check whether a PARTICULAR zone
     * has been paused by the user.
     */
    fun isPaused(
        context: Context,
        zoneId: String
    ): Boolean {

        if (zoneId.isBlank()) {
            return false
        }

        val pausedZoneId =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
                .getString(
                    KEY_PAUSED_ZONE_ID,
                    null
                )

        return pausedZoneId == zoneId
    }

    /*
     * Pause current zone only.
     */
    fun pause(
        context: Context,
        zoneId: String
    ): String {

        if (zoneId.isBlank()) {
            return "Unable to determine current zone"
        }

        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_PAUSED_ZONE_ID,
                zoneId
            )
            .apply()

        return SoundModeManager.restore(
            context
        )
    }

    /*
     * Remove pause completely.
     */
    fun resume(
        context: Context
    ) {

        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .remove(
                KEY_PAUSED_ZONE_ID
            )
            /*
             * Remove old version's boolean too.
             */
            .remove(
                "paused"
            )
            .apply()
    }

    /*
     * Called after leaving paused zone.
     */
    fun resetOnExit(
        context: Context
    ) {

        resume(context)
    }

    /*
     * Migration helper.
     *
     * Previous APK stored:
     * paused = true
     *
     * Remove that old global state.
     */
    fun clearOldPauseState(
        context: Context
    ) {

        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .remove(
                "paused"
            )
            .apply()
    }
}
