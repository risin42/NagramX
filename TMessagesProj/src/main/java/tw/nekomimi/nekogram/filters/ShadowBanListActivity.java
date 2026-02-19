package tw.nekomimi.nekogram.filters;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.PrivacyUsersActivity;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import tw.nekomimi.nekogram.filters.popup.RegexUserFilterPopup;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.FiltersChatCell;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import tw.nekomimi.nekogram.utils.AndroidUtil;

public class ShadowBanListActivity extends BaseNekoSettingsActivity {

    private final HashSet<Long> resolvingCustomFilteredUsers = new HashSet<>();
    private final HashSet<Long> resolvedCustomFilteredUsers = new HashSet<>();
    private final HashMap<Long, String> customFilteredUserDisplayCache = new HashMap<>();
    private int headerRow;
    private int blockedChannelsRow;
    private int blockedChannelsDividerRow;
    private int addUserFilterBtnRow;
    private int userFiltersStartRow;
    private int userFiltersEndRow;

    @Override
    protected void updateRows() {
        super.updateRows();
        blockedChannelsRow = rowCount++;
        blockedChannelsDividerRow = rowCount++;
        headerRow = rowCount++;
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
        invalidateCustomFilteredUsersDisplayState();
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == blockedChannelsRow) {
            presentFragment(new PrivacyUsersActivity(PrivacyUsersActivity.TYPE_BLOCKED_CHANNELS, AyuFilter.getBlockedChannelsList(), false, false));
        } else if (position == addUserFilterBtnRow) {
            showAddCustomFilteredUserDialog();
        } else if (position >= userFiltersStartRow && position < userFiltersEndRow) {
            int userIndex = position - userFiltersStartRow;
            ArrayList<Long> userIds = AyuFilter.getCustomFilteredUsersList();
            if (userIndex < 0 || userIndex >= userIds.size()) {
                return;
            }
            long userId = userIds.get(userIndex);
            RegexUserFilterPopup.show(this, view, x, y, getResourceProvider(), () -> deleteCustomFilteredUser(userId));
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        if (position >= userFiltersStartRow && position < userFiltersEndRow) {
            int userIndex = position - userFiltersStartRow;
            ArrayList<Long> userIds = AyuFilter.getCustomFilteredUsersList();
            if (userIndex >= 0 && userIndex < userIds.size()) {
                long userId = userIds.get(userIndex);
                presentFragment(ProfileActivity.of(userId));
                return true;
            }
        }
        return super.onItemLongClick(view, position, x, y);
    }

    private void showAddCustomFilteredUserDialog() {
        Context context = getContext();
        if (context == null) {
            return;
        }

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
        editText.setPadding(0, 0, 0, dp(6));
        editText.requestFocus();

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
            .setTitle(getString(R.string.RegexFiltersAdd))
            .setView(container)
            .setNegativeButton(getString(R.string.Cancel), null)
            .setPositiveButton(getString(R.string.Save), null)
            .create();

        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String input = editText.getText() == null ? "" : editText.getText().toString();
            ParsedSingleIdResult result = parseSingleCustomFilteredUser(input);
            if (!result.valid) {
                AndroidUtil.showInputError(editText);
                return;
            }
            long selfUserId = getUserConfig().getClientUserId();
            if (result.userId == selfUserId) {
                AndroidUtil.showInputError(editText);
                return;
            }

            HashSet<Long> idSet = new HashSet<>(AyuFilter.getCustomFilteredUsersList());
            if (idSet.contains(result.userId)) {
                AndroidUtil.showInputError(editText);
                return;
            }

            idSet.add(result.userId);
            ArrayList<Long> updated = new ArrayList<>(idSet);
            Collections.sort(updated);
            AyuFilter.setCustomFilteredUsers(updated);
            TLRPC.User localUser = getMessagesController().getUser(result.userId);
            if (localUser != null) {
                AyuFilter.updateCustomFilteredUserFromLocalUser(localUser);
            }
            refreshRows();
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
    }

    private ParsedSingleIdResult parseSingleCustomFilteredUser(String rawInput) {
        ParsedSingleIdResult result = new ParsedSingleIdResult();
        String input = rawInput == null ? "" : rawInput.trim();
        if (TextUtils.isEmpty(input)) {
            return result;
        }
        if (input.contains(",") || input.contains(" ") || input.contains("\n") || input.contains("\t")) {
            return result;
        }
        try {
            long userId = Long.parseLong(input);
            if (userId < 100000) {
                return result;
            }
            result.valid = true;
            result.userId = userId;
            return result;
        } catch (Exception ignore) {
            return result;
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void refreshRows() {
        invalidateCustomFilteredUsersDisplayState();
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void invalidateCustomFilteredUsersDisplayState() {
        resolvingCustomFilteredUsers.clear();
        resolvedCustomFilteredUsers.clear();
        customFilteredUserDisplayCache.clear();
    }

    private boolean isCustomFilteredUserUntracked(long userId) {
        return userId <= 0L || !AyuFilter.getCustomFilteredUsersList().contains(userId);
    }

    private void clearCustomFilteredUserDisplayState(long userId) {
        resolvingCustomFilteredUsers.remove(userId);
        resolvedCustomFilteredUsers.remove(userId);
        customFilteredUserDisplayCache.remove(userId);
    }

    private boolean hasUsableUserIdentity(TLRPC.User user) {
        if (user == null || user.id == 0L) {
            return false;
        }
        String displayName = UserObject.getUserName(user);
        if (!TextUtils.isEmpty(displayName) && !TextUtils.isEmpty(displayName.trim())) {
            return true;
        }
        return !TextUtils.isEmpty(UserObject.getPublicUsername(user));
    }

    private String formatResolvedUserTitle(TLRPC.User user) {
        if (user == null || user.id == 0L) {
            return null;
        }
        String displayName = UserObject.getUserName(user);
        if (!TextUtils.isEmpty(displayName)) {
            displayName = displayName.trim();
        }
        if (TextUtils.isEmpty(displayName)) {
            String username = UserObject.getPublicUsername(user);
            if (!TextUtils.isEmpty(username)) {
                displayName = "@" + username;
            }
        }
        if (TextUtils.isEmpty(displayName)) {
            return String.valueOf(user.id);
        }
        return displayName;
    }

    private String buildFallbackCustomFilteredUserTitle(AyuFilter.CustomFilteredUser userData, long userId) {
        if (userData != null) {
            if (!TextUtils.isEmpty(userData.displayName)) {
                String displayName = userData.displayName.trim();
                if (!TextUtils.isEmpty(displayName)) {
                    return displayName;
                }
            }
            String username = userData.username;
            if (!TextUtils.isEmpty(username)) {
                return "@" + username;
            }
        }
        return String.valueOf(userId);
    }

    private String getCustomFilteredUserRowSubtitle(long userId) {
        return String.valueOf(userId);
    }

    private boolean cacheResolvedCustomFilteredUser(long userId, TLRPC.User user, boolean notifyRow) {
        if (user == null || user.id != userId) {
            return false;
        }
        AyuFilter.updateCustomFilteredUserFromLocalUser(user);
        if (!hasUsableUserIdentity(user)) {
            return false;
        }
        String title = formatResolvedUserTitle(user);
        if (TextUtils.isEmpty(title)) {
            return false;
        }
        resolvingCustomFilteredUsers.remove(userId);
        resolvedCustomFilteredUsers.add(userId);
        customFilteredUserDisplayCache.put(userId, title);
        if (notifyRow) {
            notifyCustomFilteredUserRowChanged(userId);
        }
        return true;
    }

    private String getCustomFilteredUserRowTitle(long userId) {
        TLRPC.User localUser = getMessagesController().getUser(userId);
        if (cacheResolvedCustomFilteredUser(userId, localUser, false)) {
            return customFilteredUserDisplayCache.get(userId);
        }
        String cached = customFilteredUserDisplayCache.get(userId);
        if (!TextUtils.isEmpty(cached)) {
            return cached;
        }
        AyuFilter.CustomFilteredUser userData = AyuFilter.getCustomFilteredUser(userId);
        String fallback = buildFallbackCustomFilteredUserTitle(userData, userId);
        customFilteredUserDisplayCache.put(userId, fallback);
        return fallback;
    }

    private void ensureCustomFilteredUserResolved(long userId) {
        if (userId <= 0L || isCustomFilteredUserUntracked(userId)) {
            return;
        }
        TLRPC.User localUser = getMessagesController().getUser(userId);
        if (cacheResolvedCustomFilteredUser(userId, localUser, false)) {
            return;
        }
        if (resolvedCustomFilteredUsers.contains(userId) || resolvingCustomFilteredUsers.contains(userId)) {
            return;
        }
        resolvingCustomFilteredUsers.add(userId);
        resolveCustomFilteredUserFromLocalDb(userId);
    }

    private void resolveCustomFilteredUserFromLocalDb(long userId) {
        Utilities.globalQueue.postRunnable(() -> {
            TLRPC.User storageUser = getMessagesStorage().getUserSync(userId);
            AndroidUtilities.runOnUIThread(() -> {
                if (isCustomFilteredUserUntracked(userId)) {
                    clearCustomFilteredUserDisplayState(userId);
                    return;
                }
                if (resolvedCustomFilteredUsers.contains(userId) && !resolvingCustomFilteredUsers.contains(userId)) {
                    return;
                }
                if (cacheResolvedCustomFilteredUser(userId, storageUser, true)) {
                    getMessagesController().putUser(storageUser, true);
                    return;
                }
                if (storageUser != null) {
                    getMessagesController().putUser(storageUser, true);
                    AyuFilter.updateCustomFilteredUserFromLocalUser(storageUser);
                }
                resolveCustomFilteredUserByUsername(userId);
            });
        });
    }

    private void resolveCustomFilteredUserByUsername(long userId) {
        AyuFilter.CustomFilteredUser userData = AyuFilter.getCustomFilteredUser(userId);
        String username = userData != null ? userData.username : null;
        if (TextUtils.isEmpty(username)) {
            resolveCustomFilteredUserById(userId);
            return;
        }
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username;
        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (isCustomFilteredUserUntracked(userId)) {
                clearCustomFilteredUserDisplayState(userId);
                return;
            }
            if (resolvedCustomFilteredUsers.contains(userId) && !resolvingCustomFilteredUsers.contains(userId)) {
                return;
            }
            if (response instanceof TLRPC.TL_contacts_resolvedPeer resolvedPeer) {
                getMessagesController().putUsers(resolvedPeer.users, false);
                getMessagesController().putChats(resolvedPeer.chats, false);
                getMessagesStorage().putUsersAndChats(resolvedPeer.users, resolvedPeer.chats, true, true);
                boolean matched = resolvedPeer.peer instanceof TLRPC.TL_peerUser && resolvedPeer.peer.user_id == userId;
                if (matched) {
                    TLRPC.User resolvedUser = getMessagesController().getUser(userId);
                    if (resolvedUser == null && resolvedPeer.users != null) {
                        for (TLRPC.User user : resolvedPeer.users) {
                            if (user != null && user.id == userId) {
                                resolvedUser = user;
                                break;
                            }
                        }
                    }
                    if (cacheResolvedCustomFilteredUser(userId, resolvedUser, true)) {
                        return;
                    }
                }
            }
            resolveCustomFilteredUserById(userId);
        }));
        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
    }

    @SuppressWarnings("rawtypes")
    private void resolveCustomFilteredUserById(long userId) {
        AyuFilter.CustomFilteredUser userData = AyuFilter.getCustomFilteredUser(userId);
        if (userData == null || userData.accessHash == 0L) {
            onCustomFilteredUserResolveFailed(userId);
            return;
        }
        TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers();
        TLRPC.TL_inputUser inputUser = new TLRPC.TL_inputUser();
        inputUser.user_id = userId;
        inputUser.access_hash = userData.accessHash;
        req.id.add(inputUser);
        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (isCustomFilteredUserUntracked(userId)) {
                clearCustomFilteredUserDisplayState(userId);
                return;
            }
            if (resolvedCustomFilteredUsers.contains(userId) && !resolvingCustomFilteredUsers.contains(userId)) {
                return;
            }
            if (error == null && response instanceof Vector vector) {
                ArrayList<TLRPC.User> users = new ArrayList<>();
                for (Object object : vector.objects) {
                    if (object instanceof TLRPC.User user) {
                        users.add(user);
                    }
                }
                if (!users.isEmpty()) {
                    getMessagesController().putUsers(users, false);
                    getMessagesStorage().putUsersAndChats(users, null, true, true);
                    TLRPC.User resolvedUser = null;
                    for (TLRPC.User user : users) {
                        if (user != null && user.id == userId) {
                            resolvedUser = user;
                            break;
                        }
                    }
                    if (resolvedUser == null) {
                        resolvedUser = getMessagesController().getUser(userId);
                    }
                    if (cacheResolvedCustomFilteredUser(userId, resolvedUser, true)) {
                        return;
                    }
                }
            }
            TLRPC.User localUser = getMessagesController().getUser(userId);
            if (cacheResolvedCustomFilteredUser(userId, localUser, true)) {
                return;
            }
            onCustomFilteredUserResolveFailed(userId);
        }));
        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
    }

    private void onCustomFilteredUserResolveFailed(long userId) {
        if (isCustomFilteredUserUntracked(userId)) {
            clearCustomFilteredUserDisplayState(userId);
            return;
        }
        resolvingCustomFilteredUsers.remove(userId);
        resolvedCustomFilteredUsers.add(userId);
        AyuFilter.CustomFilteredUser userData = AyuFilter.getCustomFilteredUser(userId);
        customFilteredUserDisplayCache.put(userId, buildFallbackCustomFilteredUserTitle(userData, userId));
        notifyCustomFilteredUserRowChanged(userId);
    }

    private void notifyCustomFilteredUserRowChanged(long userId) {
        if (listAdapter == null) {
            return;
        }
        ArrayList<Long> userIds = AyuFilter.getCustomFilteredUsersList();
        int index = userIds.indexOf(userId);
        if (index < 0) {
            return;
        }
        int position = userFiltersStartRow + index;
        if (position >= 0 && position < rowCount) {
            listAdapter.notifyItemChanged(position);
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.ShadowBan);
    }

    private static class ParsedSingleIdResult {
        boolean valid;
        long userId;
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
                case TYPE_HEADER:
                    if (position == headerRow) {
                        ((HeaderCell) holder.itemView).setText(getString(R.string.RegexFiltersUserHeader));
                    }
                    break;
                case TYPE_TEXT:
                    if (position == blockedChannelsRow) {
                        TextCell textCell = (TextCell) holder.itemView;
                        int count = AyuFilter.getBlockedChannelsCount();
                        String value = count == 0 ? getString(R.string.BlockedEmpty) : String.valueOf(count);
                        textCell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextAndValueAndIcon(getString(R.string.BlockedChannels), value, R.drawable.msg2_block2, false);
                    } else if (position == addUserFilterBtnRow) {
                        TextCell textCell = (TextCell) holder.itemView;
                        textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                        textCell.setTextAndIcon(getString(R.string.RegexFiltersAdd), R.drawable.msg_add, userFiltersStartRow < userFiltersEndRow);
                    }
                    break;
                case TYPE_ACCOUNT:
                    if (position >= userFiltersStartRow && position < userFiltersEndRow) {
                        ArrayList<Long> userIds = AyuFilter.getCustomFilteredUsersList();
                        int userIndex = position - userFiltersStartRow;
                        if (userIndex >= 0 && userIndex < userIds.size()) {
                            long userId = userIds.get(userIndex);
                            boolean needUserDivider = position + 1 < userFiltersEndRow;
                            FiltersChatCell chatCell = (FiltersChatCell) holder.itemView;
                            chatCell.setUserFilter(userId, getCustomFilteredUserRowTitle(userId), getCustomFilteredUserRowSubtitle(userId), needUserDivider);
                            ensureCustomFilteredUserResolved(userId);
                        }
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) {
                return TYPE_HEADER;
            } else if (position == blockedChannelsDividerRow) {
                return TYPE_SHADOW;
            } else if (position == blockedChannelsRow || position == addUserFilterBtnRow) {
                return TYPE_TEXT;
            }
            return TYPE_ACCOUNT;
        }
    }
}
