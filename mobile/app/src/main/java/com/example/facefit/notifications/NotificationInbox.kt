package com.example.facefit.notifications

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * The in-app list of notifications FaceFit has sent, kept per account on the device.
 * Entries are recorded even when Android notifications are turned off.
 */
internal object NotificationInbox {
    data class Entry(
        val id: Long,
        val title: String,
        val message: String,
        val frameName: String?,
        val timeMs: Long,
        val read: Boolean,
    )

    private const val MAX_ENTRIES = 50
    private val version = MutableStateFlow(0)

    /** Changes whenever the inbox changes, so screens can refresh. */
    val changes: StateFlow<Int> get() = version

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("notification_inbox", Context.MODE_PRIVATE)

    private fun key(accountId: String) = "inbox.$accountId"

    @Synchronized
    fun add(context: Context, accountId: String, title: String, message: String, frameName: String?,
            timeMs: Long = System.currentTimeMillis()) {
        val updated = listOf(Entry(timeMs, title, message, frameName, timeMs, read = false)) +
            entries(context, accountId)
        save(context, accountId, updated.take(MAX_ENTRIES))
    }

    /** Newest first. */
    fun entries(context: Context, accountId: String): List<Entry> {
        val raw = prefs(context).getString(key(accountId), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                Entry(
                    id = item.getLong("id"),
                    title = item.getString("title"),
                    message = item.getString("message"),
                    frameName = item.optString("frameName").takeIf { it.isNotBlank() },
                    timeMs = item.getLong("timeMs"),
                    read = item.optBoolean("read", false),
                )
            }.sortedByDescending { it.timeMs }
        }.getOrDefault(emptyList())
    }

    fun hasUnread(context: Context, accountId: String) = entries(context, accountId).any { !it.read }

    @Synchronized
    fun markAllRead(context: Context, accountId: String) {
        val current = entries(context, accountId)
        if (current.any { !it.read }) save(context, accountId, current.map { it.copy(read = true) })
    }

    @Synchronized
    fun clear(context: Context, accountId: String) {
        prefs(context).edit().remove(key(accountId)).apply()
        version.value++
    }

    private fun save(context: Context, accountId: String, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject()
                .put("id", entry.id)
                .put("title", entry.title)
                .put("message", entry.message)
                .put("frameName", entry.frameName ?: "")
                .put("timeMs", entry.timeMs)
                .put("read", entry.read))
        }
        prefs(context).edit().putString(key(accountId), array.toString()).apply()
        version.value++
    }
}
