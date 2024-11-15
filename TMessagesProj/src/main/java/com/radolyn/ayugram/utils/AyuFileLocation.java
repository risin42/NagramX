/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram.utils;

import org.telegram.tgnet.TLRPC;

public class AyuFileLocation extends TLRPC.FileLocation {
    public String path;

    public AyuFileLocation(String path) {
        this.path = path;
    }
}
