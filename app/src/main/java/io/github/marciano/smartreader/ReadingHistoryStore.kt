package io.github.marciano.smartreader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val id: String,
    val preview: String,
    val content: String,
    val timestamp: Long
)

/**
 * Historie textů, které appka přehrála - na rozdíl od Knihovny (ručně uložené
 * texty) se sem zaznamenává automaticky při každém spuštění čtení, pokud je to
 * zapnuté v nastavení. Ukládá se stejným způsobem jako knihovna - jednoduché
 * JSON pole v SharedPreferences.
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
                    timestamp = o.optLong("timestamp", 0L)
                )
            )
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun addEntry(context: Context, content: String) {
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
                timestamp = System.currentTimeMillis()
            )
        )
        persist(context, list.take(MAX_ENTRIES))
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
            arr.put(o)
        }
        prefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
