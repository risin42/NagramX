package tw.nekomimi.nekogram.utils;

import static android.view.Display.DEFAULT_DISPLAY;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.MessageHelper;
import xyz.nextalone.nagram.NaConfig;

public class AndroidUtil {

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
            return Color.argb(255, 234, 234, 30);
        } else if (diff > -30 * 60) {
            return Color.argb(255, 234, 132, 30);
        } else if (diff > -60 * 60) {
            return Color.argb(255, 234, 30, 30);
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
        } else {
            SharedPreferences.Editor editor = MessagesController.getGlobalNotificationsSettings().edit();
            editor.putBoolean("pushService", true).apply();
            editor.putBoolean("pushConnection", true).apply();
            NaConfig.INSTANCE.getPushServiceType().setConfigInt(0);
            NaConfig.INSTANCE.getPushServiceTypeInAppDialog().setConfigBool(true);
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
        showErrorDialog(e.getLocalizedMessage());
    }

    public static void showErrorDialog(String message) {
        var fragment = LaunchActivity.getSafeLastFragment();
        if (!BulletinFactory.canShowBulletin(fragment) || message == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (message.length() > 45) {
                AlertsCreator.showSimpleAlert(fragment, getString(R.string.ErrorOccurred), message);
            } else {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, message).show();
            }
        });
    }

    public static void toggleLogs() {
        BuildVars.LOGS_ENABLED = BuildVars.DEBUG_VERSION = !BuildVars.LOGS_ENABLED;
        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
        sharedPreferences.edit().putBoolean("logsEnabled", BuildVars.LOGS_ENABLED).apply();
    }

    @SuppressWarnings("ConstantValue")
    public static String getVersionText() {
        String versionName = "release".equals(BuildConfig.BUILD_TYPE) && !BuildVars.LOGS_ENABLED ? BuildConfig.VERSION_NAME.split("-")[0] : BuildConfig.VERSION_NAME;
        return "Nagram X v" + versionName + "(" + BuildConfig.VERSION_CODE + ") " + Build.SUPPORTED_ABIS[0].toLowerCase(Locale.ROOT) + " " + BuildConfig.BUILD_TYPE + (BuildVars.LOGS_ENABLED ? " " + BuildConfig.BUILD_TIMESTAMP : "");
    }

    /*<!-- Controls the navigation bar interaction mode:
         0: 3 button mode (back, home, overview buttons)
         1: 2 button mode (back, home buttons + swipe up for overview)
         2: gestures only for back, home and overview -->
     */
    public static boolean isGestureNavigation(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }
        try {
            int mode = Settings.Secure.getInt(context.getContentResolver(), "navigation_mode", 0);
            return mode == 2;
        } catch (Throwable ignore) {
            try {
                return isEdgeToEdgeEnabled(context) == 2;
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    public static int isEdgeToEdgeEnabled(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return 0;
        }
        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier("config_navBarInteractionMode", "integer", "android");
        if (resourceId > 0) {
            return resources.getInteger(resourceId);
        }
        return 0;
    }

    public static boolean openForView(MessageObject message, Activity activity, Theme.ResourcesProvider resourcesProvider) {
        File f = null;
        String path = MessageHelper.getPathToMessage(message);
        if (!TextUtils.isEmpty(path)) {
            f = new File(path);
        }
        if (f == null || !f.exists()) {
            return false;
        }
        String mimeType = message.type == MessageObject.TYPE_FILE || message.type == MessageObject.TYPE_TEXT ? message.getMimeType() : null;
        return AndroidUtilities.openForView(f, message.getFileName(), mimeType, activity, resourcesProvider, false);
    }

    public static void performHapticFeedback() {
        if (!NekoConfig.disableVibration.Bool()) {
            try {
                Optional.ofNullable(LaunchActivity.getSafeLastFragment())
                        .ifPresent(fragment ->
                                fragment.getFragmentView()
                                        .performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                                                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                        );
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean isScreenHDR() {
        try {
            DisplayManager displayManager = (DisplayManager) ApplicationLoader.applicationContext.getSystemService(Context.DISPLAY_SERVICE);
            Display display = (displayManager != null) ? displayManager.getDisplay(DEFAULT_DISPLAY) : null;
            return display != null && display.isHdr();
        } catch (Throwable ignore) {
            return false;
        }
    }

    @RequiresApi(34)
    public static boolean hasGainmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        return bitmap.hasGainmap();
    }
}
