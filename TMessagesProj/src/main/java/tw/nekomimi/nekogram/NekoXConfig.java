package tw.nekomimi.nekogram;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import xyz.nextalone.nagram.NaConfig;

public class NekoXConfig {

    public static String FAQ_URL = "https://github.com/NextAlone/Nagram#faq";

    public static final int TITLE_TYPE_TEXT = 0;
    public static final int TITLE_TYPE_ICON = 1;
    public static final int TITLE_TYPE_MIX = 2;

    public static SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("nekox_config", Context.MODE_PRIVATE);

    public static boolean disableFlagSecure = NaConfig.INSTANCE.getDisableFlagSecure().Bool();

    public static int customApi = preferences.getInt("custom_api", 0);
    public static int customAppId = preferences.getInt("custom_app_id", 0);
    public static String customAppHash = preferences.getString("custom_app_hash", "");

    public static int currentAppId() {
        return switch (customApi) {
            case 0 -> BuildConfig.APP_ID;
            case 1 -> BuildVars.OFFICAL_APP_ID;
            case 2 -> BuildVars.TGX_APP_ID;
            default -> customAppId;
        };
    }
    
    public static String currentAppHash() {
        return switch (customApi) {
            case 0 -> BuildConfig.APP_HASH;
            case 1 -> BuildVars.OFFICAL_APP_HASH;
            case 2 -> BuildVars.TGX_APP_HASH;
            default -> customAppHash;
        };
    }
    
    public static void saveCustomApi() {
        preferences.edit()
                .putInt("custom_api", customApi)
                .putInt("custom_app_id", customAppId)
                .putString("custom_app_hash", customAppHash)
                .apply();
    }

    public static String formatLang(String name) {
        if (name == null || name.isEmpty()) {
            return getString(R.string.Default);
        } else {
            String[] parts = name.split("-");
            Locale locale;
            if (parts.length > 1) {
                locale = new Locale(parts[0], parts[1]);
            } else {
                locale = new Locale(parts[0]);
            }
            return locale.getDisplayName(LocaleController.getInstance().currentLocale);
        }
    }

    public static int getNotificationColor() {
        int color = 0;
        Configuration configuration = ApplicationLoader.applicationContext.getResources().getConfiguration();
        boolean isDark = (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (isDark) {
            color = 0xffffffff;
        } else {
            if (Theme.getActiveTheme().hasAccentColors()) {
                color = Theme.getActiveTheme().getAccentColor(Theme.getActiveTheme().currentAccentId);
            }
            if (Theme.getActiveTheme().isDark() || color == 0) {
                color = Theme.getColor(Theme.key_actionBarDefault);
            }
            // too bright
            if (AndroidUtilities.computePerceivedBrightness(color) >= 0.721f) {
                color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader) | 0xff000000;
            }
        }
        return color;
    }

    public static void setChannelAlias(long channelID, String name) {
        preferences.edit().putString(NekoConfig.channelAliasPrefix + channelID, name).apply();
    }

    public static void emptyChannelAlias(long channelID) {
        preferences.edit().remove(NekoConfig.channelAliasPrefix + channelID).apply();
    }

    public static String getChannelAlias(long channelID) {
        return preferences.getString(NekoConfig.channelAliasPrefix + channelID, null);
    }
}
