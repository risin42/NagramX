package tw.nekomimi.nekogram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;

import tw.nekomimi.nekogram.helpers.AyuFilter;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public class RegexChatFiltersListActivity extends BaseNekoSettingsActivity {

    private final long dialogId;
    private int headerRow;
    private int startRow;
    private int addBtnRow;

    public RegexChatFiltersListActivity(long dialogId) {
        this.dialogId = dialogId;
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        headerRow = rowCount++;
        startRow = rowCount;
        var filters = AyuFilter.getChatFiltersForDialog(dialogId);
        rowCount += filters.size();
        addBtnRow = rowCount++;
    }

    @Override
    protected String getActionBarTitle() {
        try {
            String title = null;
            if (dialogId > 0) {
                TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(dialogId);
                if (user != null) {
                    title = ContactsController.formatName(user.first_name, user.last_name);
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-dialogId);
                if (chat != null) {
                    title = chat.title;
                }
            }
            if (!TextUtils.isEmpty(title)) {
                return title;
            }
        } catch (Exception ignored) {
        }
        return getString(R.string.RegexFilters);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
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

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position > headerRow && position < addBtnRow) {
            int idx = position - startRow;
            var filters = AyuFilter.getChatFiltersForDialog(dialogId);
            if (idx >= 0 && idx < filters.size()) {
                if (LocaleController.isRTL && x > AndroidUtilities.dp(76) || !LocaleController.isRTL && x < (view.getMeasuredWidth() - AndroidUtilities.dp(76))) {
                    RegexChatFilterPopup.show(this, view, x, y, dialogId, idx);
                } else {
                    TextCheckCell textCheckCell = (TextCheckCell) view;
                    boolean enabled = !textCheckCell.isChecked();
                    textCheckCell.setChecked(enabled);
                    var entries = AyuFilter.getChatFilterEntries();
                    for (var e : entries) {
                        if (e.dialogId == dialogId) {
                            if (e.filters != null && idx < e.filters.size()) {
                                e.filters.get(idx).setEnabled(enabled, dialogId);
                                AyuFilter.saveChatFilterEntries(entries);
                            }
                            break;
                        }
                    }
                }
            }
        } else if (position == addBtnRow) {
            presentFragment(new RegexFilterEditActivity(dialogId));
        }
    }

    private class ListAdapter extends BaseListAdapter {
        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER:
                    if (position == headerRow) {
                        ((HeaderCell) holder.itemView).setText(getString(R.string.RegexFiltersHeader));
                    }
                    break;
                case TYPE_TEXT:
                    if (position == addBtnRow) {
                        TextCell textCell = (TextCell) holder.itemView;
                        textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                        textCell.setTextAndIcon(getString(R.string.RegexFiltersAdd), R.drawable.msg_add, false);
                    }
                    break;
                case TYPE_CHECK:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    if (position > headerRow && position < addBtnRow) {
                        int idx = position - startRow;
                        var filters = AyuFilter.getChatFiltersForDialog(dialogId);
                        if (idx >= 0 && idx < filters.size()) {
                            var model = filters.get(idx);
                            textCheckCell.setTextAndCheck(model.regex, model.isEnabled(dialogId), true);
                        }
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) return TYPE_HEADER;
            if (position == addBtnRow) return TYPE_TEXT;
            return TYPE_CHECK;
        }
    }
}
