package com.radolyn.ayugram.utils;

import android.os.Process;

import org.telegram.messenger.DispatchQueue;

public final class AyuQueues {
    public static final DispatchQueue lastSeenQueue = new DispatchQueue("lastSeenQueue", true, Process.THREAD_PRIORITY_BACKGROUND);

    private AyuQueues() {
    }
}
