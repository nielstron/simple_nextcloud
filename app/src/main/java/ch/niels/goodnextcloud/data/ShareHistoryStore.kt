package ch.niels.goodnextcloud.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ShareHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("share_history", Context.MODE_PRIVATE)

    fun frequent(account: Account): List<ShareUser> = entries(account)
        .sortedWith(compareByDescending<Entry> { it.count }.thenByDescending { it.lastSharedAt })
        .map { ShareUser(it.id, it.displayName) }

    fun record(account: Account, user: ShareUser) {
        val entries = entries(account).associateBy(Entry::id).toMutableMap()
        val previous = entries[user.id]
        entries[user.id] = Entry(
            id = user.id,
            displayName = user.displayName,
            count = (previous?.count ?: 0) + 1,
            lastSharedAt = System.currentTimeMillis(),
        )
        val json = JSONArray()
        entries.values.forEach { entry ->
            json.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("displayName", entry.displayName)
                    .put("count", entry.count)
                    .put("lastSharedAt", entry.lastSharedAt),
            )
        }
        preferences.edit().putString(key(account), json.toString()).apply()
    }

    private fun entries(account: Account): List<Entry> {
        val json = JSONArray(preferences.getString(key(account), "[]"))
        return buildList {
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                add(
                    Entry(
                        id = item.getString("id"),
                        displayName = item.getString("displayName"),
                        count = item.getInt("count"),
                        lastSharedAt = item.getLong("lastSharedAt"),
                    ),
                )
            }
        }
    }

    private fun key(account: Account) = "${account.serverUrl}|${account.username}"

    private data class Entry(
        val id: String,
        val displayName: String,
        val count: Int,
        val lastSharedAt: Long,
    )
}
