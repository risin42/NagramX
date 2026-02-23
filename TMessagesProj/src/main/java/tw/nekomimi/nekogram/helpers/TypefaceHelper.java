package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TypefaceSpan;

import java.util.List;

import xyz.nextalone.nagram.NaConfig;

public class TypefaceHelper {

    private static final String TEST_TEXT;
    private static final int CANVAS_SIZE = 40;
    private static final Paint PAINT = new Paint() {{
        setTextSize(20);
        setAntiAlias(false);
        setSubpixelText(false);
        setFakeBoldText(false);
    }};

    private static Boolean mediumWeightSupported = null;
    private static Boolean italicSupported = null;

    static {
        var lang = LocaleController.getInstance().getCurrentLocale().getLanguage();
        if (List.of("zh", "ja", "ko").contains(lang)) {
            TEST_TEXT = "你好";
        } else if (List.of("ar", "fa").contains(lang)) {
            TEST_TEXT = "مرحبا";
        } else if ("iw".equals(lang)) {
            TEST_TEXT = "שלום";
        } else if ("th".equals(lang)) {
            TEST_TEXT = "สวัสดี";
        } else if ("hi".equals(lang)) {
            TEST_TEXT = "नमस्ते";
        } else if (List.of("ru", "uk", "ky", "be", "sr").contains(lang)) {
            TEST_TEXT = "Привет";
        } else {
            TEST_TEXT = "R";
        }
    }

    public static Typeface createTypeface(String assetPath) {
        return switch (assetPath) {
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM ->
                    isMediumWeightSupported() ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.create("sans-serif", Typeface.BOLD);
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC ->
                    isMediumWeightSupported() ? Typeface.create("sans-serif-medium", Typeface.ITALIC) : Typeface.create("sans-serif", Typeface.BOLD_ITALIC);
            case AndroidUtilities.TYPEFACE_RCONDENSED_BOLD ->
                    Typeface.create("sans-serif-condensed", Typeface.BOLD);
            case AndroidUtilities.TYPEFACE_RITALIC ->
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? Typeface.create(Typeface.SANS_SERIF, 400, true) : Typeface.create("sans-serif", Typeface.ITALIC);
            case AndroidUtilities.TYPEFACE_ROBOTO_MONO ->
                    Typeface.MONOSPACE;
            default -> createTypefaceFromAsset(assetPath);
        };
    }

    public static Typeface createTypefaceFromAsset(String assetPath) {
        Typeface.Builder builder = new Typeface.Builder(ApplicationLoader.applicationContext.getAssets(), assetPath);
        if (assetPath.contains("rextrabold")) {
            builder.setWeight(800);
        }
        if (assetPath.contains("medium") || assetPath.contains("rbold")) {
            builder.setWeight(700);
        }
        if (assetPath.contains("italic")) {
            builder.setItalic(true);
        }
        return builder.build();
    }

    public static boolean isMediumWeightSupported() {
        if (mediumWeightSupported == null) {
            mediumWeightSupported = testTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            FileLog.d("mediumWeightSupported = " + mediumWeightSupported);
        }
        return mediumWeightSupported;
    }

    public static boolean isItalicSupported() {
        if (italicSupported == null) {
            italicSupported = testTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
            FileLog.d("italicSupported = " + italicSupported);
        }
        return italicSupported;
    }

    private static boolean testTypeface(Typeface typeface) {
        Canvas canvas = new Canvas();

        Bitmap bitmap1 = Bitmap.createBitmap(CANVAS_SIZE * 2, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap1);
        PAINT.setTypeface(null);
        canvas.drawText(TEST_TEXT, 0, CANVAS_SIZE, PAINT);

        Bitmap bitmap2 = Bitmap.createBitmap(CANVAS_SIZE * 2, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap2);
        PAINT.setTypeface(typeface);
        canvas.drawText(TEST_TEXT, 0, CANVAS_SIZE, PAINT);

        boolean supported = !bitmap1.sameAs(bitmap2);
        AndroidUtilities.recycleBitmaps(List.of(bitmap1, bitmap2));
        return supported;
    }

    public static Typeface createTypeface(int weight, boolean italic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(null, weight, italic);
        }
        var family = switch (weight) {
            case 800 -> "sans-serif-black";
            case 500 -> "sans-serif-medium";
            default -> "sans-serif";
        };
        return Typeface.create(family, italic ? Typeface.ITALIC : Typeface.NORMAL);
    }

    public static SpannableStringBuilder getTitleText(int currentAccount) {
        String title = NaConfig.INSTANCE.getCustomTitle().String();
        if (NaConfig.INSTANCE.getCustomTitleUserName().Bool()) {
            TLRPC.User self = UserConfig.getInstance(currentAccount).getCurrentUser();
            if (self != null && self.first_name != null) {
                title = self.first_name;
            }
        }
        var builder = new SpannableStringBuilder(title);
        builder.setSpan(new LeadingMarginSpan.Standard(dp(2), 0), 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new TypefaceSpan(TypefaceHelper.createTypeface(600, false), 0, Theme.key_telegram_color_dialogsLogo, null), 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

}
