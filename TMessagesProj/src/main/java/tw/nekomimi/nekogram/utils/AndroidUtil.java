package tw.nekomimi.nekogram.utils;

import android.os.Build;

import org.telegram.ui.ActionBar.Theme;

public class AndroidUtil {
    public static int getNavBarColor(int color) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            return color;
        }
        return Theme.getColor(Theme.key_windowBackgroundWhite);
    }
}
