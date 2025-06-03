package tw.nekomimi.nekogram.utils;

import android.os.Build;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;

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
}
