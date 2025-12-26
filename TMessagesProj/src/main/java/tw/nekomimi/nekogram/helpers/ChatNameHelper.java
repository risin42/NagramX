package tw.nekomimi.nekogram.helpers;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

import tw.nekomimi.nekogram.NekoConfig;

public class ChatNameHelper {
    public static final String chatNameOverridePrefix = "chatNameOverride_";

    private static SharedPreferences getPreferences() {
        return NekoConfig.getPreferences();
    }

    public static String getChatNameOverride(long chatId) {
        return getPreferences().getString(chatNameOverridePrefix + chatId, null);
    }

    public static void setChatNameOverride(long chatId, String name) {
        getPreferences().edit().putString(chatNameOverridePrefix + chatId, name).apply();
        MessagesController.overrideNameCache.put(chatId, name);
    }

    public static void clearChatNameOverride(long chatId) {
        getPreferences().edit().remove(chatNameOverridePrefix + chatId).apply();
        MessagesController.overrideNameCache.remove(chatId);
    }

    public static void clearAllChatNameOverrides() {
        SharedPreferences preferences = getPreferences();
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(chatNameOverridePrefix)) {
                editor.remove(key);
            }
        }
        editor.apply();
        MessagesController.overrideNameCache.clear();
    }
}