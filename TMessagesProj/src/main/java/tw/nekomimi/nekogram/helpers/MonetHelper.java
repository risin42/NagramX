package tw.nekomimi.nekogram.helpers;

import static org.telegram.ui.Components.Switch.SWITCH_STYLE_MD3;

import android.graphics.Color;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.utilities.Blend;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;

import xyz.nextalone.nagram.NaConfig;

@RequiresApi(api = Build.VERSION_CODES.S)
public class MonetHelper {
    private static final float DARK_NAME_SOFTEN_RATIO = 0.22f;
    private static final HashMap<String, Integer> ids = new HashMap<>() {{
        put("a1_0", android.R.color.system_accent1_0);
        put("a1_10", android.R.color.system_accent1_10);
        put("a1_50", android.R.color.system_accent1_50);
        put("a1_100", android.R.color.system_accent1_100);
        put("a1_200", android.R.color.system_accent1_200);
        put("a1_300", android.R.color.system_accent1_300);
        put("a1_400", android.R.color.system_accent1_400);
        put("a1_500", android.R.color.system_accent1_500);
        put("a1_600", android.R.color.system_accent1_600);
        put("a1_700", android.R.color.system_accent1_700);
        put("a1_800", android.R.color.system_accent1_800);
        put("a1_900", android.R.color.system_accent1_900);
        put("a1_1000", android.R.color.system_accent1_1000);
        put("a2_0", android.R.color.system_accent2_0);
        put("a2_10", android.R.color.system_accent2_10);
        put("a2_50", android.R.color.system_accent2_50);
        put("a2_100", android.R.color.system_accent2_100);
        put("a2_200", android.R.color.system_accent2_200);
        put("a2_300", android.R.color.system_accent2_300);
        put("a2_400", android.R.color.system_accent2_400);
        put("a2_500", android.R.color.system_accent2_500);
        put("a2_600", android.R.color.system_accent2_600);
        put("a2_700", android.R.color.system_accent2_700);
        put("a2_800", android.R.color.system_accent2_800);
        put("a2_900", android.R.color.system_accent2_900);
        put("a2_1000", android.R.color.system_accent2_1000);
        put("a3_0", android.R.color.system_accent3_0);
        put("a3_10", android.R.color.system_accent3_10);
        put("a3_50", android.R.color.system_accent3_50);
        put("a3_100", android.R.color.system_accent3_100);
        put("a3_200", android.R.color.system_accent3_200);
        put("a3_300", android.R.color.system_accent3_300);
        put("a3_400", android.R.color.system_accent3_400);
        put("a3_500", android.R.color.system_accent3_500);
        put("a3_600", android.R.color.system_accent3_600);
        put("a3_700", android.R.color.system_accent3_700);
        put("a3_800", android.R.color.system_accent3_800);
        put("a3_900", android.R.color.system_accent3_900);
        put("a3_1000", android.R.color.system_accent3_1000);
        put("n1_0", android.R.color.system_neutral1_0);
        put("n1_10", android.R.color.system_neutral1_10);
        put("n1_50", android.R.color.system_neutral1_50);
        put("n1_100", android.R.color.system_neutral1_100);
        put("n1_200", android.R.color.system_neutral1_200);
        put("n1_300", android.R.color.system_neutral1_300);
        put("n1_400", android.R.color.system_neutral1_400);
        put("n1_500", android.R.color.system_neutral1_500);
        put("n1_600", android.R.color.system_neutral1_600);
        put("n1_700", android.R.color.system_neutral1_700);
        put("n1_800", android.R.color.system_neutral1_800);
        put("n1_900", android.R.color.system_neutral1_900);
        put("n1_1000", android.R.color.system_neutral1_1000);
        put("n2_0", android.R.color.system_neutral2_0);
        put("n2_10", android.R.color.system_neutral2_10);
        put("n2_50", android.R.color.system_neutral2_50);
        put("n2_100", android.R.color.system_neutral2_100);
        put("n2_200", android.R.color.system_neutral2_200);
        put("n2_300", android.R.color.system_neutral2_300);
        put("n2_400", android.R.color.system_neutral2_400);
        put("n2_500", android.R.color.system_neutral2_500);
        put("n2_600", android.R.color.system_neutral2_600);
        put("n2_700", android.R.color.system_neutral2_700);
        put("n2_800", android.R.color.system_neutral2_800);
        put("n2_900", android.R.color.system_neutral2_900);
        put("n2_1000", android.R.color.system_neutral2_1000);
        put("monetRedLight", R.color.monetRedLight);
        put("monetRedDark", R.color.monetRedDark);
        put("monetRedCall", R.color.monetRedCall);
        put("monetGreenCall", R.color.monetGreenCall);
    }};
    private static final HashMap<String, Integer> avatarBaseColors = new HashMap<>() {{
        put("monetAvatarRed", 0xffFF845E);
        put("monetAvatarOrange", 0xffFEBB5B);
        put("monetAvatarViolet", 0xffB694F9);
        put("monetAvatarGreen", 0xff9AD164);
        put("monetAvatarCyan", 0xff5BCBE3);
        put("monetAvatarBlue", 0xff5CAFFA);
        put("monetAvatarPink", 0xffFF8AAC);
        put("monetAvatarNameRed", 0xffCC5049);
        put("monetAvatarNameOrange", 0xffD67722);
        put("monetAvatarNameViolet", 0xff955CDB);
        put("monetAvatarNameGreen", 0xff40A920);
        put("monetAvatarNameCyan", 0xff309EBA);
        put("monetAvatarNameBlue", 0xff368AD1);
        put("monetAvatarNamePink", 0xffC7508B);
        put("monetAvatarNameDarkRed", 0xffCC5049);
        put("monetAvatarNameDarkOrange", 0xffD67722);
        put("monetAvatarNameDarkViolet", 0xff955CDB);
        put("monetAvatarNameDarkGreen", 0xff40A920);
        put("monetAvatarNameDarkCyan", 0xff309EBA);
        put("monetAvatarNameDarkBlue", 0xff368AD1);
        put("monetAvatarNameDarkPink", 0xffC7508B);
    }};
    private static int lastMonetColor = 0;

    public static int getColor(String color) {
        try {
            String rawColor = color == null ? "" : color.trim();
            String baseColor = rawColor;
            String darkenPercentValue = null;

            int lastUnderscore = rawColor.lastIndexOf('_');
            if (lastUnderscore > 0 && lastUnderscore < rawColor.length() - 1) {
                String suffix = rawColor.substring(lastUnderscore + 1);
                String candidateBase = rawColor.substring(0, lastUnderscore);
                if (isDigitsOnly(suffix) && canResolveColor(candidateBase)) {
                    baseColor = candidateBase;
                    darkenPercentValue = suffix;
                }
            }

            int resolvedColor = resolveColor(baseColor);
            if (darkenPercentValue != null) {
                resolvedColor = darkenByPercent(resolvedColor, Integer.parseInt(darkenPercentValue));
            }
            return resolvedColor;
        } catch (Exception e) {
            FileLog.e("Error loading color " + color, e);
            return 0;
        }
    }

    private static boolean canResolveColor(String color) {
        return ids.containsKey(color) || avatarBaseColors.containsKey(color);
    }

    private static int resolveColor(String color) {
        Integer id = ids.get(color);
        if (id != null) {
            return ApplicationLoader.applicationContext.getColor(id);
        }

        Integer avatarBaseColor = avatarBaseColors.get(color);
        if (avatarBaseColor != null) {
            int harmonizedColor = getHarmonizedAvatarColor(avatarBaseColor);
            if (color.startsWith("monetAvatarNameDark")) {
                return softenColorForDarkText(harmonizedColor);
            }
            return harmonizedColor;
        }

        throw new IllegalArgumentException("Unknown Monet color token: " + color);
    }

    private static int getHarmonizedAvatarColor(int baseColor) {
        int accentColor = resolveColor("a1_600");
        return Blend.harmonize(baseColor, accentColor);
    }

    public static int harmonizeColor(int baseColor) {
        return getHarmonizedAvatarColor(baseColor);
    }

    private static int softenColorForDarkText(int color) {
        int neutralTextColor = resolveColor("n1_50");
        return ColorUtils.blendARGB(color, neutralTextColor, DARK_NAME_SOFTEN_RATIO);
    }

    private static int darkenByPercent(int color, int percent) {
        int normalizedPercent = Math.max(1, Math.min(percent, 100));
        if (normalizedPercent == 100) {
            return color;
        }

        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[2] = Math.max(0f, Math.min(1f, hsl[2] * normalizedPercent / 100f));

        return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), Color.alpha(color));
    }

    private static boolean isDigitsOnly(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    public static boolean useMonetMd3Colors() {
        return NaConfig.INSTANCE.getSwitchStyle().Int() == SWITCH_STYLE_MD3
            && Theme.getActiveTheme() != null
            && Theme.getActiveTheme().isMonet();
    }

    /**
     * Refresh Monet theme if the system color has changed.
     * Called in LaunchActivity.onResume()
     */
    public static void refreshMonetThemeIfChanged() {
        // Quick check: if the current theme is not a Monet theme, return directly
        Theme.ThemeInfo activeTheme = Theme.getActiveTheme();
        if (activeTheme == null || !activeTheme.isMonet()) {
            lastMonetColor = 0; // Reset to detect correctly when switching back to Monet theme
            return;
        }

        int currentColor = getColor("a1_600");

        // Record the color only on the first call, do not trigger refresh
        if (lastMonetColor == 0) {
            lastMonetColor = currentColor;
            return;
        }

        // Return directly if the color has not changed
        if (lastMonetColor == currentColor) {
            return;
        }

        // Refresh theme
        boolean isNight = Theme.isCurrentThemeNight();
        Theme.applyTheme(activeTheme, isNight);
        NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.needSetDayNightTheme, activeTheme, isNight, null, -1
        );

        lastMonetColor = currentColor;
    }
}
