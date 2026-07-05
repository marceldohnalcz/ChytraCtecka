package com.example.smartreader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedText(
    val id: String,
    val title: String,
    val content: String,
    val cursorPosition: Int,
    val savedAt: Long
)

/**
 * Ukládá rozečtený text (draft) a knihovnu uložených textů do SharedPreferences
 * jako jednoduché JSON pole. Žádná databáze není potřeba - textů bývá málo
 * a tohle je spolehlivé a bez další závislosti.
 */
object TextLibraryStore {
    private const val PREFS = "smartreader_prefs"
    private const val KEY_DRAFT_TEXT = "draft_text"
    private const val KEY_DRAFT_POSITION = "draft_position"
    private const val KEY_LIBRARY = "library_json"

    fun saveDraft(context: Context, text: String, cursorPosition: Int) {
        prefs(context).edit()
            .putString(KEY_DRAFT_TEXT, text)
            .putInt(KEY_DRAFT_POSITION, cursorPosition)
            .apply()
    }

    fun loadDraftText(context: Context): String =
        prefs(context).getString(KEY_DRAFT_TEXT, "") ?: ""

    fun loadDraftPosition(context: Context): Int =
        prefs(context).getInt(KEY_DRAFT_POSITION, 0)

    fun getLibrary(context: Context): List<SavedText> {
        val json = prefs(context).getString(KEY_LIBRARY, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<SavedText>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                SavedText(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    content = o.getString("content"),
                    cursorPosition = o.optInt("cursorPosition", 0),
                    savedAt = o.optLong("savedAt", 0L)
                )
            )
        }
        return list.sortedByDescending { it.savedAt }
    }

    fun addToLibrary(context: Context, content: String, titleHint: String? = null): SavedText {
        val list = getLibrary(context).toMutableList()
        val title = (titleHint ?: content).trim().replace("\n", " ").take(40).ifBlank { "Bez názvu" }
        val saved = SavedText(
            id = System.currentTimeMillis().toString(),
            title = title,
            content = content,
            cursorPosition = 0,
            savedAt = System.currentTimeMillis()
        )
        list.add(0, saved)
        persist(context, list)
        return saved
    }

    fun updateCursorPosition(context: Context, id: String, position: Int) {
        val list = getLibrary(context).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(cursorPosition = position)
            persist(context, list)
        }
    }

    fun removeFromLibrary(context: Context, id: String) {
        val list = getLibrary(context).filterNot { it.id == id }
        persist(context, list)
    }

    private fun persist(context: Context, list: List<SavedText>) {
        val arr = JSONArray()
        for (item in list) {
            val o = JSONObject()
            o.put("id", item.id)
            o.put("title", item.title)
            o.put("content", item.content)
            o.put("cursorPosition", item.cursorPosition)
            o.put("savedAt", item.savedAt)
            arr.put(o)
        }
        prefs(context).edit().putString(KEY_LIBRARY, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
