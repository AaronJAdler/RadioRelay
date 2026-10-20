package com.radiorelay

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class LogEntry(
    val id: Long,
    val time: Long,
    val chat: String,
    val number: String,
    val keyword: String,
    val status: String,
)

/** All app state lives here (SharedPreferences, so it survives restarts). */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("radio_relay", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(K_ENABLED, false)
        set(v) = sp.edit().putBoolean(K_ENABLED, v).apply()

    /** Messages older than this (from before auto-send was switched on) are never relayed. */
    var armedAt: Long
        get() = sp.getLong(K_ARMED_AT, 0L)
        set(v) = sp.edit().putLong(K_ARMED_AT, v).apply()

    /** Only chats whose name contains this text are watched. */
    var chatFilter: String
        get() = sp.getString(K_CHAT, "") ?: ""
        set(v) = sp.edit().putString(K_CHAT, v.trim()).apply()

    /** Don't text the same word to the same line again within this many minutes. */
    var cooldownMinutes: Int
        get() = sp.getInt(K_COOLDOWN, 5)
        set(v) = sp.edit().putInt(K_COOLDOWN, v.coerceIn(0, 24 * 60)).apply()

    var stations: List<Station>
        get() {
            val raw = sp.getString(K_STATIONS, null) ?: return DEFAULT_STATIONS
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    Station(o.getString("prefix"), o.getString("number"))
                }
            } catch (e: Exception) {
                DEFAULT_STATIONS
            }
        }
        set(list) {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("prefix", it.prefix).put("number", it.number)) }
            sp.edit().putString(K_STATIONS, arr.toString()).apply()
        }

    fun chatMatches(title: String?): Boolean {
        val f = chatFilter
        if (f.isBlank() || title.isNullOrBlank()) return false
        return title.contains(f, ignoreCase = true)
    }

    // ---- Recently seen chat names (so the user can tap to pick the group) ----

    fun recentChats(): List<String> = readStringList(K_RECENT)

    fun noteChat(title: String) {
        val current = recentChats()
        if (current.firstOrNull() == title) return
        writeStringList(K_RECENT, (listOf(title) + current.filter { it != title }).take(8))
    }

    // ---- De-duplication: notifications get re-posted with the same messages ----

    /** Returns true the first time a key is seen, false afterwards. */
    fun markProcessed(key: String): Boolean {
        val keys = readStringList(K_PROCESSED)
        if (key in keys) return false
        writeStringList(K_PROCESSED, (keys + key).takeLast(300))
        return true
    }

    fun cooldownAllows(number: String, keyword: String, now: Long): Boolean {
        val last = lastSends().optLong(cooldownKey(number, keyword), 0L)
        return now - last >= cooldownMinutes * 60_000L
    }

    fun recordSend(number: String, keyword: String, now: Long) {
        val map = lastSends()
        map.put(cooldownKey(number, keyword), now)
        // prune anything older than a day
        val stale = map.keys().asSequence().filter { now - map.optLong(it) > 86_400_000L }.toList()
        stale.forEach { map.remove(it) }
        sp.edit().putString(K_LAST_SENDS, map.toString()).apply()
    }

    private fun lastSends(): JSONObject =
        try { JSONObject(sp.getString(K_LAST_SENDS, "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    private fun cooldownKey(number: String, keyword: String) = "$number|${keyword.lowercase()}"

    // ---- Activity log ----

    fun nextLogId(): Long {
        val id = sp.getLong(K_LOG_SEQ, 1L)
        sp.edit().putLong(K_LOG_SEQ, id + 1).apply()
        return id
    }

    fun log(): List<LogEntry> = try {
        val arr = JSONArray(sp.getString(K_LOG, "[]") ?: "[]")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            LogEntry(
                o.getLong("id"), o.getLong("time"), o.optString("chat"),
                o.getString("number"), o.getString("keyword"), o.getString("status"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun addLog(e: LogEntry) = writeLog((listOf(e) + log()).take(50))

    fun updateLogStatus(id: Long, status: String) =
        writeLog(log().map { if (it.id == id) it.copy(status = status) else it })

    fun clearLog() = writeLog(emptyList())

    private fun writeLog(entries: List<LogEntry>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("time", it.time).put("chat", it.chat)
                    .put("number", it.number).put("keyword", it.keyword).put("status", it.status)
            )
        }
        sp.edit().putString(K_LOG, arr.toString()).apply()
    }

    // ---- helpers ----

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(l)

    private fun readStringList(key: String): List<String> = try {
        val arr = JSONArray(sp.getString(key, "[]") ?: "[]")
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeStringList(key: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        sp.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        const val K_ENABLED = "enabled"
        const val K_ARMED_AT = "armed_at"
        const val K_CHAT = "chat_filter"
        const val K_COOLDOWN = "cooldown_minutes"
        const val K_STATIONS = "stations"
        const val K_RECENT = "recent_chats"
        const val K_PROCESSED = "processed_keys"
        const val K_LAST_SENDS = "last_sends"
        const val K_LOG = "log"
        const val K_LOG_SEQ = "log_seq"

        val DEFAULT_STATIONS = listOf(
            Station("98", "9169090985"),
            Station("96", "9169090969"),
            Station("94", "9169090947"),
        )
    }
}
