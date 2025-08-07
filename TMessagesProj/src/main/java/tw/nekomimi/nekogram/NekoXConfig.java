package tw.nekomimi.nekogram;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import kotlin.Unit;
import tw.nekomimi.nekogram.helpers.AppRestartHelper;
import tw.nekomimi.nekogram.ui.BottomBuilder;
import tw.nekomimi.nekogram.utils.AndroidUtil;
import xyz.nextalone.nagram.NaConfig;

public class NekoXConfig {

    public static final int TITLE_TYPE_TEXT = 0;
    public static final int TITLE_TYPE_ICON = 1;
    public static final int TITLE_TYPE_MIX = 2;

    public static final int API_TYPE_DEFAULT = 0;
    public static final int API_TYPE_CUSTOM = 3;

    public static SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("nekox_config", Context.MODE_PRIVATE);

    public static boolean disableFlagSecure = NaConfig.INSTANCE.getDisableFlagSecure().Bool();

    public static int customApi = preferences.getInt("custom_api", 0);
    public static int customAppId = preferences.getInt("custom_app_id", 0);
    public static String customAppHash = preferences.getString("custom_app_hash", "");

    public static int currentAppId() {
        return switch (customApi) {
            case API_TYPE_CUSTOM -> customAppId;
            default -> BuildConfig.APP_ID;
        };
    }

    public static String currentAppHash() {
        return switch (customApi) {
            case API_TYPE_CUSTOM -> customAppHash;
            default -> BuildConfig.APP_HASH;
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

    public static void showCustomApiBottomSheet(BaseFragment fragment) {
        final Context context = fragment.getContext();
        if (context == null) return;

        AtomicInteger useApiType = new AtomicInteger(-1);
        BottomBuilder builder = new BottomBuilder(context);
        EditText[] inputs = new EditText[2];

        builder.addTitle(getString(R.string.CustomApi), true, getString(R.string.UseCustomApiNotice));

        builder.addRadioItem(getString(R.string.CustomApiNo), NekoXConfig.customApi != API_TYPE_CUSTOM, (cell) -> {
            useApiType.set(API_TYPE_DEFAULT);
            builder.doRadioCheck(cell);
            for (EditText input : inputs) {
                input.setVisibility(View.GONE);
            }
            return Unit.INSTANCE;
        });

        builder.addRadioItem(getString(R.string.CustomApiInput), NekoXConfig.customApi == API_TYPE_CUSTOM, (cell) -> {
            useApiType.set(API_TYPE_CUSTOM);
            builder.doRadioCheck(cell);
            for (EditText input : inputs) {
                input.setVisibility(View.VISIBLE);
            }
            return Unit.INSTANCE;
        });

        inputs[0] = builder.addEditText("App ID");
        inputs[0].setInputType(InputType.TYPE_CLASS_NUMBER);
        if (NekoXConfig.customAppId != 0) {
            inputs[0].setText(String.valueOf(NekoXConfig.customAppId));
        }

        inputs[1] = builder.addEditText("App Hash");
        if (!TextUtils.isEmpty(NekoXConfig.customAppHash)) {
            inputs[1].setText(NekoXConfig.customAppHash);
        }

        if (NekoXConfig.customApi != API_TYPE_CUSTOM) {
            for (EditText input : inputs) {
                input.setVisibility(View.GONE);
            }
        }

        builder.addCancelButton();
        builder.addButton(getString(R.string.Set), true, (it) -> {
            int targetType = useApiType.get();
            if (targetType == -1) {
                targetType = NekoXConfig.customApi == API_TYPE_CUSTOM ? API_TYPE_CUSTOM : API_TYPE_DEFAULT;
            }

            if (targetType == API_TYPE_CUSTOM) {
                String appIdStr = inputs[0].getText().toString().trim();
                String appHashStr = inputs[1].getText().toString().trim();

                boolean isAppIdEmpty = appIdStr.isEmpty();
                boolean isAppHashEmpty = appHashStr.isEmpty();

                if (isAppIdEmpty && isAppHashEmpty) {
                    AndroidUtil.setPushService(true);
                    resetCustomApi();
                    saveCustomApiAndRestart(context);
                    return Unit.INSTANCE;
                }

                if (isAppIdEmpty) {
                    AndroidUtil.showInputError(inputs[0]);
                    return Unit.INSTANCE;
                }
                try {
                    if (Integer.parseInt(appIdStr) == 0) {
                        AndroidUtil.showInputError(inputs[0]);
                        return Unit.INSTANCE;
                    }
                } catch (NumberFormatException e) {
                    AndroidUtil.showInputError(inputs[0]);
                    FileLog.e(e);
                    return Unit.INSTANCE;
                }

                if (isAppHashEmpty || appHashStr.length() != 32) {
                    AndroidUtil.showInputError(inputs[1]);
                    return Unit.INSTANCE;
                }

                NekoXConfig.customApi = API_TYPE_CUSTOM;
                NekoXConfig.customAppId = Integer.parseInt(appIdStr);
                NekoXConfig.customAppHash = appHashStr;

                AndroidUtil.setPushService(false);
            } else {
                AndroidUtil.setPushService(true);
                resetCustomApi();
            }

            saveCustomApiAndRestart(context);
            return Unit.INSTANCE;
        });
        builder.show();
    }

    private static void resetCustomApi() {
        NekoXConfig.customApi = API_TYPE_DEFAULT;
        NekoXConfig.customAppId = 0;
        NekoXConfig.customAppHash = "";
    }

    private static void saveCustomApiAndRestart(Context context) {
        NekoXConfig.saveCustomApi();
        AlertDialog restart = new AlertDialog(context, 0);
        restart.setTitle(getString(R.string.NagramX));
        restart.setMessage(getString(R.string.RestartAppToTakeEffect));
        restart.setPositiveButton(getString(R.string.OK), (__, ___) -> {
            AppRestartHelper.triggerRebirth(context, new Intent(context, LaunchActivity.class));
        });
        restart.show();
    }

}
