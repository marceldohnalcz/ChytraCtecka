package io.github.marciano.smartreader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val id: String,
    val preview: String,
    val content: String,
    val timestamp: Long,
    val played: Boolean = false
)

/**
 * Historie textů v appce - zaznamenává se sem každé vložení textu (např.
 * přes tlačítko Vložit), pokud je to zapnuté v nastavení. Pokud je daný text
 * následně i přehrán, aktualizuje se STEJNÝ záznam (nastaví se played=true),
 * místo aby vznikl duplicitní druhý řádek pro tentýž text.
 */
object ReadingHistoryStore {
    private const val PREFS = "smartreader_prefs"
    private const val KEY_HISTORY = "history_json"
    private const val MAX_ENTRIES = 100

    fun getHistory(context: Context): List<HistoryEntry> {
        val json = prefs(context).getString(KEY_HISTORY, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<HistoryEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                HistoryEntry(
                    id = o.getString("id"),
                    preview = o.getString("preview"),
                    content = o.getString("content"),
                    timestamp = o.optLong("timestamp", 0L),
                    played = o.optBoolean("played", false)
                )
            )
        }
        return list.sortedByDescending { it.timestamp }
    }

    /** Přidá nový záznam (např. při vložení textu). */
    fun addEntry(context: Context, content: String, played: Boolean = false) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return
        val list = getHistory(context).toMutableList()
        val preview = trimmed.replace("\n", " ").take(60)
        list.add(
            0,
            HistoryEntry(
                id = System.currentTimeMillis().toString(),
                preview = preview,
                content = trimmed,
                timestamp = System.currentTimeMillis(),
                played = played
            )
        )
        persist(context, list.take(MAX_ENTRIES))
    }

    /**
     * Zavolat, když appka spustí čtení textu. Pokud stejný text už v historii
     * je (např. byl před chvílí vložen), jen ho označí jako přehraný - nevytváří
     * duplicitní záznam. Pokud v historii není (např. byl napsán ručně), založí
     * nový záznam rovnou jako přehraný.
     */
    fun markPlayedByContent(context: Context, content: String) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return
        val list = getHistory(context).toMutableList()
        val idx = list.indexOfFirst { it.content == trimmed }
        if (idx >= 0) {
            if (!list[idx].played) {
                list[idx] = list[idx].copy(played = true)
                persist(context, list)
            }
        } else {
            addEntry(context, trimmed, played = true)
        }
    }

    fun removeEntry(context: Context, id: String) {
        val list = getHistory(context).filterNot { it.id == id }
        persist(context, list)
    }

    fun clearHistory(context: Context) {
        persist(context, emptyList())
    }

    private fun persist(context: Context, list: List<HistoryEntry>) {
        val arr = JSONArray()
        for (item in list) {
            val o = JSONObject()
            o.put("id", item.id)
            o.put("preview", item.preview)
            o.put("content", item.content)
            o.put("timestamp", item.timestamp)
            o.put("played", item.played)
            arr.put(o)
        }
        prefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
