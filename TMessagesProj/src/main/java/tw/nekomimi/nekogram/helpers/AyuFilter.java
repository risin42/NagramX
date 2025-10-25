package tw.nekomimi.nekogram.helpers;

import android.text.TextUtils;
import android.util.LongSparseArray;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;

import xyz.nextalone.nagram.NaConfig;

public class AyuFilter {
    private static ArrayList<FilterModel> filterModels;
    private static ArrayList<ChatFilterEntry> chatFilterEntries;
    private static LongSparseArray<HashMap<Integer, Boolean>> filteredCache;
    private static HashSet<Long> excludedDialogs;

    public static ArrayList<FilterModel> getRegexFilters() {
        var str = NaConfig.INSTANCE.getRegexFiltersData().String();

        FilterModel[] arr = new Gson().fromJson(str, FilterModel[].class);

        return new ArrayList<>(Arrays.asList(arr));
    }

    public static void addFilter(String text, boolean caseInsensitive) {
        var list = getRegexFilters();

        FilterModel filterModel = new FilterModel();
        filterModel.regex = text;
        filterModel.caseInsensitive = caseInsensitive;
        filterModel.enabledGroups = new ArrayList<>();
        filterModel.disabledGroups = new ArrayList<>();
        filterModel.enabledGroups.add(0L);
        list.add(0, filterModel);
        var str = new Gson().toJson(list);
        NaConfig.INSTANCE.getRegexFiltersData().setConfigString(str);

        AyuFilter.rebuildCache();
    }

    public static void editFilter(int filterIdx, String text, boolean caseInsensitive) {
        var list = getRegexFilters();

        FilterModel filterModel = list.get(filterIdx);
        filterModel.regex = text;
        filterModel.caseInsensitive = caseInsensitive;

        var str = new Gson().toJson(list);
        NaConfig.INSTANCE.getRegexFiltersData().setConfigString(str);

        AyuFilter.rebuildCache();
    }

    public static void saveFilter(ArrayList<FilterModel> filterModels1) {
        var str = new Gson().toJson(filterModels1);
        NaConfig.INSTANCE.getRegexFiltersData().setConfigString(str);

        AyuFilter.rebuildCache();
    }

    public static void removeFilter(int filterIdx) {
        var list = getRegexFilters();
        list.remove(filterIdx);

        var str = new Gson().toJson(list);
        NaConfig.INSTANCE.getRegexFiltersData().setConfigString(str);

        AyuFilter.rebuildCache();
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
        filterModels = getRegexFilters();

        for (var filter : filterModels) {
            filter.buildPattern();
        }

        chatFilterEntries = getChatFilterEntries();
        for (var entry : chatFilterEntries) {
            if (entry.filters == null) continue;
            for (var f : entry.filters) {
                f.buildPattern();
            }
        }

        filteredCache = new LongSparseArray<>();
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

        for (var pattern : filterModels) {
            if (!pattern.isEnabled(dialogId)) {
                continue;
            }
            if (pattern.pattern.matcher(text).find()) {
                return true;
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
            rebuildCache();
        }

        long dialogId = msg.getDialogId();
        if (isDialogExcluded(dialogId)) {
            return false;
        }

        Boolean result;

        var cachedResult = filteredCache.get(dialogId);
        if (cachedResult != null) {
            result = cachedResult.get(msg.getId());
            if (result != null) {
                return result;
            }
        }

        result = isFilteredInternal(text, dialogId);

        if (cachedResult == null) {
            cachedResult = new HashMap<>();
            filteredCache.put(dialogId, cachedResult);
        }

        cachedResult.put(msg.getId(), result);

        if (group != null && group.messages != null && !group.messages.isEmpty()) {
            for (var m : group.messages) {
                cachedResult.put(m.getId(), result);
            }
        }

        return result;
    }

    public static ArrayList<ChatFilterEntry> getChatFilterEntries() {
        var str = NaConfig.INSTANCE.getRegexChatFiltersData().String();
        try {
            ChatFilterEntry[] arr = new Gson().fromJson(str, ChatFilterEntry[].class);
            return new ArrayList<>(Arrays.asList(arr));
        } catch (Exception e) {
            return new ArrayList<>();
        }
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
        var entries = getChatFilterEntries();
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
            target.filters = new ArrayList<>();
            entries.add(target);
        }

        FilterModel filterModel = new FilterModel();
        filterModel.regex = text;
        filterModel.caseInsensitive = caseInsensitive;
        filterModel.enabledGroups = new ArrayList<>();
        filterModel.disabledGroups = new ArrayList<>();
        filterModel.enabledGroups.add(0L);
        target.filters.add(0, filterModel);

        saveChatFilterEntries(entries);
    }

    public static void editChatFilter(long dialogId, int filterIdx, String text, boolean caseInsensitive) {
        var entries = getChatFilterEntries();
        for (var e : entries) {
            if (e.dialogId == dialogId) {
                if (e.filters != null && filterIdx >= 0 && filterIdx < e.filters.size()) {
                    var fm = e.filters.get(filterIdx);
                    fm.regex = text;
                    fm.caseInsensitive = caseInsensitive;
                    saveChatFilterEntries(entries);
                    return;
                }
            }
        }
    }

    public static void removeChatFilter(long dialogId, int filterIdx) {
        var entries = getChatFilterEntries();
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            if (e.dialogId == dialogId) {
                if (e.filters != null && filterIdx >= 0 && filterIdx < e.filters.size()) {
                    e.filters.remove(filterIdx);
                    if (e.filters.isEmpty()) {
                        entries.remove(i);
                    }
                    saveChatFilterEntries(entries);
                    return;
                }
            }
        }
    }

    private static HashSet<Long> getExcludedDialogs() {
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
        return excludedDialogs;
    }

    public static boolean isDialogExcluded(long dialogId) {
        return getExcludedDialogs().contains(dialogId);
    }

    public static void setDialogExcluded(long dialogId, boolean excluded) {
        HashSet<Long> set = getExcludedDialogs();
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
            if (filteredCache != null) {
                filteredCache.remove(dialogId);
            }
        }
    }

    public static void clearAllFilters() {
        try {
            String emptyFilters = new Gson().toJson(new FilterModel[0]);
            NaConfig.INSTANCE.getRegexFiltersData().setConfigString(emptyFilters);

            String emptyChatEntries = new Gson().toJson(new ChatFilterEntry[0]);
            NaConfig.INSTANCE.getRegexChatFiltersData().setConfigString(emptyChatEntries);

            String emptyExcluded = new Gson().toJson(new Long[0]);
            NaConfig.INSTANCE.getRegexFiltersExcludedDialogs().setConfigString(emptyExcluded);

            AyuFilter.rebuildCache();
        } catch (Exception ignored) {
        }
    }

    public static void onMessageEdited(int msgId, long dialogId) {
        var cached = filteredCache.get(dialogId);
        if (cached != null) {
            cached.remove(msgId);
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
