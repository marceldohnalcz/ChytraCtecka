package com.example.smartreader

import android.content.Context

object AppSettings {
    private const val PREFS = "smartreader_settings"
    private const val KEY_VOICE_NAME = "voice_name"
    private const val KEY_SPEED = "speed_rate"
    private const val KEY_VOLUME = "reading_volume"
    private const val KEY_AUTO_RESUME = "auto_resume_after_call"

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

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
