package tw.nekomimi.nekogram.helpers;

import android.text.TextUtils;

import androidx.collection.LruCache;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import xyz.nextalone.nagram.NaConfig;

public class AyuFilter {
    private static final Object cacheLock = new Object();
    private static final int PER_DIALOG_CACHE_LIMIT = 1000;
    private static final ConcurrentHashMap<Long, LruCache<Integer, Boolean>> filteredCache = new ConcurrentHashMap<>();
    private static volatile ArrayList<FilterModel> filterModels;
    private static volatile ArrayList<ChatFilterEntry> chatFilterEntries;
    private static volatile HashSet<Long> excludedDialogs;

    public static ArrayList<FilterModel> getRegexFilters() {
        if (filterModels == null) {
            synchronized (cacheLock) {
                if (filterModels == null) {
                    var str = NaConfig.INSTANCE.getRegexFiltersData().String();
                    FilterModel[] arr = new Gson().fromJson(str, FilterModel[].class);
                    if (arr != null) {
                        filterModels = new ArrayList<>(Arrays.asList(arr));
                        for (var filter : filterModels) {
                            filter.buildPattern();
                        }
                    } else {
                        filterModels = new ArrayList<>();
                    }
                }
            }
        }
        return filterModels;
    }

    public static void addFilter(String text, boolean caseInsensitive) {
        var list = new ArrayList<>(getRegexFilters());
        FilterModel filterModel = new FilterModel();
        filterModel.regex = text;
        filterModel.caseInsensitive = caseInsensitive;
        filterModel.enabledGroups = new ArrayList<>();
        filterModel.disabledGroups = new ArrayList<>();
        filterModel.enabledGroups.add(0L);
        list.add(0, filterModel);
        saveFilter(list);
    }

    public static void editFilter(int filterIdx, String text, boolean caseInsensitive) {
        var list = new ArrayList<>(getRegexFilters());
        if (filterIdx < 0 || filterIdx >= list.size()) {
            return;
        }
        FilterModel filterModel = list.get(filterIdx);
        filterModel.regex = text;
        filterModel.caseInsensitive = caseInsensitive;
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
        CharSequence messageText = MessageHelper.getMessagePlainText(selectedObject, selectedObjectGroup);
        if (messageText != null && Emoji.fullyConsistsOfEmojis(messageText)) {
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

    private static boolean isFilteredInternal(CharSequence text, long dialogId) {
        if (chatFilterEntries != null) {
            for (var entry : chatFilterEntries) {
                if (entry.dialogId == dialogId) {
                    if (entry.filters != null) {
                        for (var pattern : entry.filters) {
                            if (!pattern.isEnabled(dialogId)) {
                                continue;
                            }
                            if (pattern.pattern != null && pattern.pattern.matcher(text).find()) {
                                return true;
                            }
                        }
                    }
                    break;
                }
            }
        }

        boolean isPrivateDialog = dialogId > 0;
        if (isPrivateDialog && !NaConfig.INSTANCE.getRegexFiltersEnableInChats().Bool()) {
            return false;
        }

        if (filterModels != null) {
            for (var pattern : filterModels) {
                if (!pattern.isEnabled(dialogId)) {
                    continue;
                }
                if (pattern.pattern.matcher(text).find()) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isFiltered(MessageObject msg, MessageObject.GroupedMessages group) {
        if (!NaConfig.INSTANCE.getRegexFiltersEnabled().Bool()) {
            return false;
        }

        if (msg == null || msg.isOutOwner()) {
            return false;
        }

        var text = getMessageText(msg, group);
        if (TextUtils.isEmpty(text)) {
            return false;
        }

        if (filterModels == null) {
            getRegexFilters();
        }
        if (chatFilterEntries == null) {
            getChatFilterEntries();
        }

        long dialogId = msg.getDialogId();
        if (isDialogExcluded(dialogId)) {
            return false;
        }

        LruCache<Integer, Boolean> dialogCache = filteredCache.computeIfAbsent(dialogId, k -> new LruCache<>(PER_DIALOG_CACHE_LIMIT));
        Boolean result;

        synchronized (dialogCache) {
            result = dialogCache.get(msg.getId());
        }

        if (result != null) {
            return result;
        }

        result = isFilteredInternal(text, dialogId);

        synchronized (dialogCache) {
            dialogCache.put(msg.getId(), result);
            if (group != null && group.messages != null && !group.messages.isEmpty()) {
                for (var m : group.messages) {
                    dialogCache.put(m.getId(), result);
                }
            }
        }

        return result;
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
                            for (var entry : chatFilterEntries) {
                                if (entry.filters == null) continue;
                                for (var f : entry.filters) {
                                    f.buildPattern();
                                }
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

    public static void addChatFilter(long dialogId, String text, boolean caseInsensitive) {
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
        filterModel.enabledGroups = new ArrayList<>();
        filterModel.disabledGroups = new ArrayList<>();
        filterModel.enabledGroups.add(0L);
        if (target.filters == null) {
            target.filters = new ArrayList<>();
        }
        target.filters.add(0, filterModel);

        saveChatFilterEntries(entries);
    }

    public static void editChatFilter(long dialogId, int filterIdx, String text, boolean caseInsensitive) {
        var entries = new ArrayList<>(getChatFilterEntries());
        for (var e : entries) {
            if (e.dialogId == dialogId) {
                if (e.filters != null && filterIdx >= 0 && filterIdx < e.filters.size()) {
                    var fm = e.filters.get(filterIdx);
                    fm.regex = text;
                    fm.caseInsensitive = caseInsensitive;
                    saveChatFilterEntries(entries);
                }
                return;
            }
        }
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
        try {
            NaConfig.INSTANCE.getRegexFiltersData().setConfigString("[]");
            NaConfig.INSTANCE.getRegexChatFiltersData().setConfigString("[]");
            NaConfig.INSTANCE.getRegexFiltersExcludedDialogs().setConfigString("[]");
            rebuildCache();
        } catch (Exception ignored) {
        }
    }

    public static void onMessageEdited(int msgId, long dialogId) {
        var dialogCache = filteredCache.get(dialogId);
        if (dialogCache != null) {
            synchronized (dialogCache) {
                dialogCache.remove(msgId);
            }
        }
    }

    /*public static int getLastFilteredMessageId(long dialogId) {
        var cached = filteredCache.get(dialogId);
        if (cached != null) {
            int lastId = -1;
            for (var entry : cached.entrySet()) {
                if (entry.getValue()) {
                    if (entry.getKey() > lastId) {
                        lastId = entry.getKey();
                    }
                }
            }
            return lastId;
        }
        return -1;
    }*/

    public static class FilterModel {
        @Expose
        public String regex;
        @Expose
        public boolean caseInsensitive;
        @Expose
        public ArrayList<Long> enabledGroups;
        @Expose
        public ArrayList<Long> disabledGroups;
        public Pattern pattern;

        public void buildPattern() {
            var flags = Pattern.MULTILINE;
            if (caseInsensitive) {
                flags |= Pattern.CASE_INSENSITIVE;
            }
            pattern = Pattern.compile(regex, flags);
        }

        public boolean defaultStatus() {
            return enabledGroups.contains(0L);
        }

        public boolean isEnabled(Long id) {
            boolean status = defaultStatus();
            if (id == 0L) return status;
            if (status) {
                if (disabledGroups.contains(id)) {
                    return false;
                }
            } else {
                if (enabledGroups.contains(id)) {
                    return true;
                }
            }
            return status;
        }

        public void setEnabled(boolean enabled, Long id) {
            enabledGroups.remove(id);
            disabledGroups.remove(id);
            if (enabled) {
                enabledGroups.add(id);
            } else {
                disabledGroups.add(id);
            }
        }
    }

    public static class ChatFilterEntry {
        @Expose
        public long dialogId;
        @Expose
        public ArrayList<FilterModel> filters;
    }
}
