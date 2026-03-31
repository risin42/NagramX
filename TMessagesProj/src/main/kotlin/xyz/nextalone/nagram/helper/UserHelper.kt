package xyz.nextalone.nagram.helper

import org.telegram.messenger.UserConfig

/**
 * Shared utility functions for user-related operations.
 * Extracted to avoid code duplication across LocalPremiumStatusHelper and LocalPeerColorHelper.
 */
object UserHelper {
    /**
     * Check if the given userId belongs to any locally activated account.
     */
    @JvmStatic
    fun isLocalUser(userId: Long): Boolean {
        for (i in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            val config = UserConfig.getInstance(i)
            if (config.isClientActivated && config.getClientUserId() == userId) {
                return true
            }
        }
        return false
    }

    /**
     * Get the current user ID from the selected account.
     */
    @JvmStatic
    fun getCurrentUserId(): Long {
        return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()
    }
}
