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

    public static final int DOCUMENT_TYPE_FILE = 3;

    public static final int OPTION_HISTORY = 1338_01;
    public static final int OPTION_TTL = 1338_02;

    public static final int MESSAGE_EDITED_NOTIFICATION = 6968;
    public static final int MESSAGES_DELETED_NOTIFICATION = 6969;

    public static String AYU_DATABASE = "ayu-data";

    public static String APP_NAME = NekoConfig.customSavePath.String();
}
