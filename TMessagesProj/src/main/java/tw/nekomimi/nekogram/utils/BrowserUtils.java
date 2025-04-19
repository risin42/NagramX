/*
 * This is the source code of OctoGram for Android
 * It is licensed under GNU GPL v2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright OctoGram, 2023-2025.
 */

package tw.nekomimi.nekogram.utils;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.LaunchActivity;

public class BrowserUtils {
    public static void openBrowserHome(OnHomePageOpened callback) {
        if (SharedConfig.inappBrowser) {
            if (callback != null) {
                callback.onHomePageOpened();
            }

            Browser.openUrl(LaunchActivity.instance, getDefaultBrowserHome());
            return;
        }

        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(LaunchActivity.instance);
        alertBuilder.setTitle(getString(R.string.TgBrowserOpenFail));
        alertBuilder.setMessage(getString(R.string.TgBrowserOpenFail_Desc));
        alertBuilder.setPositiveButton(getString(R.string.OK), null);
        alertBuilder.show();
    }

    public static void openBrowserHome() {
        openBrowserHome(null);
    }

    public static String getDefaultBrowserHome() {
        int engineType = SharedConfig.searchEngineType + 1;
        String searchUrl = getString("SearchEngine" + engineType + "SearchURL");
        String host = AndroidUtilities.getHostAuthority(searchUrl);
        return "https://" + host;
    }

    public interface OnHomePageOpened {
        void onHomePageOpened();
    }
}
