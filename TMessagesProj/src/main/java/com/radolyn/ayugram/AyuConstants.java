/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram;

import tw.nekomimi.nekogram.NekoConfig;

public class AyuConstants {

    public static final int DOCUMENT_TYPE_NONE = 0;
    public static final int DOCUMENT_TYPE_PHOTO = 1;
    public static final int DOCUMENT_TYPE_STICKER = 2;
    public static final int DOCUMENT_TYPE_FILE = 3;
    public static final int DOCUMENT_TYPE_WEBPAGE = 4;
    public static final int DOCUMENT_TYPE_STORY = 5;

    public static final int OPTION_HISTORY = 1338_01;
    public static final int OPTION_TTL = 1338_02;
    public static final int OPTION_READ_MESSAGE = 1338_03;
    public static final int OPTION_TTL_SAVE = 1338_04;

    public static final int MESSAGE_EDITED_NOTIFICATION = 6968;
    public static final int MESSAGES_DELETED_NOTIFICATION = 6969;
    public static final int DELETED_MEDIA_LOADED_NOTIFICATION = 6970;

    public static String AYU_DATABASE = "ayu-data";

    public static String APP_NAME = NekoConfig.customSavePath.String();
}
