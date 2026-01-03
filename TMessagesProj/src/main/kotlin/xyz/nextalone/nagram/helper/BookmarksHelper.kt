package xyz.nextalone.nagram.helper

import androidx.core.content.edit
import org.telegram.messenger.UserConfig
import xyz.nextalone.nagram.NaConfig
import xyz.nextalone.nagram.ToggleResult
import java.util.concurrent.ConcurrentHashMap

object BookmarksHelper {
    const val KEY_PREFIX = "nax_bookmarks_"
    private const val LEGACY_KEY_PREFIX = "nax_bookmarks_v1_"
    private const val CURRENT_KEY_PREFIX = "nax_bookmarks_v2_"
    const val MAX_PER_CHAT: Int = 30

    private val cache = ConcurrentHashMap<String, IntArray>()
    private val migratedOwners = ConcurrentHashMap<Int, Long>()

    private fun getOwnerId(accountId: Int): Long {
        val userId = UserConfig.getInstance(accountId).getClientUserId()
        return if (userId != 0L) userId else accountId.toLong()
    }

    @Synchronized
    private fun ensureMigrated(accountId: Int): Long {
        val ownerId = getOwnerId(accountId)
        val previous = migratedOwners[accountId]
        if (previous == ownerId) {
            return ownerId
        }
        migratedOwners[accountId] = ownerId

        val legacyPrefix = LEGACY_KEY_PREFIX + accountId + "_"
        val prefs = NaConfig.getPreferences()
        val legacyKeys = prefs.all.keys.filter { it.startsWith(legacyPrefix) }
        if (legacyKeys.isEmpty()) {
            return ownerId
        }

        val currentPrefix = CURRENT_KEY_PREFIX + ownerId + "_"
        prefs.edit {
            for (oldKey in legacyKeys) {
                val dialogPart = oldKey.substring(legacyPrefix.length)
                if (dialogPart.isEmpty()) {
                    remove(oldKey)
                    cache.remove(oldKey)
                    continue
                }
                val newKey = currentPrefix + dialogPart

                val merged = mergeIds(getIds(oldKey), getIds(newKey))
                if (merged.isEmpty()) {
                    remove(newKey)
                    cache[newKey] = intArrayOf()
                } else {
                    putString(newKey, merged.joinToString(","))
                    cache[newKey] = merged.toIntArray()
                }
                remove(oldKey)
                cache.remove(oldKey)
            }
        }
        return ownerId
    }

    private fun mergeIds(legacy: IntArray, current: IntArray): List<Int> {
        if (legacy.isEmpty()) {
            return current.toList()
        }
        if (current.isEmpty()) {
            return legacy.toList()
        }
        val set = LinkedHashSet<Int>(legacy.size + current.size)
        for (id in legacy) {
            if (id != 0) {
                set.add(id)
            }
        }
        for (id in current) {
            if (id == 0) {
                continue
            }
            set.remove(id)
            set.add(id)
        }
        val merged = set.toList()
        return if (merged.size <= MAX_PER_CHAT) merged else merged.takeLast(MAX_PER_CHAT)
    }

    private fun key(accountId: Int, dialogId: Long): String {
        val ownerId = ensureMigrated(accountId)
        return CURRENT_KEY_PREFIX + ownerId + "_" + dialogId
    }

    private fun getIds(key: String): IntArray {
        return cache.computeIfAbsent(key) { k ->
            val raw = NaConfig.getPreferences().getString(k, null)
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
        val ownerId = getOwnerId(accountId)
        val legacyPrefix = LEGACY_KEY_PREFIX + accountId + "_"
        val currentPrefix = CURRENT_KEY_PREFIX + ownerId + "_"
        NaConfig.getPreferences().edit {
            for (key in NaConfig.getPreferences().all.keys) {
                if (key.startsWith(currentPrefix) || key.startsWith(legacyPrefix)) {
                    remove(key)
                }
            }
        }
        cache.clear()
        migratedOwners.remove(accountId)
    }

    @JvmStatic
    fun getBookmarkedDialogsCounts(accountId: Int): Map<Long, Int> {
        val ownerId = ensureMigrated(accountId)
        val prefix = CURRENT_KEY_PREFIX + ownerId + "_"
        val prefs = NaConfig.getPreferences()
        val result = LinkedHashMap<Long, Int>()
        for (key in prefs.all.keys) {
            if (!key.startsWith(prefix)) {
                continue
            }
            val dialogId = key.substring(prefix.length).toLongOrNull() ?: continue
            val ids = getIds(key)
            if (ids.isNotEmpty()) {
                result[dialogId] = ids.size
            }
        }
        return result
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
            NaConfig.getPreferences().edit { remove(key) }
            cache[key] = intArrayOf()
            return
        }
        val normalized = ids.distinct().takeLast(MAX_PER_CHAT)
        NaConfig.getPreferences().edit { putString(key, normalized.joinToString(",")) }
        cache[key] = normalized.toIntArray()
    }
}
