package io.github.marciano.smartreader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TrackedProfile(
    val id: String,
    val name: String,
    val url: String,
    val lastCheckedTimestamp: Long? = null
)

/**
 * Seznam ručně sledovaných profilů (typicky FB/IG) - appka sama nic
 * nestahuje ani nekontroluje, jen si pamatuje jméno, odkaz a kdy byl profil
 * naposledy otevřený. Slouží jako rychlý rozcestník: klepneš na profil,
 * appka ho otevře v dané síti, ty zkontroluješ nové příspěvky ručně a
 * případný text pošleš appce přes Sdílet/výběr textu (to appka umí už teď).
 */
object TrackedProfilesStore {
    private const val PREFS = "smartreader_prefs"
    private const val KEY_PROFILES = "tracked_profiles_json"

    fun getProfiles(context: Context): List<TrackedProfile> {
        val json = prefs(context).getString(KEY_PROFILES, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<TrackedProfile>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                TrackedProfile(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    url = o.getString("url"),
                    lastCheckedTimestamp = if (o.has("lastChecked") && !o.isNull("lastChecked")) {
                        o.getLong("lastChecked")
                    } else null
                )
            )
        }
        return list.sortedBy { it.name.lowercase() }
    }

    fun addProfile(context: Context, name: String, url: String) {
        val trimmedName = name.trim()
        val trimmedUrl = url.trim()
        if (trimmedName.isBlank() || trimmedUrl.isBlank()) return
        val list = getProfiles(context).toMutableList()
        list.add(
            TrackedProfile(
                id = System.currentTimeMillis().toString(),
                name = trimmedName,
                url = trimmedUrl,
                lastCheckedTimestamp = null
            )
        )
        persist(context, list)
    }

    fun removeProfile(context: Context, id: String) {
        val list = getProfiles(context).filterNot { it.id == id }
        persist(context, list)
    }

    /** Zavolat v okamžiku, kdy appka profil otevře - aktualizuje čas poslední kontroly. */
    fun markChecked(context: Context, id: String) {
        val list = getProfiles(context).map {
            if (it.id == id) it.copy(lastCheckedTimestamp = System.currentTimeMillis()) else it
        }
        persist(context, list)
    }

    private fun persist(context: Context, list: List<TrackedProfile>) {
        val arr = JSONArray()
        for (item in list) {
            val o = JSONObject()
            o.put("id", item.id)
            o.put("name", item.name)
            o.put("url", item.url)
            if (item.lastCheckedTimestamp != null) {
                o.put("lastChecked", item.lastCheckedTimestamp)
            }
            arr.put(o)
        }
        prefs(context).edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
