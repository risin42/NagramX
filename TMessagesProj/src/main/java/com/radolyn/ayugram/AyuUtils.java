/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram;

import static org.telegram.messenger.Utilities.random;

import android.graphics.BitmapFactory;
import android.os.Build;
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

public class AyuUtils {
    private static final String NAX = "AyuUtils";

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

    public static String getDeviceName() {
        return Build.MANUFACTURER + " " + Build.MODEL;
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
}
