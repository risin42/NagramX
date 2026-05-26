package tw.nekomimi.nekogram.filters;

import android.text.TextUtils;

import androidx.collection.LruCache;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.MessageHelper;
import xyz.nextalone.nagram.NaConfig;

public class AyuFilter {
    private static final Object cacheLock = new Object();
    private static final int PER_DIALOG_CACHE_LIMIT = 1000;
    private static final ConcurrentHashMap<Long, LruCache<Integer, Integer>> filteredCache = new ConcurrentHashMap<>();
    private static volatile ArrayList<FilterModel> filterModels;
    private static volatile ArrayList<ChatFilterEntry> chatFilterEntries;
    private static volatile HashSet<Long> excludedDialogs;
    private static volatile HashSet<Long> blockedChannels;
    private static volatile HashSet<Long> customFilteredUsers;
    private static volatile HashMap<Long, CustomFilteredUser> customFilteredUsersData;

    public static ArrayList<FilterModel> getRegexFilters() {
        if (filterModels == null) {
            synchronized (cacheLock) {
                if (filterModels == null) {
                    var str = NaConfig.INSTANCE.getRegexFiltersData().String();
                    FilterModel[] arr = new Gson().fromJson(str, FilterModel[].class);
                    if (arr != null) {
                        filterModels = new ArrayList<>(Arrays.asList(arr));
                        boolean migrated = false;
                        for (var filter : filterModels) {
                            if (filter.migrateFromLegacy(0L)) {
                                migrated = true;
                            }
                            filter.buildPattern();
                        }
                        if (migrated) {
                            NaConfig.INSTANCE.getRegexFiltersData().setConfigString(new Gson().toJson(filterModels));
                        }
                    } else {
                        filterModels = new ArrayList<>();
                    }
                }
            }
        }
        return filterModels;
    }

    public static void addFilter(String text, boolean caseInsensitive, int filterAction) {
        addFilter(text, caseInsensitive, filterAction, 0xFFFFFFFF);
    }

    public static void addFilter(String text, boolean caseInsensitive, int filterAction, int spoilerColor) {
        var list = new ArrayList<>(getRegexFilters());
        FilterModel filterModel = new FilterModel();
        filterModel.regex = text;
        filterModel.caseInsensitive = caseInsensitive;
        filterModel.filterAction = filterAction;
        filterModel.spoilerColor = spoilerColor;
        filterModel.enabled = true;
        list.add(0, filterModel);
        saveFilter(list);
    }

    public static void editFilter(int filterIdx, String text, boolean caseInsensitive, int filterAction) {
        editFilter(filterIdx, text, caseInsensitive, filterAction, 0xFFFFFFFF);
    }

    public static void editFilter(int filterIdx, String text, boolean caseInsensitive, int filterAction, int spoilerColor) {
        var list = new ArrayList<>(getRegexFilters());
        if (filterIdx < 0 || filterIdx >= list.size()) {
            return;
        }
        FilterModel filterModel = list.get(filterIdx);
        filterModel.regex = text;
        filterModel.caseInsensitive = caseInsensitive;
        filterModel.filterAction = filterAction;
        filterModel.spoilerColor = spoilerColor;
        saveFilter(list);
    }

    public static void saveFilter(ArrayList<FilterModel> filterModels1) {
        var str = new Gson().toJson(filterModels1);
        NaConfig.INSTANCE.getRegexFiltersData().setConfigString(str);
        AyuFilter.rebuildCache();
    }

    public static void removeFilter(int filterIdx) {
        var list = new ArrayList<>(getRegexFilters());
        if (filterIdx < 0 || filterIdx >= list.size()) {
            return;
        }
        list.remove(filterIdx);
        saveFilter(list);
    }

    public static CharSequence getMessageText(MessageObject selectedObject, MessageObject.GroupedMessages selectedObjectGroup) {
        if (selectedObject == null) {
            return null;
        }
        if (selectedObject.type == MessageObject.TYPE_EMOJIS || selectedObject.type == MessageObject.TYPE_ANIMATED_STICKER || selectedObject.type == MessageObject.TYPE_STICKER) {
            return null;
        }
        CharSequence messageText = MessageHelper.getMessagePlainTextFull(selectedObject, selectedObjectGroup);
        if (TextUtils.isEmpty(messageText) || Emoji.fullyConsistsOfEmojis(messageText)) {
            messageText = null;
        }
        if (selectedObject.translated || selectedObject.isRestrictedMessage) {
            messageText = null;
        }
        return messageText;
    }

    public static void rebuildCache() {
        synchronized (cacheLock) {
            filterModels = null;
            chatFilterEntries = null;
            excludedDialogs = null;
            filteredCache.clear();
        }
    }

    public static void invalidateFilteredCache() {
        synchronized (cacheLock) {
            filteredCache.clear();
        }
    }

    private static int getFilterActionInternal(CharSequence text, long dialogId) {
        int bestAction = -1; // -1 means no match

        if (chatFilterEntries != null) {
            for (var entry : chatFilterEntries) {
                if (entry.dialogId == dialogId) {
                    if (entry.filters != null) {
                        for (var fm : entry.filters) {
                            if (!fm.enabled) continue;
                            if (fm.pattern != null && fm.pattern.matcher(text).find()) {
                                if (bestAction == -1 || fm.filterAction < bestAction) {
                                    bestAction = fm.filterAction;
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }

        boolean isPrivateDialog = dialogId > 0;
        if (isPrivateDialog && !NaConfig.INSTANCE.getRegexFiltersEnableInChats().Bool()) {
            return bestAction;
        }

        if (filterModels != null) {
            for (var fm : filterModels) {
                if (!fm.enabled) continue;
                if (fm.pattern != null && fm.pattern.matcher(text).find()) {
                    if (bestAction == -1 || fm.filterAction < bestAction) {
                        bestAction = fm.filterAction;
                    }
                }
            }
        }

        return bestAction;
    }

    public static boolean isFiltered(MessageObject msg, MessageObject.GroupedMessages group) {
        return getFilterAction(msg, group) == FilterModel.ACTION_HIDE;
    }

    private static int computeCustomResult(MessageObject msg) {
        int customResult = -1;
        long senderId = msg.getFromChatId();
        if (senderId > 0L) {
            if (NekoConfig.ignoreBlocked.Bool() && getCustomFilteredUsers().contains(senderId)) {
                CustomFilteredUser cfu = getCustomFilteredUser(senderId);
                if (cfu != null && cfu.filterAction != FilterModel.ACTION_HIDE) {
                    customResult = cfu.filterAction;
                }
            }
            if (customResult == -1 && NaConfig.INSTANCE.getMaskBlockedUserMessages().Bool()) {
                if (MessagesController.getInstance(msg.currentAccount).blockePeers.indexOfKey(senderId) >= 0) {
                    customResult = FilterModel.ACTION_SPOILER_ALL;
                }
            }
        }
        return customResult;
    }

    public static int getFilterAction(MessageObject msg, MessageObject.GroupedMessages group) {
        if (msg == null || msg.isOutOwner()) {
            return -1;
        }

        if (!NaConfig.INSTANCE.getRegexFiltersEnabled().Bool()) {
            return computeCustomResult(msg);
        }

        var text = getMessageText(msg, group);
        if (TextUtils.isEmpty(text)) {
            return computeCustomResult(msg);
        }
        if (filterModels == null) {
            getRegexFilters();
        }
        if (chatFilterEntries == null) {
            getChatFilterEntries();
        }
        long dialogId = msg.getDialogId();
        if (isDialogExcluded(dialogId)) {
            return computeCustomResult(msg);
        }

        LruCache<Integer, Integer> dialogCache = filteredCache.computeIfAbsent(dialogId, k -> new LruCache<>(PER_DIALOG_CACHE_LIMIT));
        Integer cached;

        synchronized (dialogCache) {
            cached = dialogCache.get(msg.getId());
        }

        int regexResult;
        if (cached != null) {
            regexResult = cached;
        } else {
            regexResult = getFilterActionInternal(text, dialogId);
            synchronized (dialogCache) {
                dialogCache.put(msg.getId(), regexResult);
                if (group != null && group.messages != null && !group.messages.isEmpty()) {
                    for (var m : group.messages) {
                        dialogCache.put(m.getId(), regexResult);
                    }
                }
            }
        }

        int customResult = computeCustomResult(msg);
        if (customResult != -1 && (regexResult == -1 || customResult < regexResult)) {
            return customResult;
        }
        return regexResult;
    }

    public static List<FilterModel> getMatchingFilterModels(MessageObject msg, MessageObject.GroupedMessages group) {
        List<FilterModel> matches = new ArrayList<>();
        if (!NaConfig.INSTANCE.getRegexFiltersEnabled().Bool()) return matches;
        if (msg == null || msg.isOutOwner()) return matches;

        var text = getMessageText(msg, group);
        if (TextUtils.isEmpty(text)) return matches;

        if (filterModels == null) getRegexFilters();
        if (chatFilterEntries == null) getChatFilterEntries();

        long dialogId = msg.getDialogId();
        if (isDialogExcluded(dialogId)) return matches;

        if (chatFilterEntries != null) {
            for (var entry : chatFilterEntries) {
                if (entry.dialogId == dialogId) {
                    if (entry.filters != null) {
                        for (var fm : entry.filters) {
                            if (fm.enabled && fm.pattern != null && fm.pattern.matcher(text).find()) {
                                matches.add(fm);
                            }
                        }
                    }
                    break;
                }
            }
        }

        boolean isPrivate = dialogId > 0;
        if (isPrivate && !NaConfig.INSTANCE.getRegexFiltersEnableInChats().Bool()) return matches;

        if (filterModels != null) {
            for (var fm : filterModels) {
                if (fm.enabled && fm.pattern != null && fm.pattern.matcher(text).find()) {
                    matches.add(fm);
                }
            }
        }

        return matches;
    }

    public static ArrayList<ChatFilterEntry> getChatFilterEntries() {
        if (chatFilterEntries == null) {
            synchronized (cacheLock) {
                if (chatFilterEntries == null) {
                    var str = NaConfig.INSTANCE.getRegexChatFiltersData().String();
                    try {
                        ChatFilterEntry[] arr = new Gson().fromJson(str, ChatFilterEntry[].class);
                        if (arr != null) {
                            chatFilterEntries = new ArrayList<>(Arrays.asList(arr));
                            boolean migrated = false;
                            for (var entry : chatFilterEntries) {
                                if (entry.filters == null) continue;
                                for (var f : entry.filters) {
                                    if (f.migrateFromLegacy(entry.dialogId)) {
                                        migrated = true;
                                    }
                                    f.buildPattern();
                                }
                            }
                            if (migrated) {
                                var json = new Gson().toJson(chatFilterEntries);
                                NaConfig.INSTANCE.getRegexChatFiltersData().setConfigString(json);
                            }
                        } else {
                            chatFilterEntries = new ArrayList<>();
                        }
                    } catch (Exception e) {
                        chatFilterEntries = new ArrayList<>();
                    }
                }
            }
        }
        return chatFilterEntries;
    }

    public static void saveChatFilterEntries(ArrayList<ChatFilterEntry> entries) {
        var str = new Gson().toJson(entries);
        NaConfig.INSTANCE.getRegexChatFiltersData().setConfigString(str);
        AyuFilter.rebuildCache();
    }

    public static ArrayList<FilterModel> getChatFiltersForDialog(long dialogId) {
        var entries = getChatFilterEntries();
        for (var e : entries) {
            if (e.dialogId == dialogId) {
                return e.filters != null ? e.filters : new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    public static void addChatFilter(long dialogId, String text, boolean caseInsensitive, int filterAction) {
        addChatFilter(dialogId, text, caseInsensitive, filterAction, 0xFFFFFFFF);
    }

    public static void addChatFilter(long dialogId, String text, boolean caseInsensitive, int filterAction, int spoilerColor) {
        var entries = new ArrayList<>(getChatFilterEntries());
        ChatFilterEntry target = null;
        for (var e : entries) {
            if (e.dialogId == dialogId) {
                target = e;
                break;
            }
        }
        if (target == null) {
            target = new ChatFilterEntry();
            target.dialogId = dialogId;
            entries.add(target);
        }

        FilterModel filterModel = new FilterModel();
        filterModel.regex = text;
        filterModel.caseInsensitive = caseInsensitive;
        filterModel.filterAction = filterAction;
        filterModel.spoilerColor = spoilerColor;
        filterModel.enabled = true;
        if (target.filters == null) {
            target.filters = new ArrayList<>();
        }
        target.filters.add(0, filterModel);

        saveChatFilterEntries(entries);
    }

    public static void editChatFilter(long dialogId, int filterIdx, String text, boolean caseInsensitive, int filterAction) {
        editChatFilter(dialogId, filterIdx, text, caseInsensitive, filterAction, 0xFFFFFFFF);
    }

    public static void editChatFilter(long dialogId, int filterIdx, String text, boolean caseInsensitive, int filterAction, int spoilerColor) {
        var entries = new ArrayList<>(getChatFilterEntries());
        for (var e : entries) {
            if (e.dialogId == dialogId) {
                if (e.filters != null && filterIdx >= 0 && filterIdx < e.filters.size()) {
                    var fm = e.filters.get(filterIdx);
                    fm.regex = text;
                    fm.caseInsensitive = caseInsensitive;
                    fm.filterAction = filterAction;
                    fm.spoilerColor = spoilerColor;
                    saveChatFilterEntries(entries);
                }
                return;
            }
        }
    }

    // Get the mask color for spoiler particle
    public static int getMaskColor(MessageObject msg, MessageObject.GroupedMessages group) {
        int winningAction = getFilterAction(msg, group);
        if (winningAction == FilterModel.ACTION_HIDE) return 0xFFFFFFFF;
        for (FilterModel fm : getMatchingFilterModels(msg, group)) {
            if (fm.filterAction == winningAction) return fm.spoilerColor;
        }
        long senderId = msg.getFromChatId();
        if (senderId > 0L) {
            CustomFilteredUser cfu = getCustomFilteredUser(senderId);
            if (cfu != null && cfu.filterAction == winningAction) {
                return cfu.spoilerColor;
            }

            if (NaConfig.INSTANCE.getMaskBlockedUserMessages().Bool()) {
                if (MessagesController.getInstance(msg.currentAccount).blockePeers.indexOfKey(senderId) >= 0) {
                    return NaConfig.INSTANCE.getBlockedUserMaskColor().Int();
                }
            }
        }
        return 0xFFFFFFFF;
    }

    public static void removeChatFilter(long dialogId, int filterIdx) {
        var entries = new ArrayList<>(getChatFilterEntries());
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            if (e.dialogId == dialogId) {
                if (e.filters != null && filterIdx >= 0 && filterIdx < e.filters.size()) {
                    e.filters.remove(filterIdx);
                    if (e.filters.isEmpty()) {
                        entries.remove(i);
                    }
                    saveChatFilterEntries(entries);
                }
                return;
            }
        }
    }

    private static HashSet<Long> getExcludedDialogs() {
        if (excludedDialogs == null) {
            synchronized (cacheLock) {
                if (excludedDialogs == null) {
                    try {
                        String str = NaConfig.INSTANCE.getRegexFiltersExcludedDialogs().String();
                        Long[] arr = new Gson().fromJson(str, Long[].class);
                        excludedDialogs = new HashSet<>();
                        if (arr != null) {
                            excludedDialogs.addAll(Arrays.asList(arr));
                        }
                    } catch (Exception e) {
                        excludedDialogs = new HashSet<>();
                    }
                }
            }
        }
        return excludedDialogs;
    }

    public static boolean isDialogExcluded(long dialogId) {
        return getExcludedDialogs().contains(dialogId);
    }

    public static void setDialogExcluded(long dialogId, boolean excluded) {
        HashSet<Long> set = new HashSet<>(getExcludedDialogs());
        boolean changed;
        if (excluded) {
            changed = set.add(dialogId);
        } else {
            changed = set.remove(dialogId);
        }
        if (changed) {
            Long[] arr = set.toArray(new Long[0]);
            String str = new Gson().toJson(arr);
            NaConfig.INSTANCE.getRegexFiltersExcludedDialogs().setConfigString(str);
            synchronized (cacheLock) {
                excludedDialogs = set;
            }
            filteredCache.remove(dialogId);
        }
    }

    public static void clearAllFilters() {
        NaConfig.INSTANCE.getRegexFiltersData().setConfigString("[]");
        NaConfig.INSTANCE.getRegexChatFiltersData().setConfigString("[]");
        NaConfig.INSTANCE.getRegexFiltersExcludedDialogs().setConfigString("[]");
        NaConfig.INSTANCE.getCustomFilteredUsersData().setConfigString("[]");
        synchronized (cacheLock) {
            customFilteredUsers = new HashSet<>();
            customFilteredUsersData = new HashMap<>();
        }
        rebuildCache();
    }

    private static HashSet<Long> getBlockedChannels() {
        if (blockedChannels == null) {
            synchronized (cacheLock) {
                if (blockedChannels == null) {
                    try {
                        String str = NaConfig.INSTANCE.getBlockedChannelsData().String();
                        Long[] arr = new Gson().fromJson(str, Long[].class);
                        blockedChannels = new HashSet<>();
                        if (arr != null) {
                            blockedChannels.addAll(Arrays.asList(arr));
                        }
                    } catch (Exception e) {
                        blockedChannels = new HashSet<>();
                    }
                }
            }
        }
        return blockedChannels;
    }

    private static void saveBlockedChannels(HashSet<Long> ids) {
        ArrayList<Long> sorted = new ArrayList<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null && id < 0L) {
                    sorted.add(id);
                }
            }
        }
        Collections.sort(sorted);
        String str = new Gson().toJson(sorted.toArray(new Long[0]));
        NaConfig.INSTANCE.getBlockedChannelsData().setConfigString(str);
        synchronized (cacheLock) {
            blockedChannels = new HashSet<>(sorted);
        }
    }

    public static boolean isBlockedChannel(long dialogId) {
        return NekoConfig.ignoreBlocked.Bool() && getBlockedChannels().contains(dialogId);
    }

    public static boolean isCustomFilteredPeer(long peerId) {
        return NekoConfig.ignoreBlocked.Bool() && peerId > 0L && getCustomFilteredUsers().contains(peerId);
    }

    // If shadow ban is on hide message, return true (mask is not handled here)
    public static boolean isCustomFilteredPeerHidden(long peerId) {
        if (!NekoConfig.ignoreBlocked.Bool() || peerId <= 0L) return false;
        if (!getCustomFilteredUsers().contains(peerId)) return false;
        CustomFilteredUser cfu = getCustomFilteredUser(peerId);
        return cfu == null || cfu.filterAction == FilterModel.ACTION_HIDE;
    }

    public static void setCustomFilteredUserAction(long userId, int filterAction, int spoilerColor) {
        if (userId <= 0L) return;
        ensureCustomFilteredUsersLoaded();
        HashSet<Long> ids = new HashSet<>(getCustomFilteredUsers());
        if (!ids.contains(userId)) return;
        HashMap<Long, CustomFilteredUser> map = new HashMap<>(getCustomFilteredUsersDataMap());
        CustomFilteredUser cfu = map.get(userId);
        if (cfu == null) {
            cfu = new CustomFilteredUser();
            cfu.id = userId;
        }
        cfu.filterAction = filterAction;
        cfu.spoilerColor = spoilerColor;
        map.put(userId, cfu);
        saveCustomFilteredUsers(ids, map);
        invalidateFilteredCache();
    }

    public static void blockPeer(long dialogId) {
        HashSet<Long> set = new HashSet<>(getBlockedChannels());
        if (set.add(dialogId)) {
            saveBlockedChannels(set);
        }
    }

    public static void unblockPeer(long dialogId) {
        HashSet<Long> set = new HashSet<>(getBlockedChannels());
        if (set.remove(dialogId)) {
            saveBlockedChannels(set);
        }
    }

    public static ArrayList<Long> getBlockedChannelsList() {
        return checkBlockedChannels(getBlockedChannels());
    }

    public static ArrayList<Long> getAllBlockedChannelsList() {
        ArrayList<Long> list = new ArrayList<>(getBlockedChannels());
        Collections.sort(list);
        return list;
    }

    public static int getBlockedChannelsCount() {
        return getBlockedChannels().size();
    }

    public static void clearBlockedChannels() {
        saveBlockedChannels(new HashSet<>());
    }

    public static void setBlockedChannels(ArrayList<Long> dialogIds) {
        HashSet<Long> set = new HashSet<>();
        if (dialogIds != null) {
            for (Long dialogId : dialogIds) {
                if (dialogId != null && dialogId < 0L) {
                    set.add(dialogId);
                }
            }
        }
        saveBlockedChannels(set);
    }

    public static ArrayList<Long> checkBlockedChannels(HashSet<Long> blockedChannels) {
        if (blockedChannels == null || blockedChannels.isEmpty()) return new ArrayList<>();
        ArrayList<Long> filtered = new ArrayList<>();
        try {
            final MessagesController mc = MessagesController.getInstance(UserConfig.selectedAccount);
            final MessagesStorage ms = MessagesStorage.getInstance(UserConfig.selectedAccount);
            for (Long did : blockedChannels) {
                if (did == null) continue;
                if (did < 0) {
                    TLRPC.Chat chat = mc.getChat(-did);
                    if (chat == null) {
                        chat = ms.getChatSync(-did);
                    }
                    if (chat != null) {
                        filtered.add(did);
                        mc.putChat(chat, true);
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return filtered;
    }

    public static void onMessageEdited(int msgId, long dialogId) {
        var dialogCache = filteredCache.get(dialogId);
        if (dialogCache != null) {
            synchronized (dialogCache) {
                dialogCache.remove(msgId);
            }
        }
    }

    private static void ensureCustomFilteredUsersLoaded() {
        if (customFilteredUsers != null && customFilteredUsersData != null) {
            return;
        }
        synchronized (cacheLock) {
            if (customFilteredUsers != null && customFilteredUsersData != null) {
                return;
            }
            HashSet<Long> ids = new HashSet<>();
            HashMap<Long, CustomFilteredUser> data = new HashMap<>();
            try {
                String str = NaConfig.INSTANCE.getCustomFilteredUsersData().String();
                CustomFilteredUser[] arr = new Gson().fromJson(str, CustomFilteredUser[].class);
                if (arr != null) {
                    for (CustomFilteredUser item : arr) {
                        if (item != null && item.id > 0L) {
                            ids.add(item.id);
                            data.put(item.id, item);
                        }
                    }
                }
            } catch (Exception ignore) {
            }
            customFilteredUsers = ids;
            customFilteredUsersData = data;
        }
    }

    private static HashSet<Long> getCustomFilteredUsers() {
        ensureCustomFilteredUsersLoaded();
        return customFilteredUsers;
    }

    private static HashMap<Long, CustomFilteredUser> getCustomFilteredUsersDataMap() {
        ensureCustomFilteredUsersLoaded();
        return customFilteredUsersData;
    }

    private static void saveCustomFilteredUsers(HashSet<Long> ids, HashMap<Long, CustomFilteredUser> dataMap) {
        ArrayList<Long> sorted = new ArrayList<>(ids);
        Collections.sort(sorted);
        ArrayList<CustomFilteredUser> out = new ArrayList<>(sorted.size());
        HashMap<Long, CustomFilteredUser> resultMap = new HashMap<>(sorted.size());
        for (Long id : sorted) {
            if (id == null || id <= 0L) {
                continue;
            }
            CustomFilteredUser user = dataMap.get(id);
            if (user == null) {
                user = new CustomFilteredUser();
                user.id = id;
            }
            out.add(user);
            resultMap.put(user.id, user);
        }
        String str = new Gson().toJson(out.toArray(new CustomFilteredUser[0]));
        NaConfig.INSTANCE.getCustomFilteredUsersData().setConfigString(str);
        synchronized (cacheLock) {
            customFilteredUsers = new HashSet<>(resultMap.keySet());
            customFilteredUsersData = resultMap;
        }
    }

    public static ArrayList<Long> getCustomFilteredUsersList() {
        ArrayList<Long> list = new ArrayList<>(getCustomFilteredUsers());
        Collections.sort(list);
        return list;
    }

    public static ArrayList<CustomFilteredUser> getCustomFilteredUsersDataList() {
        HashMap<Long, CustomFilteredUser> map = getCustomFilteredUsersDataMap();
        ArrayList<Long> sortedIds = getCustomFilteredUsersList();
        ArrayList<CustomFilteredUser> list = new ArrayList<>(sortedIds.size());
        for (Long id : sortedIds) {
            if (id == null || id <= 0L) {
                continue;
            }
            CustomFilteredUser item = map.get(id);
            if (item == null) {
                item = new CustomFilteredUser();
                item.id = id;
            }
            list.add(item);
        }
        return list;
    }

    public static CustomFilteredUser getCustomFilteredUser(long userId) {
        if (userId <= 0L) {
            return null;
        }
        return getCustomFilteredUsersDataMap().get(userId);
    }

    public static void setCustomFilteredUsersData(ArrayList<CustomFilteredUser> users) {
        HashSet<Long> ids = new HashSet<>();
        HashMap<Long, CustomFilteredUser> map = new HashMap<>();
        if (users != null) {
            for (CustomFilteredUser item : users) {
                if (item != null && item.id > 0L) {
                    ids.add(item.id);
                    map.put(item.id, item);
                }
            }
        }
        saveCustomFilteredUsers(ids, map);
    }

    public static void setCustomFilteredUsers(ArrayList<Long> ids) {
        HashSet<Long> set = new HashSet<>();
        HashMap<Long, CustomFilteredUser> current = new HashMap<>(getCustomFilteredUsersDataMap());
        HashMap<Long, CustomFilteredUser> data = new HashMap<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null && id > 0L) {
                    set.add(id);
                    CustomFilteredUser item = current.get(id);
                    if (item == null) {
                        item = new CustomFilteredUser();
                        item.id = id;
                    }
                    data.put(id, item);
                }
            }
        }
        saveCustomFilteredUsers(set, data);
    }

    public static void updateCustomFilteredUserFromLocalUser(TLRPC.User user) {
        if (user == null || user.id <= 0L || !getCustomFilteredUsers().contains(user.id)) {
            return;
        }
        HashSet<Long> ids = new HashSet<>(getCustomFilteredUsers());
        HashMap<Long, CustomFilteredUser> map = new HashMap<>(getCustomFilteredUsersDataMap());
        CustomFilteredUser current = map.get(user.id);
        if (current == null) {
            current = new CustomFilteredUser();
            current.id = user.id;
        }
        boolean changed = false;
        String username = UserObject.getPublicUsername(user);
        String displayName = UserObject.getUserName(user);
        if (user.access_hash != 0L && current.accessHash != user.access_hash) {
            current.accessHash = user.access_hash;
            changed = true;
        }
        if (!TextUtils.equals(current.username, username)) {
            current.username = username;
            changed = true;
        }
        if (!TextUtils.equals(current.displayName, displayName)) {
            current.displayName = displayName;
            changed = true;
        }
        if (changed) {
            map.put(user.id, current);
            saveCustomFilteredUsers(ids, map);
        }
    }

    public static class FilterModel {
        public static final int ACTION_HIDE = 0;
        public static final int ACTION_SPOILER_ALL = 1;
        public static final int ACTION_SPOILER_MATCH = 2;

        @Expose
        public int filterAction = ACTION_HIDE;
        @Expose
        public int spoilerColor = 0xFFFFFFFF;
        @Expose
        public String regex;
        @Expose
        public boolean caseInsensitive;
        @Expose
        public boolean enabled = true;
        public Pattern pattern;

        // Legacy fields for deserialization migration only
        public ArrayList<Long> enabledGroups;
        public ArrayList<Long> disabledGroups;

        public void buildPattern() {
            var flags = Pattern.MULTILINE;
            if (caseInsensitive) {
                flags |= Pattern.CASE_INSENSITIVE;
            }
            try {
                pattern = Pattern.compile(regex, flags);
            } catch (Exception e) {
                pattern = null;
                FileLog.e(e);
            }
        }

        public boolean migrateFromLegacy(long dialogId) {
            if (enabledGroups == null && disabledGroups == null) {
                return false;
            }
            boolean defaultEnabled = enabledGroups != null && enabledGroups.contains(0L);
            if (defaultEnabled) {
                enabled = disabledGroups == null || !disabledGroups.contains(dialogId);
            } else {
                enabled = enabledGroups != null && enabledGroups.contains(dialogId);
            }
            enabledGroups = null;
            disabledGroups = null;
            return true;
        }
    }

    public static class ChatFilterEntry {
        @Expose
        public long dialogId;
        @Expose
        public ArrayList<FilterModel> filters;
    }

    public static class CustomFilteredUser {
        @Expose
        public long id;
        @Expose
        public long accessHash;
        @Expose
        public String username;
        @Expose
        public String displayName;
        @Expose
        public int filterAction = FilterModel.ACTION_HIDE;
        @Expose
        public int spoilerColor = 0xFFFFFFFF;
    }
}
