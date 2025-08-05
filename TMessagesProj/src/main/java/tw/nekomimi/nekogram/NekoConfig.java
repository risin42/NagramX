package tw.nekomimi.nekogram;

import static tw.nekomimi.nekogram.config.ConfigItem.configTypeBool;
import static tw.nekomimi.nekogram.config.ConfigItem.configTypeFloat;
import static tw.nekomimi.nekogram.config.ConfigItem.configTypeInt;
import static tw.nekomimi.nekogram.config.ConfigItem.configTypeLong;
import static tw.nekomimi.nekogram.config.ConfigItem.configTypeMapIntInt;
import static tw.nekomimi.nekogram.config.ConfigItem.configTypeSetInt;
import static tw.nekomimi.nekogram.config.ConfigItem.configTypeString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Pair;

import com.radolyn.ayugram.utils.AyuGhostUtils;

import org.telegram.messenger.ApplicationLoader;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.helpers.CloudSettingsHelper;

@SuppressLint("ApplySharedPref")
@SuppressWarnings("unused")
public class NekoConfig {

    public static final int TABLET_AUTO = 0;
    public static final int TABLET_ENABLE = 1;

    public static final int DIALOG_FILTER_EXCLUDE_NONE = 0;
    public static final int DIALOG_FILTER_EXCLUDE_MUTED = 1;
    public static final int DIALOG_FILTER_EXCLUDE_ALL = 2;

    public static final int MARKDOWN_PARSER_TELEGRAM = 0;
    public static final int MARKDOWN_PARSER_NEKO = 1;

    public static final int DRAWER_BACKGROUND_DEFAULT = 0;
    public static final int DRAWER_BACKGROUND_AVATAR = 1;
    public static final int DRAWER_BACKGROUND_BIG_AVATAR = 2;
    public static final int DRAWER_BACKGROUND_WALLPAPER = 3;

    public static final int DNS_TYPE_DEFAULT = 0;
    public static final int DNS_TYPE_NAX = 1;
    public static final int DNS_TYPE_SYSTEM = 2;
    public static final int DNS_TYPE_CUSTOM_DOH = 3;

    public static final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("nkmrcfg", Context.MODE_PRIVATE);
    public static final Object sync = new Object();
    public static final String channelAliasPrefix = "channelAliasPrefix_";

    private static boolean configLoaded = false;
    private static final ArrayList<ConfigItem> configs = new ArrayList<>();
    public static final ArrayList<DatacenterInfo> datacenterInfos = new ArrayList<>(5);

    // Configs
    public static ConfigItem configMigrated = addConfig("ConfigMigrated", configTypeBool, false);
    public static ConfigItem largeAvatarInDrawer = addConfig("AvatarAsBackground", configTypeInt, DRAWER_BACKGROUND_WALLPAPER);
    public static ConfigItem unreadBadgeOnBackButton = addConfig("unreadBadgeOnBackButton", configTypeBool, false);
    public static ConfigItem useCustomEmoji = addConfig("useCustomEmoji", configTypeBool, false);
    public static ConfigItem repeatConfirm = addConfig("repeatConfirm", configTypeBool, true);
    public static ConfigItem disableInstantCamera = addConfig("DisableInstantCamera", configTypeBool, true);
    public static ConfigItem showSeconds = addConfig("showSeconds", configTypeBool, false);

    // From NekoConfig
    public static ConfigItem useIPv6 = addConfig("IPv6", configTypeBool, false);
    public static ConfigItem hidePhone = addConfig("HidePhone", configTypeBool, true);
    public static ConfigItem ignoreBlocked = addConfig("IgnoreBlocked", configTypeBool, false);
    public static ConfigItem tabletMode = addConfig("TabletMode", configTypeInt, 0);

    public static ConfigItem typeface = addConfig("TypefaceUseDefault", configTypeBool, false);
    public static ConfigItem nameOrder = addConfig("NameOrder", configTypeInt, 1);
    public static ConfigItem mapPreviewProvider = addConfig("MapPreviewProvider", configTypeInt, 0);
    public static ConfigItem transparentStatusBar = addConfig("TransparentStatusBar", configTypeBool, true);
    public static ConfigItem forceBlurInChat = addConfig("forceBlurInChat", configTypeBool, false);
    public static ConfigItem chatBlueAlphaValue = addConfig("forceBlurInChatAlphaValue", configTypeInt, 127);
    public static ConfigItem hideProxySponsorChannel = addConfig("HideProxySponsorChannel", configTypeBool, false);
    public static ConfigItem showAddToSavedMessages = addConfig("showAddToSavedMessages", configTypeBool, true);
    public static ConfigItem showReport = addConfig("showReport", configTypeBool, false);
    public static ConfigItem showViewHistory = addConfig("showViewHistory", configTypeBool, true);
    public static ConfigItem showAdminActions = addConfig("showAdminActions", configTypeBool, true);
    public static ConfigItem showChangePermissions = addConfig("showChangePermissions", configTypeBool, true);
    public static ConfigItem showDeleteDownloadedFile = addConfig("showDeleteDownloadedFile", configTypeBool, true);
    public static ConfigItem showMessageDetails = addConfig("showMessageDetails", configTypeBool, true);
    public static ConfigItem showTranslate = addConfig("showTranslate", configTypeBool, true);
    public static ConfigItem showRepeat = addConfig("showRepeat", configTypeBool, true);
    public static ConfigItem showShareMessages = addConfig("showShareMessages", configTypeBool, false);
    public static ConfigItem showMessageHide = addConfig("showMessageHide", configTypeBool, false);

    public static ConfigItem actionBarDecoration = addConfig("ActionBarDecoration", configTypeInt, 0);
    public static ConfigItem newYear = addConfig("ChristmasHat", configTypeBool, false);
    public static ConfigItem stickerSize = addConfig("stickerSize", configTypeFloat, 14.0f);
    public static ConfigItem unlimitedFavedStickers = addConfig("UnlimitedFavoredStickers", configTypeBool, false);
    public static ConfigItem unlimitedPinnedDialogs = addConfig("UnlimitedPinnedDialogs", configTypeBool, false);
    public static ConfigItem disablePhotoSideAction = addConfig("DisablePhotoViewerSideAction", configTypeBool, false);
    public static ConfigItem openArchiveOnPull = addConfig("OpenArchiveOnPull", configTypeBool, false);
    public static ConfigItem hideKeyboardOnChatScroll = addConfig("HideKeyboardOnChatScroll", configTypeBool, false);
    public static ConfigItem avatarBackgroundBlur = addConfig("BlurAvatarBackground", configTypeBool, false);
    public static ConfigItem avatarBackgroundDarken = addConfig("DarkenAvatarBackground", configTypeBool, false);
    public static ConfigItem useSystemEmoji = addConfig("EmojiUseDefault", configTypeBool, false);
    public static ConfigItem rearVideoMessages = addConfig("RearVideoMessages", configTypeBool, false);
    public static ConfigItem hideAllTab = addConfig("HideAllTab", configTypeBool, false);

    public static ConfigItem sortByUnread = addConfig("sort_by_unread", configTypeBool, false);
    public static ConfigItem sortByUnmuted = addConfig("sort_by_unmuted", configTypeBool, true);
    public static ConfigItem sortByUser = addConfig("sort_by_user", configTypeBool, true);
    public static ConfigItem sortByContacts = addConfig("sort_by_contacts", configTypeBool, true);

    public static ConfigItem disableSystemAccount = addConfig("DisableSystemAccount", configTypeBool, false);
    public static ConfigItem skipOpenLinkConfirm = addConfig("SkipOpenLinkConfirm", configTypeBool, false);

    public static ConfigItem showIdAndDc = addConfig("ShowIdAndDc", configTypeBool, true);

    public static ConfigItem cachePath = addConfig("cache_path", configTypeString, "");
    public static ConfigItem customSavePath = addConfig("customSavePath", configTypeString, "Nagram");

    public static ConfigItem translationProvider = addConfig("translationProvider", configTypeInt, 1);
    public static ConfigItem translateToLang = addConfig("TransToLang", configTypeString, ""); // "" -> translate to current language (MessageTrans.kt & Translator.kt)
    public static ConfigItem translateInputLang = addConfig("TransInputToLang", configTypeString, "en");
    public static ConfigItem googleCloudTranslateKey = addConfig("GoogleCloudTransKey", configTypeString, "");

    public static ConfigItem disableNotificationBubbles = addConfig("disableNotificationBubbles", configTypeBool, false);

    public static ConfigItem tabsTitleType = addConfig("TabTitleType", configTypeInt, NekoXConfig.TITLE_TYPE_MIX);
    public static ConfigItem confirmAVMessage = addConfig("ConfirmAVMessage", configTypeBool, false);
    public static ConfigItem askBeforeCall = addConfig("AskBeforeCalling", configTypeBool, true);
    public static ConfigItem disableNumberRounding = addConfig("DisableNumberRounding", configTypeBool, false);

    public static ConfigItem dnsType = addConfig("DnsType", configTypeInt, DNS_TYPE_DEFAULT);
    public static ConfigItem customDoH = addConfig("CustomDoH", configTypeString, "");
    public static ConfigItem hideProxyByDefault = addConfig("HideProxyByDefault", configTypeBool, true);
    public static ConfigItem useProxyItem = addConfig("UseProxyItem", configTypeBool, true);

    public static ConfigItem disableAppBarShadow = addConfig("DisableAppBarShadow", configTypeBool, false);
    public static ConfigItem mediaPreview = addConfig("MediaPreview", configTypeBool, true);

    public static ConfigItem autoPauseVideo = addConfig("AutoPauseVideo", configTypeBool, false);
    public static ConfigItem disableProximityEvents = addConfig("DisableProximityEvents", configTypeBool, false);

    public static ConfigItem ignoreContentRestrictions = addConfig("ignoreContentRestrictions", configTypeBool, true);
    public static ConfigItem useChatAttachMediaMenu = addConfig("UseChatAttachEnterMenu", configTypeBool, true);
    public static ConfigItem disableLinkPreviewByDefault = addConfig("DisableLinkPreviewByDefault", configTypeBool, false);
    public static ConfigItem sendCommentAfterForward = addConfig("SendCommentAfterForward", configTypeBool, true);
    public static ConfigItem disableTrending = addConfig("DisableTrending", configTypeBool, true);
    public static ConfigItem dontSendGreetingSticker = addConfig("DontSendGreetingSticker", configTypeBool, true);
    public static ConfigItem hideTimeForSticker = addConfig("HideTimeForSticker", configTypeBool, false);
    public static ConfigItem takeGIFasVideo = addConfig("TakeGIFasVideo", configTypeBool, false);
    public static ConfigItem maxRecentStickerCount = addConfig("maxRecentStickerCount", configTypeInt, 20);
    public static ConfigItem disableSwipeToNext = addConfig("disableSwipeToNextChannel", configTypeBool, true);
    public static ConfigItem disableChoosingSticker = addConfig("disableChoosingSticker", configTypeBool, false);
    public static ConfigItem hideGroupSticker = addConfig("hideGroupSticker", configTypeBool, false);
    public static ConfigItem rememberAllBackMessages = addConfig("rememberAllBackMessages", configTypeBool, false);
    public static ConfigItem hideSendAsChannel = addConfig("hideSendAsChannel", configTypeBool, false);
    public static ConfigItem showSpoilersDirectly = addConfig("showSpoilersDirectly", configTypeBool, false);

    public static ConfigItem labelChannelUser = addConfig("labelChannelUser", configTypeBool, true);
    public static ConfigItem channelAlias = addConfig("channelAlias", configTypeBool, false);

    public static ConfigItem disableAutoDownloadingWin32Executable = addConfig("Win32ExecutableFiles", configTypeBool, true);
    public static ConfigItem disableAutoDownloadingArchive = addConfig("ArchiveFiles", configTypeBool, true);

    public static ConfigItem useMediaStreamInVoip = addConfig("UseMediaStreamInVoip", configTypeBool, false);
    public static ConfigItem customAudioBitrate = addConfig("customAudioBitrate", configTypeInt, 32);
    public static ConfigItem enhancedFileLoader = addConfig("enhancedFileLoader", configTypeBool, false);
    public static ConfigItem uploadBoost = addConfig("uploadBoost", configTypeBool, false);
    public static ConfigItem useOSMDroidMap = addConfig("useOSMDroidMap", configTypeBool, false);
    public static ConfigItem mapDriftingFixForGoogleMaps = addConfig("mapDriftingFixForGoogleMaps", configTypeBool, true);

    public static ConfigItem localPremium = addConfig("localPremium", configTypeBool, false);
    public static ConfigItem localeToDBC = addConfig("LocaleToDBC", configTypeBool, false);

    public static ConfigItem usePersianCalendar = addConfig("UsePersianCalendar", configTypeBool, false);
    public static ConfigItem displayPersianCalendarByLatin = addConfig("DisplayPersianCalendarByLatin", configTypeBool, false);

    public static ConfigItem minimizedStickerCreator = addConfig("minimizedStickerCreator", configTypeBool, false);

    // --- Ghost Mode ---
    public static ConfigItem sendReadMessagePackets = addConfig("sendReadMessagePackets", configTypeBool, true);
    public static ConfigItem sendReadStoriesPackets = addConfig("sendReadStoriesPackets", configTypeBool, true);
    public static ConfigItem sendOnlinePackets = addConfig("sendOnlinePackets", configTypeBool, true);
    public static ConfigItem sendUploadProgress = addConfig("sendUploadProgress", configTypeBool, true);
    public static ConfigItem sendOfflinePacketAfterOnline = addConfig("sendOfflinePacketAfterOnline", configTypeBool, false);
    public static ConfigItem markReadAfterSend = addConfig("markReadAfterSend", configTypeBool, true);
    public static ConfigItem showGhostInDrawer = addConfig("showGhostInDrawer", configTypeBool, false);

    // --- Locked Status ---
    public static ConfigItem sendReadMessagePacketsLocked = addConfig("sendReadMessagePacketsLocked", configTypeBool, false);
    public static ConfigItem sendReadStoriesPacketsLocked = addConfig("sendReadStoriesPacketsLocked", configTypeBool, false);
    public static ConfigItem sendOnlinePacketsLocked = addConfig("sendOnlinePacketsLocked", configTypeBool, false);
    public static ConfigItem sendUploadProgressLocked = addConfig("sendUploadProgressLocked", configTypeBool, false);
    public static ConfigItem sendOfflinePacketAfterOnlineLocked = addConfig("sendOfflinePacketAfterOnlineLocked", configTypeBool, false);
    // --- Ghost Mode ---

    static {
        loadConfig(false);
        checkMigration();
    }

    public static ConfigItem addConfig(String k, int t, Object d) {
        ConfigItem a = new ConfigItem(k, t, d);
        configs.add(a);
        return a;
    }

    public static void loadConfig(boolean force) {
        synchronized (sync) {
            if (configLoaded && !force) {
                return;
            }
            for (int i = 0; i < configs.size(); i++) {
                ConfigItem o = configs.get(i);

                if (o.type == configTypeBool) {
                    o.value = preferences.getBoolean(o.key, (boolean) o.defaultValue);
                }
                if (o.type == configTypeInt) {
                    o.value = preferences.getInt(o.key, (int) o.defaultValue);
                }
                if (o.type == configTypeLong) {
                    o.value = preferences.getLong(o.key, (Long) o.defaultValue);
                }
                if (o.type == configTypeFloat) {
                    o.value = preferences.getFloat(o.key, (Float) o.defaultValue);
                }
                if (o.type == configTypeString) {
                    o.value = preferences.getString(o.key, (String) o.defaultValue);
                }
                if (o.type == configTypeSetInt) {
                    Set<String> ss = preferences.getStringSet(o.key, new HashSet<>());
                    HashSet<Integer> si = new HashSet<>();
                    for (String s : ss) {
                        si.add(Integer.parseInt(s));
                    }
                    o.value = si;
                }
                if (o.type == configTypeMapIntInt) {
                    String cv = preferences.getString(o.key, "");
                    // Log.e("NC", String.format("Getting pref %s val %s", o.key, cv));
                    if (cv.length() == 0) {
                        o.value = new HashMap<Integer, Integer>();
                    } else {
                        try {
                            byte[] data = Base64.decode(cv, Base64.DEFAULT);
                            ObjectInputStream ois = new ObjectInputStream(
                                    new ByteArrayInputStream(data));
                            o.value = (HashMap<Integer, Integer>) ois.readObject();
                            if (o.value == null) {
                                o.value = new HashMap<Integer, Integer>();
                            }
                            ois.close();
                        } catch (Exception e) {
                            o.value = new HashMap<Integer, Integer>();
                        }
                    }
                }
            }
            if (!configLoaded)
                preferences.registerOnSharedPreferenceChangeListener(CloudSettingsHelper.listener);
            for (int a = 1; a <= 5; a++) {
                datacenterInfos.add(new DatacenterInfo(a));
            }
            configLoaded = true;
        }
    }

    public static class DatacenterInfo {

        public int id;

        public long pingId;
        public long ping;
        public boolean checking;
        public boolean available;
        public long availableCheckTime;

        public DatacenterInfo(int i) {
            id = i;
        }
    }

    public static boolean fixDriftingForGoogleMaps() {
        return !useOSMDroidMap.Bool() && mapDriftingFixForGoogleMaps.Bool();
    }

    public static void checkMigration() {
        if (!configMigrated.Bool()) {
            configMigrated.setConfigBool(true);

            if (preferences.contains("DisableChatAction")) {
                sendUploadProgress.setConfigBool(!preferences.getBoolean("DisableChatAction", true));
            }
            if (preferences.contains("DisableSendReadStories")) {
                sendReadStoriesPackets.setConfigBool(!preferences.getBoolean("DisableSendReadStories", true));
            }
        }
    }

    // --- Ghost Mode ---
    public static boolean isGhostModeActive() {
        for (Pair<ConfigItem, ConfigItem> pair : ghostToggleItems) {
            ConfigItem item = pair.first;
            ConfigItem lockedItem = pair.second;
            if (!lockedItem.Bool()) {
                boolean currentValue = item.Bool();
                boolean isGhostState = (item == sendOfflinePacketAfterOnline) == currentValue;

                if (!isGhostState) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void setGhostMode(boolean enabled) {
        for (Pair<ConfigItem, ConfigItem> pair : ghostToggleItems) {
            ConfigItem item = pair.first;
            ConfigItem lockedItem = pair.second;
            if (!lockedItem.Bool()) {
                boolean targetValue = (item == sendOfflinePacketAfterOnline) == enabled;
                item.setConfigBool(targetValue);
            }
        }
    }

    public static void toggleGhostMode() {
        boolean newState = !isGhostModeActive();
        setGhostMode(newState);

        boolean sendOnlineNow = !newState && !sendOfflinePacketAfterOnlineLocked.Bool() && sendOfflinePacketAfterOnline.Bool();
        AyuGhostUtils.performStatusRequest(sendOnlineNow);
    }

    private static final List<Pair<ConfigItem, ConfigItem>> ghostToggleItems = Arrays.asList(
            new Pair<>(sendReadMessagePackets, sendReadMessagePacketsLocked),
            new Pair<>(sendReadStoriesPackets, sendReadStoriesPacketsLocked),
            new Pair<>(sendOnlinePackets, sendOnlinePacketsLocked),
            new Pair<>(sendUploadProgress, sendUploadProgressLocked),
            new Pair<>(sendOfflinePacketAfterOnline, sendOfflinePacketAfterOnlineLocked)
    );
    // --- Ghost Mode ---
}
