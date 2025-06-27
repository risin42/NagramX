package org.telegram.messenger;

import static org.telegram.ui.LaunchActivity.getLastFragment;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StickerImageView;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.utils.FileUtil;

@RequiresApi(api = Build.VERSION_CODES.R)
public class FilesMigrationService extends Service {

    public static boolean hasOldFolder;
    public static boolean isRunning;
    public static FilesMigrationBottomSheet filesMigrationBottomSheet;
    private int totalFilesCount;
    private int movedFilesCount;
    private static boolean wasShown = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void start() {
        Intent intent = new Intent(ApplicationLoader.applicationContext, FilesMigrationService.class);
        ApplicationLoader.applicationContext.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationsController.checkOtherNotificationsChannel();
        Notification notification = new Notification.Builder(this, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL)
                .setContentTitle(getText(R.string.MigratingFiles))
                .setAutoCancel(false)
                .setSmallIcon(R.drawable.notification)
                .build();

        isRunning = true;
        new Thread() {
            @Override
            public void run() {
                migrateOldFolder();
                AndroidUtilities.runOnUIThread(() -> {
                    isRunning = false;
                    stopForeground(true);
                    stopSelf();
                });
            }
        }.start();
        startForeground(301, notification);

        return super.onStartCommand(intent, flags, startId);
    }

    public void migrateOldFolder() {
        File path = new File(NekoConfig.cachePath.String());
        if (!path.exists() || !path.isDirectory()) {
            return;
        }

        File newPath = ApplicationLoader.applicationContext.getExternalFilesDir(null);
        File telegramPath = new File(newPath, "Telegram");

        List<File> oldDirs = new ArrayList<>();
        oldDirs.add(new File(path, "audios"));
        oldDirs.add(new File(path, "documents"));
        oldDirs.add(new File(path, "images"));
        oldDirs.add(new File(path, "stories"));
        oldDirs.add(new File(path, "videos"));

        List<File> newDirs = new ArrayList<>();
        newDirs.add(new File(telegramPath, "Telegram Audio"));
        newDirs.add(new File(telegramPath, "Telegram Documents"));
        newDirs.add(new File(telegramPath, "Telegram Images"));
        newDirs.add(new File(telegramPath, "Telegram Stories"));
        newDirs.add(new File(telegramPath, "Telegram Video"));

        for (File oldDir : oldDirs) {
            if (oldDir.exists()) {
                totalFilesCount += getFilesCount(oldDir);
            }
        }

        long moveStart = System.currentTimeMillis();

        for (File oldDir : oldDirs) {
            if (oldDir.exists() && oldDir.canRead() && oldDir.canWrite()) {
                moveDirectory(oldDir, newDirs.get(oldDirs.indexOf(oldDir)));
            }
        }

        File oldCacheDir = new File(path, "caches");
        if (oldCacheDir.exists()) {
            FileUtil.deleteDirectory(oldCacheDir);
        }

        long dt = System.currentTimeMillis() - moveStart;

        FileLog.d("move time = " + dt);

        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
        sharedPreferences.edit().putBoolean("migration_to_telegram_storage_finished", true).apply();

        AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(getLastFragment()).createSuccessBulletin(getString(R.string.Done)).show());
    }

    private int getFilesCount(File source) {
        if (!source.exists()) {
            return 0;
        }
        int count = 0;
        File[] fileList = source.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                if (file.isDirectory()) {
                    count += getFilesCount(file);
                } else {
                    count++;
                }
            }
        }
        return count;
    }

    private void moveDirectory(File source, File target) {
        if (!source.exists() || (!target.exists() && !target.mkdir())) {
            return;
        }
        try (Stream<Path> files = Files.list(source.toPath())) {
            files.forEach(path -> {
                File dest = new File(target, path.getFileName().toString());
                if (Files.isDirectory(path)) {
                    moveDirectory(path.toFile(), dest);
                } else {
                    try {
                        Files.move(path, dest.toPath());
                    } catch (Exception e) {
                        FileLog.e(e, false);
                        try {
                            path.toFile().delete();
                        } catch (Exception e1) {
                            FileLog.e(e1);
                        }
                    }
                    movedFilesCount++;
                    updateProgress();
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            source.delete();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    long lastUpdateTime;
    private void updateProgress() {
        long time = System.currentTimeMillis();
        if (time - lastUpdateTime > 20 || movedFilesCount >= totalFilesCount - 1) {
            int currentCount = movedFilesCount;
            AndroidUtilities.runOnUIThread(() -> {
                Notification notification = new Notification.Builder(FilesMigrationService.this, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL)
                        .setContentTitle(getText(R.string.MigratingFiles))
                        .setContentText(String.format("%s/%s", currentCount, totalFilesCount))
                        .setSmallIcon(R.drawable.notification)
                        .setAutoCancel(false)
                        .setProgress(totalFilesCount, currentCount, false)
                        .build();
                NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.notify(301, notification);
            });
        }
    }

    public static void checkBottomSheet(BaseFragment fragment) {
        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean("migration_to_telegram_storage_finished", false) || sharedPreferences.getInt("migration_to_telegram_storage_count", 0) >= 3 || wasShown || filesMigrationBottomSheet != null || isRunning) {
            return;
        }

        File path = new File(NekoConfig.cachePath.String());
        if (!path.exists() || !path.isDirectory()) {
            return;
        }

        File oldDirectory = new File(path, "caches");
        hasOldFolder = oldDirectory.exists();
        if (hasOldFolder) {
            filesMigrationBottomSheet = new FilesMigrationBottomSheet(fragment);
            filesMigrationBottomSheet.show();
            wasShown = true;
            sharedPreferences.edit().putInt("migration_to_telegram_storage_count", sharedPreferences.getInt("migration_to_telegram_storage_count", 0) + 1).apply();
        } else {
            sharedPreferences.edit().putBoolean("migration_to_telegram_storage_finished", true).apply();
        }
    }

    public static class FilesMigrationBottomSheet extends BottomSheet {

        BaseFragment fragment;

        @Override
        protected boolean canDismissWithSwipe() {
            return false;
        }

        @Override
        protected boolean canDismissWithTouchOutside() {
            return false;
        }

        public FilesMigrationBottomSheet(BaseFragment fragment) {
            super(fragment.getParentActivity(), false);
            this.fragment = fragment;
            setCanceledOnTouchOutside(false);
            Context context = fragment.getParentActivity();
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);

            StickerImageView imageView = new StickerImageView(context, currentAccount);
            imageView.setStickerNum(7);
            imageView.getImageReceiver().setAutoRepeat(1);
            linearLayout.addView(imageView, LayoutHelper.createLinear(144, 144, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

            TextView title = new TextView(context);
            title.setGravity(Gravity.START);
            title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTypeface(AndroidUtilities.bold());
            title.setText(LocaleController.getString(R.string.MigrateOldFolderTitle));
            linearLayout.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 30, 21, 0));

            TextView description = new TextView(context);
            description.setGravity(Gravity.START);
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            description.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MigrateOldFolderDescription)));
            linearLayout.addView(description, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 15, 21, 16));


            TextView buttonTextView = new TextView(context);
            buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
            buttonTextView.setGravity(Gravity.CENTER);
            buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            buttonTextView.setTypeface(AndroidUtilities.bold());
            buttonTextView.setText(LocaleController.getString(R.string.MigrateOldFolderButton));

            buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            buttonTextView.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 6));

            linearLayout.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, 0, 16, 15, 16, 16));

            buttonTextView.setOnClickListener(view -> migrateOldFolder());

            ScrollView scrollView = new ScrollView(context);
            scrollView.addView(linearLayout);
            setCustomView(scrollView);
        }

        public void migrateOldFolder() {
            Activity activity = fragment.getParentActivity();
            boolean canRead = Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED && activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED && activity.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED || activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

            if (!canRead) {
                ArrayList<String> permissions = new ArrayList<>();
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
                String[] string = new String[permissions.size()];
                activity.requestPermissions(permissions.toArray(string), 4);
                return;
            }
            start();
            dismiss();
        }

        @Override
        public void dismiss() {
            super.dismiss();
            filesMigrationBottomSheet = null;
        }
    }
}
