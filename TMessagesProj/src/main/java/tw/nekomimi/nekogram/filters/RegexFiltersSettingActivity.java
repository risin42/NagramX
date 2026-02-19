package tw.nekomimi.nekogram.filters;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.HashMap;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.FiltersChatCell;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import tw.nekomimi.nekogram.utils.AlertUtil;
import xyz.nextalone.nagram.NaConfig;

public class RegexFiltersSettingActivity extends BaseNekoSettingsActivity {

    private int filtersOptionHeaderRow;
    private int regexFiltersEnableInChatsRow;
    private int ignoreBlockedRow;
    private int filtersOptionDividerRow;
    private int filtersHeaderRow;
    private int sharedFiltersPageRow;
    private int userFiltersPageRow;
    private int dividerRow;
    private int chatFiltersHeaderRow;
    private int chatFiltersStartRow;
    private int chatFiltersEndRow;
    private int addChatFilterBtnRow;

    public RegexFiltersSettingActivity() {
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        filtersOptionHeaderRow = -1;
        regexFiltersEnableInChatsRow = -1;
        ignoreBlockedRow = -1;
        filtersOptionDividerRow = -1;
        filtersHeaderRow = -1;
        sharedFiltersPageRow = -1;
        userFiltersPageRow = -1;
        dividerRow = -1;
        chatFiltersHeaderRow = -1;
        chatFiltersStartRow = -1;
        chatFiltersEndRow = -1;
        addChatFilterBtnRow = -1;

        filtersOptionHeaderRow = rowCount++;
        regexFiltersEnableInChatsRow = rowCount++;
        ignoreBlockedRow = rowCount++;
        filtersOptionDividerRow = rowCount++;

        filtersHeaderRow = rowCount++;
        sharedFiltersPageRow = rowCount++;
        userFiltersPageRow = rowCount++;
        dividerRow = rowCount++;
        // Chat-specific filters section
        chatFiltersHeaderRow = rowCount++;
        addChatFilterBtnRow = rowCount++;
        var chatEntries = checkChatFilters(AyuFilter.getChatFilterEntries());
        chatFiltersStartRow = rowCount;
        rowCount += chatEntries.size();
        chatFiltersEndRow = rowCount;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();

        updateRows();

        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @SuppressWarnings("NewApi")
    @Override
    public View createView(Context context) {
        View v = super.createView(context);

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItem.addSubItem(1, R.drawable.msg_photo_settings_solar, getString(R.string.RegexFiltersImport));
        menuItem.addSubItem(2, R.drawable.msg_instant_link_solar, getString(R.string.RegexFiltersExport));
        menuItem.addColoredGap();
        ActionBarMenuSubItem clearSub = menuItem.addSubItem(3, R.drawable.msg_clear, getString(R.string.ClearRegexFilters));
        int red = Theme.getColor(Theme.key_text_RedRegular);
        clearSub.setColors(red, red);
        clearSub.setSelectorColor(Theme.multAlpha(red, .12f));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
                if (id == 1) { // Import
                    try {
                        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        CharSequence text = null;
                        if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null && clipboard.getPrimaryClip().getItemCount() > 0) {
                            text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(context);
                        }
                        if (text == null) {
                            AlertUtil.showToast("empty data");
                            return;
                        }
                        String json = text.toString().trim();
                        long selfUserId = getUserConfig().getClientUserId();
                        ArrayList<AyuFilter.FilterModel> sharedIncoming = null;
                        ArrayList<AyuFilter.ChatFilterEntry> chatsIncoming = null;
                        ArrayList<AyuFilter.CustomFilteredUser> customFilteredUsersIncoming = null;
                        if (json.startsWith("[")) {
                            AyuFilter.FilterModel[] arr = new Gson().fromJson(json, AyuFilter.FilterModel[].class);
                            if (arr != null) {
                                sharedIncoming = new ArrayList<>();
                                for (AyuFilter.FilterModel m : arr) {
                                    if (m == null || m.regex == null) continue;
                                    m.migrateFromLegacy(0L);
                                    sharedIncoming.add(m);
                                }
                            }
                        } else {
                            TransferData data = new Gson().fromJson(json, TransferData.class);
                            if (data != null) {
                                if (data.shared != null) {
                                    sharedIncoming = new ArrayList<>();
                                    for (AyuFilter.FilterModel m : data.shared) {
                                        if (m == null || m.regex == null) continue;
                                        m.migrateFromLegacy(0L);
                                        sharedIncoming.add(m);
                                    }
                                }
                                if (data.chats != null) {
                                    chatsIncoming = new ArrayList<>();
                                    for (AyuFilter.ChatFilterEntry e1 : data.chats) {
                                        if (e1 == null) continue;
                                        if (e1.filters == null) e1.filters = new ArrayList<>();
                                        ArrayList<AyuFilter.FilterModel> fixed = new ArrayList<>();
                                        for (AyuFilter.FilterModel m : e1.filters) {
                                            if (m == null || m.regex == null) continue;
                                            m.migrateFromLegacy(e1.dialogId);
                                            fixed.add(m);
                                        }
                                        e1.filters = fixed;
                                        chatsIncoming.add(e1);
                                    }
                                }
                                if (data.customFilteredUsersData != null) {
                                    customFilteredUsersIncoming = new ArrayList<>();
                                    for (AyuFilter.CustomFilteredUser user : data.customFilteredUsersData) {
                                        if (user == null || user.id <= 0L || user.id == selfUserId) {
                                            continue;
                                        }
                                        AyuFilter.CustomFilteredUser normalized = new AyuFilter.CustomFilteredUser();
                                        normalized.id = user.id;
                                        normalized.accessHash = user.accessHash;
                                        normalized.username = user.username;
                                        if (!TextUtils.isEmpty(user.displayName)) {
                                            String displayName = user.displayName.trim();
                                            if (!TextUtils.isEmpty(displayName)) {
                                                normalized.displayName = displayName;
                                            }
                                        }
                                        customFilteredUsersIncoming.add(normalized);
                                    }
                                } else if (data.customFilteredUsers != null) {
                                    customFilteredUsersIncoming = new ArrayList<>();
                                    for (Long userId : data.customFilteredUsers) {
                                        if (userId == null || userId <= 0L || userId == selfUserId) {
                                            continue;
                                        }
                                        AyuFilter.CustomFilteredUser normalized = new AyuFilter.CustomFilteredUser();
                                        normalized.id = userId;
                                        customFilteredUsersIncoming.add(normalized);
                                    }
                                }
                            }
                        }
                        if ((sharedIncoming == null || sharedIncoming.isEmpty())
                            && (chatsIncoming == null || chatsIncoming.isEmpty())
                            && (customFilteredUsersIncoming == null || customFilteredUsersIncoming.isEmpty())
                        ) {
                            BulletinFactory.of(RegexFiltersSettingActivity.this).createSimpleBulletin(R.raw.error, getString(R.string.RegexFiltersImportError)).show();
                            return;
                        }
                        if (sharedIncoming != null && !sharedIncoming.isEmpty()) {
                            ArrayList<AyuFilter.FilterModel> currentShared = AyuFilter.getRegexFilters();
                            for (AyuFilter.FilterModel in : sharedIncoming) {
                                boolean found = false;
                                for (int i = 0; i < currentShared.size(); i++) {
                                    AyuFilter.FilterModel ex = currentShared.get(i);
                                    if (ex != null && ex.regex != null && ex.regex.equals(in.regex) && ex.caseInsensitive == in.caseInsensitive) {
                                        ex.enabled = in.enabled;
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    currentShared.add(in);
                                }
                            }
                            AyuFilter.saveFilter(currentShared);
                        }
                        if (chatsIncoming != null && !chatsIncoming.isEmpty()) {
                            ArrayList<AyuFilter.ChatFilterEntry> currentChats = checkChatFilters(AyuFilter.getChatFilterEntries());
                            for (AyuFilter.ChatFilterEntry inEntry : chatsIncoming) {
                                if (inEntry == null) continue;
                                AyuFilter.ChatFilterEntry target = null;
                                for (AyuFilter.ChatFilterEntry exEntry : currentChats) {
                                    if (exEntry != null && exEntry.dialogId == inEntry.dialogId) {
                                        target = exEntry;
                                        break;
                                    }
                                }
                                if (target == null) {
                                    AyuFilter.ChatFilterEntry newEntry = new AyuFilter.ChatFilterEntry();
                                    newEntry.dialogId = inEntry.dialogId;
                                    newEntry.filters = new ArrayList<>();
                                    currentChats.add(newEntry);
                                    target = newEntry;
                                }
                                if (inEntry.filters != null) {
                                    for (AyuFilter.FilterModel in : inEntry.filters) {
                                        boolean found = false;
                                        if (target.filters == null)
                                            target.filters = new ArrayList<>();
                                        for (int i = 0; i < target.filters.size(); i++) {
                                            AyuFilter.FilterModel ex = target.filters.get(i);
                                            if (ex != null && ex.regex != null && ex.regex.equals(in.regex) && ex.caseInsensitive == in.caseInsensitive) {
                                                ex.enabled = in.enabled;
                                                found = true;
                                                break;
                                            }
                                        }
                                        if (!found) {
                                            target.filters.add(in);
                                        }
                                    }
                                }
                            }
                            AyuFilter.saveChatFilterEntries(currentChats);
                        }
                        if (customFilteredUsersIncoming != null && !customFilteredUsersIncoming.isEmpty()) {
                            HashMap<Long, AyuFilter.CustomFilteredUser> merged = new HashMap<>();
                            for (AyuFilter.CustomFilteredUser existing : AyuFilter.getCustomFilteredUsersDataList()) {
                                if (existing == null || existing.id <= 0L || existing.id == selfUserId) {
                                    continue;
                                }
                                merged.put(existing.id, existing);
                            }
                            for (AyuFilter.CustomFilteredUser incoming : customFilteredUsersIncoming) {
                                if (incoming == null || incoming.id <= 0L || incoming.id == selfUserId) {
                                    continue;
                                }
                                AyuFilter.CustomFilteredUser target = merged.get(incoming.id);
                                if (target == null) {
                                    target = new AyuFilter.CustomFilteredUser();
                                    target.id = incoming.id;
                                }
                                if (incoming.accessHash != 0L) {
                                    target.accessHash = incoming.accessHash;
                                }
                                String username = incoming.username;
                                if (!TextUtils.isEmpty(username)) {
                                    target.username = username;
                                }
                                if (!TextUtils.isEmpty(incoming.displayName)) {
                                    String displayName = incoming.displayName.trim();
                                    if (!TextUtils.isEmpty(displayName)) {
                                        target.displayName = displayName;
                                    }
                                }
                                merged.put(target.id, target);
                            }
                            AyuFilter.setCustomFilteredUsersData(new ArrayList<>(merged.values()));
                        }
                        refreshRows();
                        BulletinFactory.of(RegexFiltersSettingActivity.this).createSimpleBulletin(R.raw.done, getString(R.string.RegexFiltersImportSuccess)).show();
                    } catch (Exception e) {
                        FileLog.e(e);
                        BulletinFactory.of(RegexFiltersSettingActivity.this).createSimpleBulletin(R.raw.error, getString(R.string.RegexFiltersImportError)).show();
                    }
                } else if (id == 2) { // Export
                    try {
                        TransferData data = new TransferData();
                        data.shared = AyuFilter.getRegexFilters();
                        data.chats = checkChatFilters(AyuFilter.getChatFilterEntries());
                        data.customFilteredUsersData = AyuFilter.getCustomFilteredUsersDataList();
                        String json = new Gson().toJson(data);
                        AndroidUtilities.addToClipboard(json);
                        BulletinFactory.of(RegexFiltersSettingActivity.this).createCopyLinkBulletin().show();
                    } catch (Exception e) {
                        FileLog.e(e);
                        BulletinFactory.of(RegexFiltersSettingActivity.this).createSimpleBulletin(R.raw.error, getString(R.string.RegexFiltersExportError)).show();
                    }
                } else if (id == 3) {
                    new AlertDialog.Builder(getContext(), getResourceProvider())
                        .setTitle(getString(R.string.ClearRegexFilters))
                        .setMessage(getString(R.string.ClearRegexFiltersAlertMessage))
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .setPositiveButton(getString(R.string.Clear), (dialog, which) -> {
                            AyuFilter.clearAllFilters();
                            refreshRows();
                        })
                        .makeRed(AlertDialog.BUTTON_POSITIVE)
                        .show();
                }
            }
        });
        return v;
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == regexFiltersEnableInChatsRow) {
            TextCheckCell cell = (TextCheckCell) view;
            boolean enabled = !cell.isChecked();
            cell.setChecked(enabled);
            NaConfig.INSTANCE.getRegexFiltersEnableInChats().setConfigBool(enabled);
            AyuFilter.invalidateFilteredCache();
        } else if (position == ignoreBlockedRow) {
            TextCheckCell cell = (TextCheckCell) view;
            boolean enabled = !cell.isChecked();
            cell.setChecked(enabled);
            NekoConfig.ignoreBlocked.setConfigBool(enabled);
        } else if (position == sharedFiltersPageRow) {
            presentFragment(new RegexSharedFiltersListActivity());
        } else if (position == userFiltersPageRow) {
            presentFragment(new ShadowBanListActivity());
        } else if (position == addChatFilterBtnRow) {
            presentFragment(getDialogsActivity());
        } else if (position >= chatFiltersStartRow && position < chatFiltersEndRow) {
            int idx = position - chatFiltersStartRow;
            var chatEntries = checkChatFilters(AyuFilter.getChatFilterEntries());
            if (idx >= 0 && idx < chatEntries.size()) {
                long did = chatEntries.get(idx).dialogId;
                presentFragment(new RegexChatFiltersListActivity(did));
            }
        }
    }

    @NonNull
    private DialogsActivity getDialogsActivity() {
        Bundle b = new Bundle();
        b.putBoolean("onlySelect", true);
        b.putBoolean("allowGlobalSearch", false);
        b.putBoolean("checkCanWrite", false);
        DialogsActivity activity = new DialogsActivity(b);
        activity.setDelegate((fragment, did, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (did != null && !did.isEmpty()) {
                long dialogId = did.get(0).dialogId;
                parentLayout.removeFragmentFromStack(fragment, true);
                presentFragment(new RegexFilterEditActivity(dialogId));
                return true;
            }
            return false;
        });
        return activity;
    }

    private ArrayList<AyuFilter.ChatFilterEntry> checkChatFilters(ArrayList<AyuFilter.ChatFilterEntry> chatEntries) {
        if (chatEntries == null || chatEntries.isEmpty()) return chatEntries;
        ArrayList<AyuFilter.ChatFilterEntry> newEntries = new ArrayList<>();
        for (AyuFilter.ChatFilterEntry entry : chatEntries) {
            if (entry == null) continue;
            if (entry.dialogId > 0) {
                TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(entry.dialogId);
                if (user != null) {
                    newEntries.add(entry);
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-entry.dialogId);
                if (chat != null) {
                    newEntries.add(entry);
                }
            }
        }
        return newEntries;
    }

    @SuppressLint("NotifyDataSetChanged")
    private void refreshRows() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        return super.onItemLongClick(view, position, x, y);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.RegexFilters);
    }

    private static class TransferData {
        public ArrayList<AyuFilter.FilterModel> shared;
        public ArrayList<AyuFilter.ChatFilterEntry> chats;
        public ArrayList<AyuFilter.CustomFilteredUser> customFilteredUsersData;
        public ArrayList<Long> customFilteredUsers;
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_ACCOUNT) {
                FiltersChatCell chatCell = new FiltersChatCell(mContext);
                chatCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                chatCell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(chatCell);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_SHADOW:
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case TYPE_CHECK:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    if (position == regexFiltersEnableInChatsRow) {
                        textCheckCell.setTextAndCheck(getString(R.string.RegexFiltersEnableInChats), NaConfig.INSTANCE.getRegexFiltersEnableInChats().Bool(), true);
                    } else if (position == ignoreBlockedRow) {
                        textCheckCell.setTextAndCheck(getString(R.string.IgnoreBlocked), NekoConfig.ignoreBlocked.Bool(), true);
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    textCell.setTextAndIcon(getString(R.string.RegexFiltersAdd), R.drawable.msg_add, chatFiltersStartRow < chatFiltersEndRow);
                    break;
                case TYPE_SETTINGS:
                    TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
                    if (position == sharedFiltersPageRow) {
                        settingsCell.setTextAndValue(getString(R.string.RegexFiltersSharedHeader), String.valueOf(AyuFilter.getRegexFilters().size()), true);
                    } else if (position == userFiltersPageRow) {
                        settingsCell.setTextAndValue(getString(R.string.ShadowBan), String.valueOf(AyuFilter.getCustomFilteredUsersList().size()), false);
                    }
                    break;
                case TYPE_ACCOUNT:
                    if (position >= chatFiltersStartRow && position < chatFiltersEndRow) {
                        int idx = position - chatFiltersStartRow;
                        var chatEntries = checkChatFilters(AyuFilter.getChatFilterEntries());
                        if (idx >= 0 && idx < chatEntries.size()) {
                            var entry = chatEntries.get(idx);
                            long did = entry.dialogId;
                            int count = entry.filters != null ? entry.filters.size() : 0;
                            FiltersChatCell chatCell = (FiltersChatCell) holder.itemView;
                            chatCell.setDialog(did, count);
                        }
                    }
                    break;
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == filtersOptionHeaderRow) {
                        headerCell.setText(getString(R.string.General));
                    } else if (position == filtersHeaderRow) {
                        headerCell.setText(getString(R.string.RegexFiltersGlobalHeader));
                    } else if (position == chatFiltersHeaderRow) {
                        headerCell.setText(getString(R.string.RegexFiltersChatHeader));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == filtersOptionDividerRow || position == dividerRow) {
                return TYPE_SHADOW;
            } else if (position == filtersHeaderRow || position == filtersOptionHeaderRow || position == chatFiltersHeaderRow) {
                return TYPE_HEADER;
            } else if (position == sharedFiltersPageRow || position == userFiltersPageRow) {
                return TYPE_SETTINGS;
            } else if (position == addChatFilterBtnRow) {
                return TYPE_TEXT;
            } else if (position >= chatFiltersStartRow && position < chatFiltersEndRow) {
                return TYPE_ACCOUNT;
            }
            return TYPE_CHECK;
        }
    }
}
