package tw.nekomimi.nekogram;

import android.content.SharedPreferences;

public class DialogConfig {
    public static final String customForumTabPrefix = "customForumTabs_";
    private static final SharedPreferences preferences = NekoConfig.preferences;

    public static String getCustomForumTabsKey(long dialogId) {
        return customForumTabPrefix + dialogId;
    }

    public static boolean isCustomForumTabsEnable(long dialogId) {
        return preferences.getBoolean(getCustomForumTabsKey(dialogId), false);
    }

    public static boolean hasCustomForumTabsConfig(long dialogId) {
        return preferences.contains(getCustomForumTabsKey(dialogId));
    }

    public static void setCustomForumTabsEnable(long dialogId, boolean enable) {
        preferences.edit().putBoolean(getCustomForumTabsKey(dialogId), enable).apply();
    }

    public static void removeCustomForumTabsConfig(long dialogId) {
        preferences.edit().remove(getCustomForumTabsKey(dialogId)).apply();
    }
}