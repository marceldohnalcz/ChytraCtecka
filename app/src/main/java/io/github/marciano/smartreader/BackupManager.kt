package io.github.marciano.smartreader

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Zálohování a obnova VŠECH dat, která si appka pamatuje - nastavení,
 * historie čtení, knihovna uložených textů a sledované profily - do jednoho
 * čitelného souboru .json.
 *
 * Formát má vlastní číslo verze ([FORMAT_VERSION]): až se v budoucnu tvar dat
 * změní, obnova ze starší zálohy podle něj pozná, s čím má co do činění, místo
 * aby appku shodila. Samotné čtení je proto psané shovívavě - chybějící části
 * se prostě přeskočí, nezpůsobí chybu.
 */
object BackupManager {

    const val FORMAT_VERSION = 1
    const val FILE_NAME_PREFIX = "chytra-ctecka-zaloha"

    /** Jak se má obnova zachovat k datům, která už v appce jsou. */
    enum class RestoreMode {
        /** Zahodit stávající a nahradit obsahem zálohy. */
        REPLACE,

        /** Přidat k stávajícím to, co v nich ještě není (podle id). */
        MERGE
    }

    data class RestoreResult(
        val historyCount: Int,
        val libraryCount: Int,
        val profilesCount: Int,
        val settingsRestored: Boolean
    )

    // ---------- ZÁLOHA ----------

    fun createBackupJson(context: Context): String {
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put("createdAt", System.currentTimeMillis())
        root.put("appVersion", appVersionName(context))

        root.put("settings", settingsToJson(context))
        root.put("history", historyToJson(context))
        root.put("library", libraryToJson(context))
        root.put("trackedProfiles", profilesToJson(context))

        return root.toString(2)
    }

    private fun appVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    private fun settingsToJson(context: Context): JSONObject {
        val o = JSONObject()
        AppSettings.loadVoiceName(context)?.let { o.put("voiceName", it) }
        AppSettings.loadTtsEngine(context)?.let { o.put("ttsEngine", it) }
        o.put("speed", AppSettings.loadSpeed(context).toDouble())
        o.put("pitch", AppSettings.loadPitch(context).toDouble())
        o.put("volume", AppSettings.loadVolume(context).toDouble())
        o.put("autoResumeAfterCall", AppSettings.loadAutoResumeAfterCall(context))
        o.put("historyEnabled", AppSettings.loadHistoryEnabled(context))
        o.put("themeMode", AppSettings.loadThemeMode(context))
        return o
    }

    private fun historyToJson(context: Context): JSONArray {
        val arr = JSONArray()
        for (item in ReadingHistoryStore.getHistory(context)) {
            val o = JSONObject()
            o.put("id", item.id)
            o.put("preview", item.preview)
            o.put("content", item.content)
            o.put("timestamp", item.timestamp)
            o.put("played", item.played)
            arr.put(o)
        }
        return arr
    }

    private fun libraryToJson(context: Context): JSONArray {
        val arr = JSONArray()
        for (item in TextLibraryStore.getLibrary(context)) {
            val o = JSONObject()
            o.put("id", item.id)
            o.put("title", item.title)
            o.put("content", item.content)
            o.put("cursorPosition", item.cursorPosition)
            o.put("savedAt", item.savedAt)
            arr.put(o)
        }
        return arr
    }

    private fun profilesToJson(context: Context): JSONArray {
        val arr = JSONArray()
        for (item in TrackedProfilesStore.getProfiles(context)) {
            val o = JSONObject()
            o.put("id", item.id)
            o.put("name", item.name)
            o.put("url", item.url)
            item.lastCheckedTimestamp?.let { o.put("lastChecked", it) }
            arr.put(o)
        }
        return arr
    }

    // ---------- OBNOVA ----------

    /**
     * Načte zálohu z textu souboru. Vyhodí [IllegalArgumentException] s
     * popisem, pokud soubor není platná záloha této appky - volající to má
     * odchytit a slušně oznámit uživateli, ne spadnout.
     */
    fun restoreFromJson(context: Context, jsonText: String, mode: RestoreMode): RestoreResult {
        val root = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            throw IllegalArgumentException("not_json")
        }

        val version = root.optInt("formatVersion", -1)
        if (version <= 0) throw IllegalArgumentException("not_backup")
        if (version > FORMAT_VERSION) throw IllegalArgumentException("newer_version")

        // Nastavení se obnovuje vždy celé (sloučení u nastavení nedává smysl).
        val settingsRestored = root.optJSONObject("settings")?.let { s ->
            restoreSettings(context, s)
            true
        } ?: false

        val history = parseHistory(root.optJSONArray("history"))
        val library = parseLibrary(root.optJSONArray("library"))
        val profiles = parseProfiles(root.optJSONArray("trackedProfiles"))

        val finalHistory = if (mode == RestoreMode.REPLACE) history else {
            val existing = ReadingHistoryStore.getHistory(context)
            val existingIds = existing.map { it.id }.toSet()
            existing + history.filter { it.id !in existingIds }
        }
        val finalLibrary = if (mode == RestoreMode.REPLACE) library else {
            val existing = TextLibraryStore.getLibrary(context)
            val existingIds = existing.map { it.id }.toSet()
            existing + library.filter { it.id !in existingIds }
        }
        val finalProfiles = if (mode == RestoreMode.REPLACE) profiles else {
            val existing = TrackedProfilesStore.getProfiles(context)
            val existingIds = existing.map { it.id }.toSet()
            existing + profiles.filter { it.id !in existingIds }
        }

        ReadingHistoryStore.replaceAll(context, finalHistory)
        TextLibraryStore.replaceAll(context, finalLibrary)
        TrackedProfilesStore.replaceAll(context, finalProfiles)

        return RestoreResult(
            historyCount = history.size,
            libraryCount = library.size,
            profilesCount = profiles.size,
            settingsRestored = settingsRestored
        )
    }

    private fun restoreSettings(context: Context, s: JSONObject) {
        if (s.has("voiceName")) AppSettings.saveVoiceName(context, s.getString("voiceName"))
        if (s.has("ttsEngine")) AppSettings.saveTtsEngine(context, s.getString("ttsEngine"))
        if (s.has("speed")) {
            AppSettings.saveSpeed(context, s.getDouble("speed").toFloat().coerceIn(0.5f, 3.0f))
        }
        if (s.has("pitch")) {
            AppSettings.savePitch(context, s.getDouble("pitch").toFloat().coerceIn(0.5f, 1.5f))
        }
        if (s.has("volume")) {
            AppSettings.saveVolume(context, s.getDouble("volume").toFloat().coerceIn(0f, 1f))
        }
        if (s.has("autoResumeAfterCall")) {
            AppSettings.saveAutoResumeAfterCall(context, s.getBoolean("autoResumeAfterCall"))
        }
        if (s.has("historyEnabled")) {
            AppSettings.saveHistoryEnabled(context, s.getBoolean("historyEnabled"))
        }
        if (s.has("themeMode")) {
            val mode = s.getInt("themeMode")
            // Jen hodnoty, které appka zná - cizí číslo by jinak mohlo rozbít vzhled.
            val allowed = setOf(
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
            if (mode in allowed) AppSettings.saveThemeMode(context, mode)
        }
    }

    private fun parseHistory(arr: JSONArray?): List<HistoryEntry> {
        if (arr == null) return emptyList()
        val list = mutableListOf<HistoryEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val content = o.optString("content", "")
            if (content.isBlank()) continue
            list.add(
                HistoryEntry(
                    id = o.optString("id", System.nanoTime().toString() + i),
                    preview = o.optString("preview", content.replace("\n", " ").take(60)),
                    content = content,
                    timestamp = o.optLong("timestamp", 0L),
                    played = o.optBoolean("played", false)
                )
            )
        }
        return list
    }

    private fun parseLibrary(arr: JSONArray?): List<SavedText> {
        if (arr == null) return emptyList()
        val list = mutableListOf<SavedText>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val content = o.optString("content", "")
            if (content.isBlank()) continue
            list.add(
                SavedText(
                    id = o.optString("id", System.nanoTime().toString() + i),
                    title = o.optString("title", content.take(40)),
                    content = content,
                    cursorPosition = o.optInt("cursorPosition", 0),
                    savedAt = o.optLong("savedAt", 0L)
                )
            )
        }
        return list
    }

    private fun parseProfiles(arr: JSONArray?): List<TrackedProfile> {
        if (arr == null) return emptyList()
        val list = mutableListOf<TrackedProfile>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name", "")
            val url = o.optString("url", "")
            if (name.isBlank() || url.isBlank()) continue
            list.add(
                TrackedProfile(
                    id = o.optString("id", System.nanoTime().toString() + i),
                    name = name,
                    url = url,
                    lastCheckedTimestamp = if (o.has("lastChecked") && !o.isNull("lastChecked")) {
                        o.optLong("lastChecked")
                    } else null
                )
            )
        }
        return list
    }
}
