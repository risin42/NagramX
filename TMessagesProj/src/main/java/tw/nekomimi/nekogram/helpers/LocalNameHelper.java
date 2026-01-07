package tw.nekomimi.nekogram.helpers;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

import tw.nekomimi.nekogram.NekoConfig;

public class LocalNameHelper {
    public static final String chatNameOverridePrefix = "chatNameOverride_";
    public static final String userNameOverridePrefix = "userNameOverride_";

    private static SharedPreferences getPreferences() {
        return NekoConfig.getPreferences();
    }

    public static String getChatNameOverride(long chatId) {
        return getPreferences().getString(chatNameOverridePrefix + chatId, null);
    }

    public static void setChatNameOverride(long chatId, String name) {
        getPreferences().edit().putString(chatNameOverridePrefix + chatId, name).apply();
        MessagesController.chatOverrideNameCache.put(chatId, name);
    }

    public static void clearChatNameOverride(long chatId) {
        getPreferences().edit().remove(chatNameOverridePrefix + chatId).apply();
        MessagesController.chatOverrideNameCache.remove(chatId);
    }

    public static String getUserNameOverride(long userId) {
        return getPreferences().getString(userNameOverridePrefix + userId, null);
    }

    public static void setUserNameOverride(long userId, String name) {
        getPreferences().edit().putString(userNameOverridePrefix + userId, name).apply();
        MessagesController.userOverrideNameCache.put(userId, name);
    }

    public static void clearUserNameOverride(long userId) {
        getPreferences().edit().remove(userNameOverridePrefix + userId).apply();
        MessagesController.userOverrideNameCache.remove(userId);
    }

    public static void clearAllLocalNameOverrides() {
        SharedPreferences preferences = getPreferences();
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(chatNameOverridePrefix)) {
                editor.remove(key);
            }
            if (key.startsWith(userNameOverridePrefix)) {
                editor.remove(key);
            }
        }
        editor.apply();
        MessagesController.chatOverrideNameCache.clear();
        MessagesController.userOverrideNameCache.clear();
    }
}
