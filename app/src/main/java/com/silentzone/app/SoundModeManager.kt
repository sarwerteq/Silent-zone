package com.silentzone.app
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
object SoundModeManager {
    private const val PREFS="silent_zone_sound_state"; private const val SAVED="original_mode_saved"; private const val RINGER="original_ringer_mode"; private const val FILTER="original_dnd_filter"
    fun apply(context:Context, action:String):String {
        val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE); val mode=action.uppercase()
        if(mode !in listOf("SILENT","VIBRATE","DND")) return "Unsupported sound action: $mode"
        if(mode=="DND"&&!nm.isNotificationPolicyAccessGranted) return "DND access required"
        if(!p.getBoolean(SAVED,false)) p.edit().putInt(RINGER,audio.ringerMode).putInt(FILTER,nm.currentInterruptionFilter).putBoolean(SAVED,true).apply()
        return try { when(mode){"SILENT"->audio.ringerMode=AudioManager.RINGER_MODE_SILENT;"VIBRATE"->audio.ringerMode=AudioManager.RINGER_MODE_VIBRATE;"DND"->nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)}; "$mode mode applied successfully" } catch(e:Exception){"Unable to change mode: ${e.message ?: "Unknown error"}"}
    }
    fun restore(context:Context):String {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE); if(!p.getBoolean(SAVED,false)) return "Phone mode unchanged"
        val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager; val nm=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return try { audio.ringerMode=p.getInt(RINGER,AudioManager.RINGER_MODE_NORMAL); if(nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(p.getInt(FILTER,NotificationManager.INTERRUPTION_FILTER_ALL)); p.edit().clear().apply(); "Original phone mode restored" } catch(e:Exception){"Restore failed: ${e.message ?: "Unknown error"}"}
    }
}
