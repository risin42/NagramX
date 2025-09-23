package tw.nekomimi.nekogram.utils;

import static org.telegram.messenger.LocaleController.getString;

import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import tw.nekomimi.nekogram.NekoConfig;
import xyz.nextalone.nagram.NaConfig;

public class AndroidUtil {

    public static int getNavBarColor(int color) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            return color;
        }
        BaseFragment fragment = LaunchActivity.getLastFragment();
        Theme.ResourcesProvider resourcesProvider = fragment != null ? fragment.getResourceProvider() : null;
        return Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
    }

    public static int getNavBarColor(int color, Theme.ResourcesProvider resourcesProvider) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            return color;
        }
        return Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
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

    private static final Pattern FORMAT_CONTROL_CHARS_PATTERN = Pattern.compile("\\p{Cf}");

    public static CharSequence sanitizeString(CharSequence input) {
        if (TextUtils.isEmpty(input)) {
            return input;
        }
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
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            NaConfig.INSTANCE.getPushServiceType().setConfigInt(0);
            NaConfig.INSTANCE.getPushServiceTypeInAppDialog().setConfigBool(true);
            SharedPreferences.Editor editor = MessagesController.getGlobalNotificationsSettings().edit();
            editor.putBoolean("pushService", true).apply();
            editor.putBoolean("pushConnection", true).apply();
        }
    }

    public static void disableHapticFeedback(View view) {
        if (view != null) {
            view.setHapticFeedbackEnabled(false);
            if (view instanceof ViewGroup group) {
                for (int i = 0; i < group.getChildCount(); i++) {
                    disableHapticFeedback(group.getChildAt(i));
                }
            }
        }
    }

    private static final Set<String> WIN32_EXECUTABLE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "cmd", "bat", "com", "exe", "lnk", "msi", "ps1", "reg", "vb", "vbe", "vbs", "vbscript"
    ));
    private static final Set<String> ARCHIVE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "apk", "zip", "7z", "tar", "gz", "zst", "iso", "xz", "lha", "lzh"
    ));

    public static boolean isAutoDownloadDisabledFor(String documentName) {
        if (TextUtils.isEmpty(documentName)) {
            return false;
        }
        int dotIndex = documentName.lastIndexOf('.');
        if (dotIndex < 0) {
            return false;
        }
        String extension = documentName.substring(dotIndex + 1).toLowerCase();

        boolean isExecutable = NekoConfig.disableAutoDownloadingWin32Executable.Bool() &&
                WIN32_EXECUTABLE_EXTENSIONS.contains(extension);

        boolean isArchive = NekoConfig.disableAutoDownloadingArchive.Bool() &&
                ARCHIVE_EXTENSIONS.contains(extension);

        return isExecutable || isArchive;
    }

    public static void showErrorDialog(Exception e) {
        var fragment = LaunchActivity.getSafeLastFragment();
        var message = e.getLocalizedMessage();
        if (!BulletinFactory.canShowBulletin(fragment) || message == null) {
            return;
        }
        if (message.length() > 45) {
            AlertsCreator.showSimpleAlert(fragment, getString(R.string.ErrorOccurred), e.getMessage());
        } else {
            BulletinFactory.of(fragment).createErrorBulletin(message).show();
        }
    }
}
