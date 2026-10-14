package com.silentzone.app

import android.content.Context

object SilentZonePauseManager {

    private const val PREFS =
        "silent_zone_pause"

    private const val KEY_PAUSED =
        "paused"

    fun isPaused(
        context: Context
    ): Boolean {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                KEY_PAUSED,
                false
            )
    }

    fun pause(
        context: Context
    ): String {

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                KEY_PAUSED,
                true
            )
            .apply()

        return SoundModeManager.restore(
            context
        )
    }

    fun resume(
        context: Context
    ) {

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                KEY_PAUSED,
                false
            )
            .apply()
    }

    fun resetOnExit(
        context: Context
    ) {

        resume(context)
    }
}
