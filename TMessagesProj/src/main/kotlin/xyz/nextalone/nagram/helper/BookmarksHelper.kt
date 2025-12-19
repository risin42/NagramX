package xyz.nextalone.nagram.helper

import androidx.core.content.edit
import xyz.nextalone.nagram.NaConfig
import xyz.nextalone.nagram.ToggleResult
import java.util.concurrent.ConcurrentHashMap

object BookmarksHelper {
    const val KEY_PREFIX = "nax_bookmarks_v1_"
    const val MAX_PER_CHAT: Int = 30

    private val cache = ConcurrentHashMap<String, IntArray>()

    private fun key(accountId: Int, dialogId: Long): String {
        return KEY_PREFIX + accountId + "_" + dialogId
    }

    private fun getIds(key: String): IntArray {
        return cache.computeIfAbsent(key) { k ->
            val raw = NaConfig.preferences.getString(k, null)
            if (raw.isNullOrBlank()) {
                intArrayOf()
            } else {
                val result = ArrayList<Int>(MAX_PER_CHAT)
                val seen = HashSet<Int>(MAX_PER_CHAT)
                for (part in raw.split(',')) {
                    val id = part.trim().toIntOrNull() ?: continue
                    if (id == 0) continue
                    if (seen.add(id)) {
                        result.add(id)
                    }
                }
                val ids = if (result.size <= MAX_PER_CHAT) {
                    result
                } else {
                    ArrayList(result.takeLast(MAX_PER_CHAT))
                }
                ids.toIntArray()
            }
        }
    }

    private fun normalizeMessageIds(messageIds: IntArray): List<Int> {
        if (messageIds.isEmpty()) {
            return emptyList()
        }
        val result = ArrayList<Int>(messageIds.size)
        val seen = HashSet<Int>(messageIds.size)
        for (id in messageIds) {
            if (id == 0) {
                continue
            }
            if (seen.add(id)) {
                result.add(id)
            }
        }
        return result
    }

    @JvmStatic
    fun isBookmarked(accountId: Int, dialogId: Long, messageId: Int): Boolean {
        if (!NaConfig.showAddToBookmark.Bool() || messageId == 0) {
            return false
        }
        val ids = getIds(key(accountId, dialogId))
        for (id in ids) {
            if (id == messageId) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun getBookmarkedMessageIds(accountId: Int, dialogId: Long): IntArray {
        return getIds(key(accountId, dialogId)).clone()
    }

    @JvmStatic
    fun areAllBookmarked(accountId: Int, dialogId: Long, messageIds: IntArray): Boolean {
        if (!NaConfig.showAddToBookmark.Bool()) {
            return false
        }
        val ids = normalizeMessageIds(messageIds)
        if (ids.isEmpty()) {
            return false
        }
        val current = getIds(key(accountId, dialogId))
        if (current.isEmpty()) {
            return false
        }
        val currentSet = current.toHashSet()
        for (id in ids) {
            if (!currentSet.contains(id)) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun clearAllBookmarks(accountId: Int) {
        val prefix = KEY_PREFIX + accountId + "_"
        NaConfig.preferences.edit {
            for (key in NaConfig.preferences.all.keys) {
                if (key.startsWith(prefix)) {
                    remove(key)
                }
            }
        }
        cache.clear()
    }

    @JvmStatic
    fun toggleBookmarks(accountId: Int, dialogId: Long, messageIds: IntArray): ToggleResult {
        val ids = normalizeMessageIds(messageIds)
        if (ids.isEmpty()) {
            return ToggleResult.REMOVED
        }
        val k = key(accountId, dialogId)
        val current = getIds(k).toMutableList()
        val currentSet = current.toHashSet()
        val allBookmarked = ids.all { currentSet.contains(it) }
        if (allBookmarked) {
            val removed = current.removeAll(ids.toSet())
            if (removed) {
                persist(k, current)
            }
            return ToggleResult.REMOVED
        }
        var missingCount = 0
        for (id in ids) {
            if (!currentSet.contains(id)) {
                missingCount++
            }
        }
        if (current.size + missingCount > MAX_PER_CHAT) {
            return ToggleResult.LIMIT_REACHED
        }
        for (id in ids) {
            if (currentSet.add(id)) {
                current.add(id)
            }
        }
        persist(k, current)
        return ToggleResult.ADDED
    }

    @JvmStatic
    fun removeBookmark(accountId: Int, dialogId: Long, messageId: Int): Boolean {
        return removeBookmarks(accountId, dialogId, intArrayOf(messageId))
    }

    @JvmStatic
    fun removeBookmarks(accountId: Int, dialogId: Long, messageIds: IntArray): Boolean {
        val ids = normalizeMessageIds(messageIds)
        if (ids.isEmpty()) {
            return false
        }
        val k = key(accountId, dialogId)
        val current = getIds(k).toMutableList()
        val removed = current.removeAll(ids.toSet())
        if (removed) {
            persist(k, current)
        }
        return removed
    }

    private fun persist(key: String, ids: List<Int>) {
        if (ids.isEmpty()) {
            NaConfig.preferences.edit { remove(key) }
            cache[key] = intArrayOf()
            return
        }
        val normalized = ids.distinct().takeLast(MAX_PER_CHAT)
        NaConfig.preferences.edit { putString(key, normalized.joinToString(",")) }
        cache[key] = normalized.toIntArray()
    }
}
