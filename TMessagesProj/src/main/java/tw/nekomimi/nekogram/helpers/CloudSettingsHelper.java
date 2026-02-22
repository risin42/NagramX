package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Base64;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TextViewSwitcher;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import tw.nekomimi.nekogram.settings.NekoSettingsActivity;
import tw.nekomimi.nekogram.utils.GsonUtil;

public class CloudSettingsHelper {
    public static final SharedPreferences.OnSharedPreferenceChangeListener listener = (preferences, key) -> CloudSettingsHelper.getInstance().doAutoSync();
    private static final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("nekocloud", Context.MODE_PRIVATE);
    private final SparseArray<Long> cloudSyncedDate = new SparseArray<>();
    private final Handler handler = new Handler();
    private long localSyncedDate = preferences.getLong("updated_at", -1);
    private boolean autoSync = preferences.getBoolean("auto_sync", false);

    private static final String SETTINGS_CHUNKS_COUNT_KEY = "neko_settings";
    private static final String SETTINGS_CHUNK_KEY_PREFIX = "neko_settings_";
    private static final String SETTINGS_UPDATED_AT_KEY = "neko_settings_updated_at";
    private static final String SETTINGS_ENCODING_KEY = "neko_settings_encoding";
    private static final String SETTINGS_ENCODING_GZIP_BASE64_V1 = "gzip_base64_v1";
    private static final int MAX_CHUNK_CHARS = 3000;
    private static final int RESTORE_BATCH_SIZE = 50;

    private final Runnable cloudSyncRunnable = () -> CloudSettingsHelper.getInstance().syncToCloud((success, error) -> {
        if (!success) {
            var global = BulletinFactory.global();
            if (error == null) {
                global.createSimpleBulletin(R.raw.error, getString(R.string.CloudConfigSyncFailed)).show();
            } else {
                global.createSimpleBulletin(R.raw.error, getString(R.string.CloudConfigSyncFailed), error).show();
            }
        }
    });

    public static CloudSettingsHelper getInstance() {
        return InstanceHolder.instance;
    }

    private static String formatDateUntil(long date) {
        try {
            Calendar rightNow = Calendar.getInstance();
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (year == dateYear) {
                return LocaleController.getInstance().getFormatterBannedUntilThisYear().format(new Date(date));
            } else {
                return LocaleController.getInstance().getFormatterBannedUntil().format(new Date(date));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public void showDialog(BaseFragment parentFragment) {
        if (parentFragment == null) {
            return;
        }

        Context context = parentFragment.getParentActivity();
        Theme.ResourcesProvider resourcesProvider = parentFragment.getResourceProvider();
        int selectedAccount = UserConfig.selectedAccount;

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString(R.string.CloudConfig));
        builder.setMessage(AndroidUtilities.replaceTags(getString(R.string.CloudConfigDesc)));
        builder.setTopImage(R.drawable.cloud, Theme.getColor(Theme.key_dialogTopBackground, resourcesProvider));

        TextViewSwitcher syncedDate = new TextViewSwitcher(context);
        syncedDate.setFactory(() -> {
            TextView tv = new TextView(context);
            tv.setGravity(Gravity.START);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            tv.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
            return tv;
        });
        syncedDate.setInAnimation(context, R.anim.alpha_in);
        syncedDate.setOutAnimation(context, R.anim.alpha_out);
        syncedDate.setText(formatSyncedDate(), false);

        ButtonWithCounterView restoreButton = new ButtonWithCounterView(context, false, resourcesProvider).setRound();
        restoreButton.setText(getString(R.string.CloudConfigRestore), false);
        restoreButton.setEnabled(false);
        restoreButton.setClickable(false);

        var storageHelper = getCloudStorageHelper();
        storageHelper.getItem(SETTINGS_UPDATED_AT_KEY, (res, error) -> {
            if (error == null && AndroidUtilities.isNumeric(res)) {
                cloudSyncedDate.put(selectedAccount, Long.parseLong(res));
                restoreButton.setEnabled(true);
                restoreButton.setClickable(true);
            } else {
                cloudSyncedDate.put(selectedAccount, -1L);
                restoreButton.setEnabled(false);
                restoreButton.setClickable(false);
            }
            syncedDate.setText(formatSyncedDate());
        });

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        ButtonWithCounterView buttonTextView = new ButtonWithCounterView(context, true, resourcesProvider).setRound();
        buttonTextView.setText(getString(R.string.CloudConfigSync), false);
        linearLayout.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 0, 16, 0));
        buttonTextView.setOnClickListener(view -> {
            syncedDate.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.CloudConfigSyncing)));
            syncToCloud((success, error) -> {
                syncedDate.setText(formatSyncedDate());
                if (!success) {
                    if (error == null) {
                        BulletinFactory.of(Bulletin.BulletinWindow.make(context), resourcesProvider).createSimpleBulletin(R.raw.error, getString(R.string.CloudConfigSyncFailed)).show();
                    } else {
                        BulletinFactory.of(Bulletin.BulletinWindow.make(context), resourcesProvider).createSimpleBulletin(R.raw.error, getString(R.string.CloudConfigSyncFailed), error).show();
                    }
                }
                boolean hasCloudData = cloudSyncedDate.get(selectedAccount, 0L) > 0;
                restoreButton.setEnabled(hasCloudData);
                restoreButton.setClickable(hasCloudData);
            });
        });

        linearLayout.addView(restoreButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 8, 16, 0));
        restoreButton.setOnClickListener(view -> {
            if (!restoreButton.isEnabled()) return;
            syncedDate.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.CloudConfigSyncing)));
            restoreFromCloud((success, error) -> {
                syncedDate.setText(formatSyncedDate());
                if (!success) {
                    if (error == null) {
                        BulletinFactory.of(Bulletin.BulletinWindow.make(context), resourcesProvider).createSimpleBulletin(R.raw.error, getString(R.string.CloudConfigRestoreFailed)).show();
                    } else {
                        BulletinFactory.of(Bulletin.BulletinWindow.make(context), resourcesProvider).createSimpleBulletin(R.raw.error, getString(R.string.CloudConfigRestoreFailed), error).show();
                    }
                } else {
                    AlertDialog restart = new AlertDialog(context, 0);
                    restart.setTitle(getString(R.string.NagramX));
                    restart.setMessage(getString(R.string.RestartAppToTakeEffect));
                    restart.setPositiveButton(getString(R.string.OK), (__, ___) -> AppRestartHelper.triggerRebirth(context, new Intent(context, LaunchActivity.class)));
                    restart.show();
                }
            });
        });

        ButtonWithCounterView deleteButton = new ButtonWithCounterView(context, false, resourcesProvider).setRound();
        deleteButton.setText(getString(R.string.DeleteCloudBackup), false);
        deleteButton.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
        linearLayout.addView(deleteButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 8, 16, 0));
        deleteButton.setOnClickListener(view -> {
            syncedDate.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.CloudConfigSyncing)));
            deleteCloudBackup((success, error) -> {
                syncedDate.setText(formatSyncedDate());
                if (!success) {
                    if (error == null) {
                        BulletinFactory.of(Bulletin.BulletinWindow.make(context), resourcesProvider).createSimpleBulletin(R.raw.info, getString(R.string.CloudConfigNoBackupToDelete)).show();
                    } else {
                        BulletinFactory.of(Bulletin.BulletinWindow.make(context), resourcesProvider).createSimpleBulletin(R.raw.error, getString(R.string.DeleteCloudBackupFailed), error).show();
                    }
                } else {
                    BulletinFactory.of(Bulletin.BulletinWindow.make(context), resourcesProvider).createSimpleBulletin(R.raw.done, getString(R.string.DeleteCloudBackupSuccess)).show();
                    restoreButton.setEnabled(false);
                    restoreButton.setClickable(false);
                }
            });
        });

        MiniCheckBoxCell autoSyncCheck = new MiniCheckBoxCell(context, 8, resourcesProvider);
        autoSyncCheck.setTextAndValueAndCheck(getString(R.string.CloudConfigAutoSync), getString(R.string.CloudConfigAutoSyncDesc), autoSync);
        autoSyncCheck.setOnClickListener(view13 -> {
            autoSync = !autoSync;
            preferences.edit().putBoolean("auto_sync", autoSync).apply();
            autoSyncCheck.setChecked(autoSync);
        });
        linearLayout.addView(autoSyncCheck, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 8, 8, 0));

        linearLayout.addView(syncedDate, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 8, 16, 0));

        builder.setView(linearLayout);
        parentFragment.showDialog(builder.create());
    }

    public void doAutoSync() {
        if (!autoSync) {
            return;
        }
        handler.removeCallbacks(cloudSyncRunnable);
        handler.postDelayed(cloudSyncRunnable, 1200);
    }

    private void syncToCloud(Utilities.Callback2<Boolean, String> callback) {
        try {
            String settingsJson = NekoSettingsActivity.backupSettingsJson(true, 0);
            String payload = gzipBase64Encode(settingsJson);
            int numChunks = (int) Math.ceil((double) payload.length() / MAX_CHUNK_CHARS);
            syncChunk(payload, 0, numChunks, MAX_CHUNK_CHARS, callback);
        } catch (Exception error) {
            callback.run(false, error.toString());
        }
    }

    private void syncChunk(String setting, int index, int numChunks, int chunkSize, Utilities.Callback2<Boolean, String> callback) {
        if (index >= numChunks) {
            getCloudStorageHelper().setItem(SETTINGS_CHUNKS_COUNT_KEY, String.valueOf(numChunks), (res, error) -> {
                if (error == null) {
                    localSyncedDate = System.currentTimeMillis();
                    cloudSyncedDate.put(UserConfig.selectedAccount, localSyncedDate);
                    getCloudStorageHelper().setItem(SETTINGS_UPDATED_AT_KEY, String.valueOf(localSyncedDate), null);
                    getCloudStorageHelper().setItem(SETTINGS_ENCODING_KEY, SETTINGS_ENCODING_GZIP_BASE64_V1, null);
                    preferences.edit().putLong("updated_at", localSyncedDate).apply();
                    callback.run(true, null);
                } else {
                    callback.run(false, error);
                }
            });
            return;
        }

        int startIndex = index * chunkSize;
        int endIndex = Math.min(startIndex + chunkSize, setting.length());
        String chunk = setting.substring(startIndex, endIndex);
        String storageKey = SETTINGS_CHUNK_KEY_PREFIX + index;

        getCloudStorageHelper().setItem(storageKey, chunk, (res, error) -> {
            if (error != null) {
                callback.run(false, error);
            } else {
                syncChunk(setting, index + 1, numChunks, chunkSize, callback);
            }
        });
    }

    private void restoreFromCloud(Utilities.Callback2<Boolean, String> callback) {
        getCloudStorageHelper().getItems(new String[]{SETTINGS_CHUNKS_COUNT_KEY, SETTINGS_ENCODING_KEY}, (meta, metaError) -> {
            if (metaError != null || meta == null) {
                callback.run(false, metaError);
                return;
            }
            String countStr = meta.get(SETTINGS_CHUNKS_COUNT_KEY);
            if (!AndroidUtilities.isNumeric(countStr)) {
                callback.run(false, null);
                return;
            }
            int numChunks = 0;
            try {
                if (countStr != null) {
                    numChunks = Integer.parseInt(countStr);
                }
            } catch (Exception e) {
                FileLog.e(e);
                callback.run(false, e.getLocalizedMessage());
                return;
            }
            String encoding = meta.get(SETTINGS_ENCODING_KEY);
            fetchChunksFromCloud(numChunks, 0, new StringBuilder(), (payload, chunksError) -> {
                if (chunksError != null) {
                    callback.run(false, chunksError);
                    return;
                }
                try {
                    String json;
                    if (SETTINGS_ENCODING_GZIP_BASE64_V1.equals(encoding)) {
                        json = gzipBase64Decode(payload);
                    } else {
                        json = payload;
                    }
                    NekoSettingsActivity.importSettings(GsonUtil.toJsonObject(json));
                    localSyncedDate = System.currentTimeMillis();
                    preferences.edit().putLong("updated_at", localSyncedDate).apply();
                    callback.run(true, null);
                } catch (Exception e) {
                    FileLog.e(e);
                    callback.run(false, e.getLocalizedMessage());
                }
            });
        });
    }

    private void fetchChunksFromCloud(int numChunks, int offset, StringBuilder sb, Utilities.Callback2<String, String> callback) {
        if (offset >= numChunks) {
            callback.run(sb.toString(), null);
            return;
        }
        int end = Math.min(offset + RESTORE_BATCH_SIZE, numChunks);
        String[] keys = new String[end - offset];
        for (int i = offset; i < end; i++) {
            keys[i - offset] = SETTINGS_CHUNK_KEY_PREFIX + i;
        }
        getCloudStorageHelper().getItems(keys, (res, error) -> {
            if (error != null || res == null) {
                callback.run(null, error);
                return;
            }
            for (int i = offset; i < end; i++) {
                String chunk = res.get(SETTINGS_CHUNK_KEY_PREFIX + i);
                if (chunk == null) {
                    callback.run(null, "Chunk " + i + " is missing");
                    return;
                }
                sb.append(chunk);
            }
            fetchChunksFromCloud(numChunks, end, sb, callback);
        });
    }

    private static String gzipBase64Encode(String input) throws Exception {
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(inputBytes);
        }
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    @SuppressWarnings("StringOperationCanBeSimplified") // API 33
    private static String gzipBase64Decode(String input) throws Exception {
        byte[] compressed = Base64.decode(input, Base64.DEFAULT);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = gzip.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    private void deleteCloudBackup(Utilities.Callback2<Boolean, String> callback) {
        getCloudStorageHelper().getKeys((keys, error) -> {
            if (error != null) {
                callback.run(false, error);
                return;
            }
            if (keys == null || keys.length == 0) {
                callback.run(false, null);
                return;
            }

            ArrayList<String> nekoKeys = new ArrayList<>();
            for (String key : keys) {
                if (key.startsWith("neko_settings")) {
                    nekoKeys.add(key);
                }
            }

            if (nekoKeys.isEmpty()) {
                callback.run(false, null);
                return;
            }

            getCloudStorageHelper().removeItems(nekoKeys.toArray(new String[0]), (res_, error_) -> {
                if (error_ == null) {
                    cloudSyncedDate.put(UserConfig.selectedAccount, -1L);
                    callback.run(true, null);
                } else {
                    callback.run(false, error_);
                }
            });
        });
    }

    private CloudStorageHelper getCloudStorageHelper() {
        return CloudStorageHelper.getInstance(UserConfig.selectedAccount);
    }

    private String formatSyncedDate() {
        return LocaleController.formatString(R.string.CloudConfigSyncDate, localSyncedDate > 0 ? formatDateUntil(localSyncedDate) : getString(R.string.CloudConfigSyncDateNever), cloudSyncedDate.get(UserConfig.selectedAccount, 0L) > 0 ? formatDateUntil(cloudSyncedDate.get(UserConfig.selectedAccount, 0L)) : getString(R.string.CloudConfigSyncDateNever));
    }

    private static final class InstanceHolder {
        private static final CloudSettingsHelper instance = new CloudSettingsHelper();
    }

    @SuppressLint("ViewConstructor")
    private static class MiniCheckBoxCell extends FrameLayout {

        private final TextView textView;
        private final TextView valueTextView;
        private final CheckBoxSquare checkBox;

        public MiniCheckBoxCell(Context context, int padding, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            ScaleStateListAnimator.apply(this, .02f, 1.2f);

            setForeground(Theme.createRadSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), .10f), 16, 16));

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            valueTextView = new TextView(context);
            valueTextView.setTextColor(Theme.getColor(Theme.key_dialogIcon, resourcesProvider));
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            valueTextView.setEllipsize(TextUtils.TruncateAt.END);
            linearLayout.addView(valueTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 22 + padding : padding, 4, LocaleController.isRTL ? padding : 22 + padding, 4));

            checkBox = new CheckBoxSquare(context, true, resourcesProvider);
            addView(checkBox, LayoutHelper.createFrame(18, 18, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? padding : 4, 0, LocaleController.isRTL ? 4 : padding, 0));
        }

        public void setTextAndValueAndCheck(String text, String value, boolean checked) {
            textView.setText(text);
            valueTextView.setText(value);
            checkBox.setChecked(checked, false);
        }

        public boolean isChecked() {
            return checkBox.isChecked();
        }

        public void setChecked(boolean checked) {
            checkBox.setChecked(checked, true);
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.CheckBox");
            info.setCheckable(true);
            info.setChecked(checkBox.isChecked());
            StringBuilder sb = new StringBuilder();
            sb.append(textView.getText());
            if (!TextUtils.isEmpty(valueTextView.getText())) {
                sb.append('\n');
                sb.append(valueTextView.getText());
            }
            info.setContentDescription(sb);
        }
    }
}
