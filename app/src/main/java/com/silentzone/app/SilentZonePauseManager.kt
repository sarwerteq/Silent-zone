package com.silentzone.app
import android.content.Context
object SilentZonePauseManager {
    private const val PREFS="silent_zone_pause"; private const val KEY="paused_zone_id"
    fun isPaused(context:Context,zoneId:String)=zoneId.isNotBlank()&&context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,null)==zoneId
    fun pause(context:Context,zoneId:String):String { if(zoneId.isBlank()) return "Unable to determine current zone"; context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,zoneId).apply(); return SoundModeManager.restore(context) }
    fun resume(context:Context){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY).remove("paused").apply()}
    fun resetOnExit(context:Context)=resume(context)
    fun clearOldPauseState(context:Context){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove("paused").apply()}
}
