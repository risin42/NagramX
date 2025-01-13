/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.provider.Settings.Secure;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import androidx.core.util.Pair;
import com.google.android.exoplayer2.util.Log;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;

import static org.telegram.messenger.Utilities.random;

public class AyuUtils {
    private static final String NAX = "nu.gpu.nagram_AyuUtils";
    private static final char[] chars = "abcdefghijklmnopqrstuvwxyz1234567890".toCharArray();

    public static boolean moveOrCopyFile(File from, File to) {
        boolean success;
        try {
            success = from.renameTo(to);
        } catch (SecurityException e) {
            if (BuildVars.LOGS_ENABLED) Log.e(NAX, e.toString());
            success = false;
        }

        if (!success) {
            try {
                success = AndroidUtilities.copyFile(from, to);
            } catch (Exception e) {
                if (BuildVars.LOGS_ENABLED) Log.e(NAX, e.toString());
            }
        }

        return success;
    }

    public static String removeExtension(String filename) {
        if (TextUtils.isEmpty(filename)) {
            return filename;
        }

        int index = filename.lastIndexOf('.');
        if (index == -1) { // no extension
            return filename;
        }

        return filename.substring(0, index);
    }

    public static String getExtension(String filename) {
        if (TextUtils.isEmpty(filename)) {
            return "";
        }

        int index = filename.lastIndexOf('.');
        if (index == -1) { // no extension
            return "";
        }

        return filename.substring(index);
    }

    public static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            char c = chars[random.nextInt(chars.length)];
            sb.append(c);
        }
        return sb.toString();
    }

    public static String getFilename(TLObject obj, File attachPathFile) {
        String filename = null;
        if (obj instanceof TLRPC.Message && ((TLRPC.Message) obj).media != null) {
            filename = FileLoader.getDocumentFileName(((TLRPC.Message) obj).media.document);
        }

        if (obj instanceof TLRPC.Document) {
            filename = FileLoader.getDocumentFileName((TLRPC.Document) obj);
        }

        if (TextUtils.isEmpty(filename) && obj instanceof TLRPC.Message) {
            filename = FileLoader.getMessageFileName((TLRPC.Message) obj);
        }
        if (TextUtils.isEmpty(filename)) {
            filename = attachPathFile.getName();
        }
        if (TextUtils.isEmpty(filename)) {
            // well, shit happens
            filename = "unnamed";
        }

        var f = AyuUtils.removeExtension(filename);

        if (obj instanceof TLRPC.Message && ((TLRPC.Message) obj).media instanceof TLRPC.TL_messageMediaPhoto && ((TLRPC.Message) obj).media.photo.sizes != null && !((TLRPC.Message) obj).media.photo.sizes.isEmpty()) {
            var photoSize = FileLoader.getClosestPhotoSizeWithSize(((TLRPC.Message) obj).media.photo.sizes, AndroidUtilities.getPhotoSize());

            if (photoSize != null) {
                f += "#" + photoSize.w + "x" + photoSize.h;
            }
        }

        f += "@" + AyuUtils.generateRandomString(6);
        f += AyuUtils.getExtension(filename);

        return f;
    }

    public static String getReadableFilename(String name) {
        var ext = AyuUtils.getExtension(name);
        var index = name.lastIndexOf("@");
        if (index == -1) {
            return name;
        }

        return name.substring(0, index) + ext;
    }

    public static Pair<Integer, Integer> extractImageSizeFromName(String name) {
        var start = name.lastIndexOf("#") + 1;
        if (start == 0) {
            return null;
        }

        var end = name.lastIndexOf("@");
        if (end == -1) {
            return null;
        }

        try {
            var size = name.substring(start, end).split("x");
            var w = Integer.parseInt(size[0]);
            var h = Integer.parseInt(size[1]);

            return new Pair<>(w, h);
        } catch (Exception e) {
            if (BuildVars.LOGS_ENABLED) Log.e(NAX, "extractImageSizeFromName fucked", e);
            return null;
        }
    }

    public static Pair<Integer, Integer> extractImageSizeFromFile(String path) {
        try {
            var options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            var w = options.outWidth;
            var h = options.outHeight;

            return new Pair<>(w, h);
        } catch (Exception e) {
            if (BuildVars.LOGS_ENABLED) Log.e(NAX, "extractImageSizeFromFile fucked", e);
            return null;
        }
    }

    public static String getPackageName() {
        return ApplicationLoader.applicationContext.getPackageName();
    }

    @SuppressLint("HardwareIds")
    public static String getDeviceIdentifier() {
        return Secure.getString(ApplicationLoader.applicationContext.getContentResolver(), Secure.ANDROID_ID);
    }

    public static String getDeviceName() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    public static void killApplication(Activity activity) {
        activity.finishAndRemoveTask();
        int pid = android.os.Process.myPid();
        android.os.Process.killProcess(pid);
    }

    public static int getMinRealId(ArrayList<MessageObject> messages) {
        for (int i = messages.size() - 1; i > 0; i--) {
            var message = messages.get(i);
            if (message.getId() > 0 && message.isSent()) {
                return message.getId();
            }
        }

        return Integer.MAX_VALUE; // no questions
    }

    public static void shiftEntities(ArrayList<TLRPC.MessageEntity> entities, int offset) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        for (var entity : entities) {
            entity.offset += offset;
        }
    }

    public static CharSequence shortify(CharSequence text, int maxLength) {
        if (TextUtils.isEmpty(text) || text.length() <= maxLength) {
            return text;
        }

        return text.subSequence(0, maxLength - 1) + "…";
    }

    public static void blurify(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return;
        }

        if (!TextUtils.isEmpty(messageObject.messageOwner.message)) {
            var entity = new TLRPC.TL_messageEntitySpoiler();
            entity.offset = 0;
            entity.length = messageObject.messageOwner.message.length();
            messageObject.messageOwner.entities.add(entity);
        }

        if (messageObject.messageOwner.media != null) {
            messageObject.messageOwner.media.spoiler = true;
        }
    }

    // public static CharSequence htmlToString(String text) {
    //     Spannable htmlParsed;
    //     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    //         htmlParsed = new SpannableString(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY));
    //     } else {
    //         htmlParsed = new SpannableString(Html.fromHtml(text));
    //     }

    //     return LocaleUtils.formatWithURLs(htmlParsed);
    // }

    public static boolean isMediaDownloadable(MessageObject message, boolean toGalleryOnly) {
        if (message == null || message.messageOwner == null || message.messageOwner.media == null) {
            return false;
        }

        if (message.messageOwner.media.photo instanceof TLRPC.TL_photoEmpty) {
            return false;
        }

        if (message.messageOwner.media.document instanceof TLRPC.TL_documentEmpty) {
            return false;
        }

        if (MessageObject.isMediaEmpty(message.messageOwner)) {
            return false;
        }

        boolean res = message.isSecretMedia() ||
                message.isGif() ||
                message.isNewGif() ||
                message.isRoundVideo() ||
                message.isVideo() ||
                message.isPhoto();

        if (toGalleryOnly || res) {
            return res;
        }

        return message.isDocument() ||
                message.isMusic() ||
                message.isVoice();
    }
}
