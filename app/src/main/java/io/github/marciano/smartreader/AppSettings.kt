package io.github.marciano.smartreader

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object AppSettings {
    private const val PREFS = "smartreader_settings"
    private const val KEY_VOICE_NAME = "voice_name"
    private const val KEY_SPEED = "speed_rate"
    private const val KEY_VOLUME = "reading_volume"
    private const val KEY_AUTO_RESUME = "auto_resume_after_call"
    private const val KEY_THEME_MODE = "theme_mode"

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

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
