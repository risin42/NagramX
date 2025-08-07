package tw.nekomimi.nekogram.utils;

import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.util.regex.Pattern;

import xyz.nextalone.nagram.NaConfig;

public class AndroidUtil {

    public static int getNavBarColor(int color) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            return color;
        }
        return Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    public static long getDirectorySize(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        long size = 0;
        try {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File f : files) {
                        size += getDirectorySize(f);
                    }
                }
            } else {
                size += file.length();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return size;
    }

    public static int getOnlineColor(TLRPC.User user, Theme.ResourcesProvider resourcesProvider) {
        if (!NaConfig.INSTANCE.getShowOnlineStatus().Bool()) {
            return 0;
        }
        if (user == null || user.status == null || user.bot || user.self) {
            return 0;
        }
        if (user.status.expires <= 0 && MessagesController.getInstance(UserConfig.selectedAccount).onlinePrivacy.containsKey(user.id)) {
            return Theme.getColor(Theme.key_chats_onlineCircle, resourcesProvider);
        }
        final int diff = user.status.expires - ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime();
        if (diff > 0) {
            return Theme.getColor(Theme.key_chats_onlineCircle, resourcesProvider);
        } else if (diff > -15 * 60) {
            return android.graphics.Color.argb(255, 234, 234, 30);
        } else if (diff > -30 * 60) {
            return android.graphics.Color.argb(255, 234, 132, 30);
        } else if (diff > -60 * 60) {
            return android.graphics.Color.argb(255, 234, 30, 30);
        }
        return 0;
    }

    public static CharSequence sanitizeString(CharSequence input) {
        if (TextUtils.isEmpty(input)) {
            return input;
        }
        final Pattern FORMAT_CONTROL_CHARS_PATTERN = Pattern.compile("\\p{Cf}");
        return FORMAT_CONTROL_CHARS_PATTERN.matcher(input).replaceAll("");
    }

    public static void showInputError(View view) {
        AndroidUtilities.shakeViewSpring(view, -6);
        BotWebViewVibrationEffect.APP_ERROR.vibrate();
    }

    public static void setPushService(boolean fcm) {
        if (fcm) {
            NaConfig.INSTANCE.getPushServiceType().setConfigInt(1);
            NaConfig.INSTANCE.getPushServiceTypeInAppDialog().setConfigBool(false);
        } else {
            NaConfig.INSTANCE.getPushServiceType().setConfigInt(0);
            NaConfig.INSTANCE.getPushServiceTypeInAppDialog().setConfigBool(true);
            SharedPreferences.Editor editor = MessagesController.getGlobalNotificationsSettings().edit();
            editor.putBoolean("pushService", true).apply();
            editor.putBoolean("pushConnection", true).apply();
        }
    }

}
