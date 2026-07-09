package io.github.marceldohnalcz.smartreader

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class SmartReaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Aplikovat uložený motiv co nejdřív, ať appka nebliká špatnou barvou při startu.
        AppCompatDelegate.setDefaultNightMode(AppSettings.loadThemeMode(this))
    }
}
