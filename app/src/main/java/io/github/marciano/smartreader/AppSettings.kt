package io.github.marciano.smartreader

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object AppSettings {
    private const val PREFS = "smartreader_settings"
    private const val KEY_VOICE_NAME = "voice_name"
    private const val KEY_SPEED = "speed_rate"
    private const val KEY_PITCH = "voice_pitch"
    private const val KEY_VOLUME = "reading_volume"
    private const val KEY_AUTO_RESUME = "auto_resume_after_call"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_TTS_ENGINE = "tts_engine_package"
    private const val KEY_ACTIVE_PIPER_VOICE = "active_piper_voice_id"
    private const val KEY_HISTORY_ENABLED = "history_enabled"

    fun saveVoiceName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_VOICE_NAME, name).apply()
    }

    fun loadVoiceName(context: Context): String? =
        prefs(context).getString(KEY_VOICE_NAME, null)

    fun saveSpeed(context: Context, rate: Float) {
        prefs(context).edit().putFloat(KEY_SPEED, rate).apply()
    }

    fun loadSpeed(context: Context): Float =
        prefs(context).getFloat(KEY_SPEED, 1.0f)

    /** Výška hlasu - 1.0 = normální (neutrální výchozí hodnota). */
    fun savePitch(context: Context, pitch: Float) {
        prefs(context).edit().putFloat(KEY_PITCH, pitch).apply()
    }

    fun loadPitch(context: Context): Float =
        prefs(context).getFloat(KEY_PITCH, 1.0f)

    fun saveVolume(context: Context, volume: Float) {
        prefs(context).edit().putFloat(KEY_VOLUME, volume).apply()
    }

    fun loadVolume(context: Context): Float =
        prefs(context).getFloat(KEY_VOLUME, 1.0f)

    fun saveAutoResumeAfterCall(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_RESUME, enabled).apply()
    }

    fun loadAutoResumeAfterCall(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_RESUME, false)

    /** Uloží MODE_NIGHT_* konstantu z AppCompatDelegate (NO/YES/FOLLOW_SYSTEM). */
    fun saveThemeMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun loadThemeMode(context: Context): Int =
        prefs(context).getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    /** null = použít systémový výchozí hlasový modul (žádný konkrétní není uložený). */
    fun saveTtsEngine(context: Context, packageName: String?) {
        val editor = prefs(context).edit()
        if (packageName == null) {
            editor.remove(KEY_TTS_ENGINE)
        } else {
            editor.putString(KEY_TTS_ENGINE, packageName)
        }
        editor.apply()
    }

    fun loadTtsEngine(context: Context): String? =
        prefs(context).getString(KEY_TTS_ENGINE, null)

    /**
     * ID stažitelného (Piper) hlasu, který má appka použít pro čtení - null
     * znamená "používej systémový TTS hlas" (výchozí, jako doteď).
     */
    fun saveActivePiperVoice(context: Context, voiceId: String?) {
        val editor = prefs(context).edit()
        if (voiceId == null) {
            editor.remove(KEY_ACTIVE_PIPER_VOICE)
        } else {
            editor.putString(KEY_ACTIVE_PIPER_VOICE, voiceId)
        }
        editor.apply()
    }

    fun loadActivePiperVoice(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE_PIPER_VOICE, null)

    fun saveHistoryEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HISTORY_ENABLED, enabled).apply()
    }

    fun loadHistoryEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HISTORY_ENABLED, false)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
