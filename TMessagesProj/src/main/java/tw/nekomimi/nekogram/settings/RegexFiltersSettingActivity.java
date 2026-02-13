/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
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
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.AyuFilter;
import tw.nekomimi.nekogram.ui.RegexChatFiltersListActivity;
import tw.nekomimi.nekogram.ui.RegexFilterEditActivity;
import tw.nekomimi.nekogram.ui.RegexFilterPopup;
import tw.nekomimi.nekogram.ui.RegexUserFilterPopup;
import tw.nekomimi.nekogram.ui.cells.ChatRowCell;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import tw.nekomimi.nekogram.utils.AlertUtil;
import xyz.nextalone.nagram.NaConfig;

public class RegexFiltersSettingActivity extends BaseNekoSettingsActivity {

    private final long dialogId;
    private int filtersOptionHeaderRow;
    private int regexFiltersEnableInChatsRow;
    private int ignoreBlockedRow;
    private int filtersOptionDividerRow;
    private int filtersHeaderRow;
    private int addFilterBtnRow;
    private int filtersStartRow;
    private int filtersEndRow;
    private int dividerRow;
    private int chatFiltersHeaderRow;
    private int chatFiltersStartRow;
    private int chatFiltersEndRow;
    private int addChatFilterBtnRow;
    private int userFiltersDividerRow;
    private int userFiltersHeaderRow;
    private int addUserFilterBtnRow;
    private int userFiltersStartRow;
    private int userFiltersEndRow;

    public RegexFiltersSettingActivity() {
        dialogId = 0L;
    }

    public RegexFiltersSettingActivity(long dialogId) {
        this.dialogId = dialogId;
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        filtersOptionHeaderRow = rowCount++;
        regexFiltersEnableInChatsRow = rowCount++;
        ignoreBlockedRow = rowCount++;
        filtersOptionDividerRow = rowCount++;

        filtersHeaderRow = rowCount++;
        addFilterBtnRow = rowCount++;
        var filters = AyuFilter.getRegexFilters();
        filtersStartRow = rowCount;
        rowCount += filters.size();
        filtersEndRow = rowCount;
        dividerRow = rowCount++;
        // Chat-specific filters section
        chatFiltersHeaderRow = rowCount++;
        addChatFilterBtnRow = rowCount++;
        var chatEntries = checkChatFilters(AyuFilter.getChatFilterEntries());
        chatFiltersStartRow = rowCount;
        rowCount += chatEntries.size();
        chatFiltersEndRow = rowCount;
        userFiltersDividerRow = rowCount++;
        userFiltersHeaderRow = rowCount++;
        addUserFilterBtnRow = rowCount++;
        ArrayList<Long> userIds = AyuFilter.getCustomFilteredUsersList();
        userFiltersStartRow = rowCount;
        rowCount += userIds.size();
        userFiltersEndRow = rowCount;
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
        menuItem.setContentDescription(getString(R.string.AccDescrMoreOptions));
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
                        ArrayList<AyuFilter.FilterModel> sharedIncoming = null;
                        ArrayList<AyuFilter.ChatFilterEntry> chatsIncoming = null;
                        ArrayList<Long> customFilteredUsersIncoming = null;
                        if (json.startsWith("[")) {
                            AyuFilter.FilterModel[] arr = new Gson().fromJson(json, AyuFilter.FilterModel[].class);
                            if (arr != null) {
                                sharedIncoming = new ArrayList<>();
                                for (AyuFilter.FilterModel m : arr) {
                                    if (m == null || m.regex == null) continue;
                                    if (m.enabledGroups == null)
                                        m.enabledGroups = new ArrayList<>();
                                    if (m.disabledGroups == null)
                                        m.disabledGroups = new ArrayList<>();
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
                                        if (m.enabledGroups == null)
                                            m.enabledGroups = new ArrayList<>();
                                        if (m.disabledGroups == null)
                                            m.disabledGroups = new ArrayList<>();
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
                                            if (m.enabledGroups == null)
                                                m.enabledGroups = new ArrayList<>();
                                            if (m.disabledGroups == null)
                                                m.disabledGroups = new ArrayList<>();
                                            fixed.add(m);
                                        }
                                        e1.filters = fixed;
                                        chatsIncoming.add(e1);
                                    }
                                }
                                if (data.customFilteredUsers != null) {
                                    customFilteredUsersIncoming = new ArrayList<>();
                                    for (Long userId : data.customFilteredUsers) {
                                        if (userId != null && userId != 0L) {
                                            customFilteredUsersIncoming.add(userId);
                                        }
                                    }
                                }
                            }
                        }
                        if ((sharedIncoming == null || sharedIncoming.isEmpty())
                            && (chatsIncoming == null || chatsIncoming.isEmpty())
                            && (customFilteredUsersIncoming == null || customFilteredUsersIncoming.isEmpty())) {
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
                                        ex.enabledGroups = in.enabledGroups != null ? in.enabledGroups : new ArrayList<>();
                                        ex.disabledGroups = in.disabledGroups != null ? in.disabledGroups : new ArrayList<>();
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
                                                ex.enabledGroups = in.enabledGroups != null ? in.enabledGroups : new ArrayList<>();
                                                ex.disabledGroups = in.disabledGroups != null ? in.disabledGroups : new ArrayList<>();
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
                            HashSet<Long> merged = new HashSet<>(AyuFilter.getCustomFilteredUsersList());
                            merged.addAll(customFilteredUsersIncoming);
                            AyuFilter.setCustomFilteredUsers(new ArrayList<>(merged));
                        }
                        updateRows();
                        if (listAdapter != null) listAdapter.notifyDataSetChanged();
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
                        data.customFilteredUsers = AyuFilter.getCustomFilteredUsersList();
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
                            updateRows();
                            if (listAdapter != null) {
                                listAdapter.notifyDataSetChanged();
                            }
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
        } else if (position == ignoreBlockedRow) {
            TextCheckCell cell = (TextCheckCell) view;
            boolean enabled = !cell.isChecked();
            cell.setChecked(enabled);
            NekoConfig.ignoreBlocked.setConfigBool(enabled);
        } else if (position == addUserFilterBtnRow) {
            showEditCustomFilteredUserDialog(0L);
        } else if (position >= userFiltersStartRow && position < userFiltersEndRow) {
            int userIndex = position - userFiltersStartRow;
            ArrayList<Long> userIds = AyuFilter.getCustomFilteredUsersList();
            if (userIndex < 0 || userIndex >= userIds.size()) {
                return;
            }
            RegexUserFilterPopup.show(this, view, x, y, userIds.get(userIndex));
        } else if (position >= filtersStartRow && position < filtersEndRow) {
            ArrayList<AyuFilter.FilterModel> filterModels = AyuFilter.getRegexFilters();
            int filterIndex = position - filtersStartRow;
            if (filterIndex < 0 || filterIndex >= filterModels.size()) {
                return;
            }

            if (dialogId == 0 && LocaleController.isRTL && x > AndroidUtilities.dp(76) || !LocaleController.isRTL && x < (view.getMeasuredWidth() - AndroidUtilities.dp(76))) {
                RegexFilterPopup.show(this, view, x, y, filterIndex);
            } else {
                TextCheckCell textCheckCell = (TextCheckCell) view;
                AyuFilter.FilterModel filterModel = filterModels.get(filterIndex);

                boolean enabled = !textCheckCell.isChecked();
                textCheckCell.setChecked(enabled);
                filterModel.setEnabled(enabled, dialogId);
                AyuFilter.saveFilter(filterModels);
            }
        } else if (position == addFilterBtnRow) {
            presentFragment(new RegexFilterEditActivity());
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

    public void showEditCustomFilteredUserDialog(long originalUserId) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        boolean isEdit = originalUserId != 0L;
        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor));
        editText.setBackground(null);
        editText.setLineColors(
            Theme.getColor(Theme.key_windowBackgroundWhiteInputField),
            Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
            Theme.getColor(Theme.key_text_RedRegular)
        );
        editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        editText.setMinLines(1);
        editText.setMaxLines(1);
        editText.setHint(getString(R.string.RegexFiltersUserFilterHint));
        editText.setPadding(0, 0, 0, AndroidUtilities.dp(6));

        if (isEdit) {
            editText.setText(String.valueOf(originalUserId));
            editText.setSelection(editText.getText().length());
        }

        FrameLayout container = new FrameLayout(context);
        container.addView(editText, LayoutHelper.createFrame(
            LayoutHelper.MATCH_PARENT,
            LayoutHelper.WRAP_CONTENT,
            Gravity.TOP | Gravity.LEFT,
            24,
            8,
            24,
            0
        ));

        AlertDialog dialog = new AlertDialog.Builder(context, getResourceProvider())
            .setTitle(getString(isEdit ? R.string.RegexFiltersEditUserFilter : R.string.RegexFiltersAddUserFilter))
            .setView(container)
            .setNegativeButton(getString(R.string.Cancel), null)
            .setPositiveButton(getString(R.string.Save), null)
            .create();

        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String input = editText.getText() == null ? "" : editText.getText().toString();
            ParsedSingleIdResult result = parseSingleCustomFilteredUser(input);
            if (!result.valid) {
                BulletinFactory.of(RegexFiltersSettingActivity.this)
                    .createSimpleBulletin(R.raw.error, LocaleController.formatString(R.string.RegexFiltersUserFilterInvalid, result.invalidToken))
                    .show();
                return;
            }

            HashSet<Long> idSet = new HashSet<>(AyuFilter.getCustomFilteredUsersList());
            if (isEdit) {
                idSet.remove(originalUserId);
            }
            if (idSet.contains(result.userId)) {
                BulletinFactory.of(RegexFiltersSettingActivity.this)
                    .createSimpleBulletin(R.raw.error, getString(R.string.RegexFiltersUserFilterExists))
                    .show();
                return;
            }

            idSet.add(result.userId);
            ArrayList<Long> updated = new ArrayList<>(idSet);
            Collections.sort(updated);
            AyuFilter.setCustomFilteredUsers(updated);
            refreshRows();
            BulletinFactory.of(RegexFiltersSettingActivity.this)
                .createSimpleBulletin(R.raw.done, getString(isEdit ? R.string.RegexFiltersUserFilterUpdated : R.string.RegexFiltersUserFilterAdded))
                .show();
            dialog.dismiss();
        }));
        showDialog(dialog);
    }

    public void deleteCustomFilteredUser(long userId) {
        ArrayList<Long> userIds = AyuFilter.getCustomFilteredUsersList();
        if (!userIds.remove(userId)) {
            return;
        }
        AyuFilter.setCustomFilteredUsers(userIds);
        refreshRows();
        BulletinFactory.of(this)
            .createSimpleBulletin(R.raw.done, getString(R.string.RegexFiltersUserFilterDeleted))
            .show();
    }

    private ParsedSingleIdResult parseSingleCustomFilteredUser(String rawInput) {
        ParsedSingleIdResult result = new ParsedSingleIdResult();
        String input = rawInput == null ? "" : rawInput.trim();
        if (TextUtils.isEmpty(input)) {
            result.invalidToken = "";
            return result;
        }
        if (input.contains(",") || input.contains(" ") || input.contains("\n") || input.contains("\t")) {
            result.invalidToken = input;
            return result;
        }
        try {
            long userId = Long.parseLong(input);
            if (userId == 0L) {
                result.invalidToken = input;
                return result;
            }
            result.valid = true;
            result.userId = userId;
            return result;
        } catch (Exception ignore) {
            result.invalidToken = input;
            return result;
        }
    }

    private void refreshRows() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private static class ParsedSingleIdResult {
        boolean valid;
        long userId;
        String invalidToken;
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

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        if (dialogId == 0 && position > filtersHeaderRow && position < addFilterBtnRow) {
            int filterIndex = position - filtersHeaderRow - 1;
            ArrayList<AyuFilter.FilterModel> filterModels = AyuFilter.getRegexFilters();
            if (filterIndex >= 0 && filterIndex < filterModels.size()) {
                RegexFilterPopup.show(this, view, x, y, filterIndex);
                return true;
            }
        } else if (position >= userFiltersStartRow && position < userFiltersEndRow) {
            int userIndex = position - userFiltersStartRow;
            ArrayList<Long> userIds = AyuFilter.getCustomFilteredUsersList();
            if (userIndex >= 0 && userIndex < userIds.size()) {
                RegexUserFilterPopup.show(this, view, x, y, userIds.get(userIndex));
                return true;
            }
        }
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
                ChatRowCell chatCell = new ChatRowCell(mContext);
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
                    } else if (position >= userFiltersStartRow && position < userFiltersEndRow) {
                        ArrayList<Long> userIds = AyuFilter.getCustomFilteredUsersList();
                        int userIndex = position - userFiltersStartRow;
                        if (userIndex >= 0 && userIndex < userIds.size()) {
                            boolean needDivider = position + 1 < userFiltersEndRow;
                            textCheckCell.setTextAndCheck(String.valueOf(userIds.get(userIndex)), true, needDivider);
                        }
                    } else if (position >= filtersStartRow && position < filtersEndRow) {
                        ArrayList<AyuFilter.FilterModel> filterModels = AyuFilter.getRegexFilters();
                        int filterIndex = position - filtersStartRow;
                        if (filterIndex >= 0 && filterIndex < filterModels.size()) {
                            AyuFilter.FilterModel filterModel = filterModels.get(filterIndex);
                            textCheckCell.setTextAndCheck(filterModel.regex, filterModel.isEnabled(dialogId), true);
                        }
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    boolean needDivider = false;
                    if (position == addFilterBtnRow) {
                        needDivider = filtersStartRow < filtersEndRow;
                    } else if (position == addChatFilterBtnRow) {
                        needDivider = chatFiltersStartRow < chatFiltersEndRow;
                    } else if (position == addUserFilterBtnRow) {
                        needDivider = userFiltersStartRow < userFiltersEndRow;
                    }
                    textCell.setTextAndIcon(getString(R.string.RegexFiltersAdd), R.drawable.msg_add, needDivider);
                    break;
                case TYPE_ACCOUNT:
                    if (position >= chatFiltersStartRow && position < chatFiltersEndRow) {
                        int idx = position - chatFiltersStartRow;
                        var chatEntries = checkChatFilters(AyuFilter.getChatFilterEntries());
                        if (idx >= 0 && idx < chatEntries.size()) {
                            var entry = chatEntries.get(idx);
                            long did = entry.dialogId;
                            int count = entry.filters != null ? entry.filters.size() : 0;
                            ChatRowCell chatCell = (ChatRowCell) holder.itemView;
                            chatCell.setDialog(did, count);
                        }
                    }
                    break;
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == filtersOptionHeaderRow) {
                        headerCell.setText(getString(R.string.General));
                    } else if (position == filtersHeaderRow) {
                        headerCell.setText(getString(R.string.RegexFiltersSharedHeader));
                    } else if (position == chatFiltersHeaderRow) {
                        headerCell.setText(getString(R.string.RegexFiltersChatHeader));
                    } else if (position == userFiltersHeaderRow) {
                        headerCell.setText(getString(R.string.RegexFiltersUserHeader));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == filtersOptionDividerRow || position == dividerRow || position == userFiltersDividerRow) {
                return TYPE_SHADOW;
            } else if (position == filtersHeaderRow || position == filtersOptionHeaderRow || position == chatFiltersHeaderRow || position == userFiltersHeaderRow) {
                return TYPE_HEADER;
            } else if (position == addFilterBtnRow || position == addChatFilterBtnRow || position == addUserFilterBtnRow) {
                return TYPE_TEXT;
            } else if (position >= chatFiltersStartRow && position < chatFiltersEndRow) {
                return TYPE_ACCOUNT;
            }
            return TYPE_CHECK;
        }
    }
}
