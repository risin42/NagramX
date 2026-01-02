package com.radolyn.ayugram.utils;

import java.util.concurrent.ConcurrentHashMap;

import tw.nekomimi.nekogram.NekoConfig;

public class AyuGhostPreferences {
    public static final String ghostExclusionPrefix = "ghostModeExclusion_";
    private static final ConcurrentHashMap<Long, Boolean> ghostModeExclusions = new ConcurrentHashMap<>();

    public static void setGhostModeExclusion(long chatId, boolean value) {
        long key = Math.abs(chatId);
        ghostModeExclusions.put(key, value);
        NekoConfig.getPreferences().edit().putBoolean(ghostExclusionPrefix + key, value).apply();
    }

    public static boolean getGhostModeExclusion(long chatId) {
        long key = Math.abs(chatId);
        return ghostModeExclusions.computeIfAbsent(key, k -> NekoConfig.getPreferences().getBoolean(ghostExclusionPrefix + k, false));
    }
}
