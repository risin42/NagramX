package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;

public class ChatNameHelper {
    public static final String chatNameOverridePrefix = "chatNameOverride_";
    public static final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("nkmrcfg", Context.MODE_PRIVATE);

    public static String getChatNameOverride(long chatId) {
        return preferences.getString(chatNameOverridePrefix + chatId, null);
    }

    public static void setChatNameOverride(long chatId, String name) {
        preferences.edit().putString(chatNameOverridePrefix + chatId, name).apply();
        MessagesController.overrideNameCache.put(chatId, name);
    }

    public static void clearChatNameOverride(long chatId) {
        preferences.edit().remove(chatNameOverridePrefix + chatId).apply();
        MessagesController.overrideNameCache.remove(chatId);
    }

    public static void clearAllChatNameOverrides() {
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