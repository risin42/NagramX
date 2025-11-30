package com.radolyn.ayugram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.radolyn.ayugram.AyuConstants;
import com.radolyn.ayugram.database.entities.EditedMessage;
import com.radolyn.ayugram.messages.AyuMessagesController;
import com.radolyn.ayugram.proprietary.AyuMessageUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatScrimPopupContainerLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.inset.WindowInsetsStateHolder;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import tw.nekomimi.nekogram.helpers.MessageHelper;
import tw.nekomimi.nekogram.ui.MessageDetailsActivity;
import xyz.nextalone.nagram.NaConfig;

public class AyuMessageHistory extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, AyuMessageCell.AyuMessageCellDelegate {
    private static final int OPTION_DELETE = 1;
    private static final int OPTION_COPY = 2;
    private static final int OPTION_COPY_PHOTO = 3;
    private static final int OPTION_COPY_PHOTO_AS_STICKER = 4;
    private static final int OPTION_DETAILS = 5;
    private final MessageObject messageObject;
    private List<EditedMessage> messages;
    private int rowCount;
    private RecyclerListView listView;
    private ActionBarPopupWindow scrimPopupWindow;
    private final WindowInsetsStateHolder windowInsetsStateHolder = new WindowInsetsStateHolder(this::checkInsets);

    public AyuMessageHistory(MessageObject messageObject) {
        this.messageObject = messageObject;
        updateHistory();
    }

    private void checkInsets() {
        if (listView != null) {
            listView.setPadding(0, 0, 0, windowInsetsStateHolder.getCurrentNavigationBarInset() + dp(8));
        }
    }

    private void updateHistory() {
        messages = AyuMessagesController.getInstance().getRevisions(getUserConfig().clientUserId, messageObject.messageOwner.dialog_id, messageObject.messageOwner.id);
        rowCount = messages.size();
    }

    @Override
    public View createView(Context context) {
        var firstMsg = messages.get(0);
        var peer = getMessagesController().getUserOrChat(firstMsg.dialogId);
        int currentAccount = UserConfig.selectedAccount;

        String name = switch (peer) {
            case TLRPC.User user -> user.first_name;
            case TLRPC.Chat chat -> chat.title;
            default -> getString(R.string.EditsHistoryMenuText);
        };

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(name);
        actionBar.setSubtitle(String.valueOf(firstMsg.messageId));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        SizeNotifierFrameLayout frameLayout = new SizeNotifierFrameLayout(context) {
            @Override
            protected boolean isActionBarVisible() {
                return false;
            }

            @Override
            protected boolean isStatusBarVisible() {
                return false;
            }

            @Override
            protected boolean useRootView() {
                return false;
            }
        };

        fragmentView = frameLayout;
        frameLayout.setOccupyStatusBar(false);
        frameLayout.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, (v, insets) -> {
            windowInsetsStateHolder.setInsets(insets);
            return WindowInsetsCompat.CONSUMED;
        });

        listView = new RecyclerListView(context);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);

        LinearLayoutManager layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        layoutManager.setStackFromEnd(true);

        listView.setLayoutManager(layoutManager);
        listView.setVerticalScrollBarEnabled(true);
        listView.setAdapter(new ListAdapter(context, currentAccount));
        listView.setSelectorType(9);
        listView.setSelectorDrawableColor(0);
        listView.setClipToPadding(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (rowCount > 0) {
            listView.scrollToPosition(rowCount - 1);
        }

        listView.setOnItemClickListener((view, position, x, y) -> {
            if (view instanceof AyuMessageCell) {
                createMenu(view, x, y, position);
            }
        });

        return fragmentView;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, AyuConstants.MESSAGE_EDITED_NOTIFICATION);

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

        NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, AyuConstants.MESSAGE_EDITED_NOTIFICATION);
        Bulletin.removeDelegate(this);

        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
            scrimPopupWindow = null;
        }

        if (listView != null) {
            listView.setAdapter(null);
            listView.setOnItemClickListener((RecyclerListView.OnItemClickListener) null);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == AyuConstants.MESSAGE_EDITED_NOTIFICATION) {
            var dialogId = (long) args[0];
            var messageId = (int) args[1];

            if (dialogId == messageObject.messageOwner.dialog_id && messageId == messageObject.messageOwner.id) {
                updateHistory();
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
            }
        }
    }

    @Override
    public void onTextCopied() {
        BulletinFactory.of(this).createCopyBulletin(getString(R.string.MessageCopied)).show();
    }

    @Override
    public void onImagePressed(ChatMessageCell cell) {
        if (cell.getMessageObject() != null) {
            if (cell.getMessageObject().isSticker()) {
                var inputStickerSet = cell.getMessageObject().getInputStickerSet();
                if (inputStickerSet != null) {
                    showDialog(new StickersAlert(getParentActivity(), this, inputStickerSet, null, null, false));
                }
            } else {
                AndroidUtilities.openForView(cell.getMessageObject(), getParentActivity(), null, false);
            }
        }
    }

    @Override
    public void onAvatarPressed(ChatMessageCell cell, long userId) {
        Bundle args = new Bundle();
        if (userId > 0) {
            args.putLong("user_id", userId);
        } else {
            args.putLong("chat_id", -userId);
        }
        presentFragment(new ProfileActivity(args));
    }

    @Override
    public void onResume() {
        super.onResume();

        if (fragmentView instanceof SizeNotifierFrameLayout) {
            ((SizeNotifierFrameLayout) fragmentView).onResume();
        }

        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return windowInsetsStateHolder.getCurrentNavigationBarInset();
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();

        if (fragmentView instanceof SizeNotifierFrameLayout) {
            ((SizeNotifierFrameLayout) fragmentView).onPause();
        }

        Bulletin.removeDelegate(this);

        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
            scrimPopupWindow = null;
        }
    }

    private void createMenu(View v, float x, float y, int position) {
        final MessageObject msg = (v instanceof ChatMessageCell) ? ((ChatMessageCell) v).getMessageObject() : null;
        if (msg == null || getParentActivity() == null) {
            return;
        }

        ArrayList<CharSequence> items = new ArrayList<>();
        ArrayList<Integer> options = new ArrayList<>();
        ArrayList<Integer> icons = new ArrayList<>();

        String textToCopy = msg.messageOwner != null ? msg.messageOwner.message : null;
        if (textToCopy != null && !textToCopy.isEmpty()) {
            items.add(getString(R.string.Copy));
            icons.add(R.drawable.msg_copy);
            options.add(OPTION_COPY);
        }

        boolean isStaticSticker = msg.isSticker() && !msg.isAnimatedSticker() && !msg.isVideoSticker();
        if ((msg.isPhoto() || isStaticSticker) && !msg.needDrawBluredPreview()) {
            if (msg.isPhoto()) {
                items.add(getString(R.string.CopyPhoto));
            } else {
                items.add(getString(R.string.CopySticker));
            }
            icons.add(R.drawable.msg_copy_photo);
            options.add(OPTION_COPY_PHOTO);

            if (msg.isPhoto()) {
                items.add(getString(R.string.CopyPhotoAsSticker));
                icons.add(R.drawable.msg_copy_photo);
                options.add(OPTION_COPY_PHOTO_AS_STICKER);
            }
        }

        items.add(getString(R.string.Delete));
        icons.add(R.drawable.msg_delete);
        options.add(OPTION_DELETE);

        items.add(getString(R.string.MessageDetails));
        icons.add(R.drawable.msg_info);
        options.add(OPTION_DETAILS);

        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity(), R.drawable.popup_fixed_alert, getResourceProvider(), 0);
        popupLayout.setMinimumWidth(dp(200));
        popupLayout.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

        for (int a = 0, N = items.size(); a < N; ++a) {
            ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getParentActivity(), a == 0, a == N - 1, getResourceProvider());
            cell.setMinimumWidth(dp(200));
            cell.setTextAndIcon(items.get(a), icons.get(a));
            final Integer option = options.get(a);
            popupLayout.addView(cell);
            final int pos = position;
            cell.setOnClickListener(v1 -> {
                if (option == OPTION_DELETE) {
                    EditedMessage edited = messages.get(pos);
                    Utilities.globalQueue.postRunnable(() -> AyuMessagesController.getInstance().deleteRevision(edited.fakeId));
                    if (pos >= 0 && pos < messages.size()) {
                        messages.remove(pos);
                        rowCount = messages.size();
                        var adapter = listView.getAdapter();
                        if (adapter != null) {
                            adapter.notifyItemRemoved(pos);
                        }
                    }
                } else if (option == OPTION_COPY) {
                    String text = msg.messageOwner != null ? msg.messageOwner.message : null;
                    if (text != null && !text.isEmpty()) {
                        AndroidUtilities.addToClipboard(text);
                        BulletinFactory.of(this).createCopyBulletin(getString(R.string.MessageCopied)).show();
                    }
                } else if (option == OPTION_COPY_PHOTO) {
                    MessageHelper.addMessageToClipboard(msg, () -> BulletinFactory.of(this).createCopyBulletin(getString(R.string.PhotoCopied)).show());
                } else if (option == OPTION_COPY_PHOTO_AS_STICKER) {
                    MessageHelper.addMessageToClipboardAsSticker(msg, () -> BulletinFactory.of(this).createCopyBulletin(getString(R.string.PhotoCopied)).show());
                } else if (option == OPTION_DETAILS) {
                    presentFragment(new MessageDetailsActivity(msg, null));
                }
                if (scrimPopupWindow != null) {
                    scrimPopupWindow.dismiss();
                }
            });
        }

        ChatScrimPopupContainerLayout scrimPopupContainerLayout = new ChatScrimPopupContainerLayout(fragmentView.getContext()) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
                    closeMenu();
                }
                return super.dispatchKeyEvent(event);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                boolean b = super.dispatchTouchEvent(ev);
                if (ev.getAction() == MotionEvent.ACTION_DOWN && !b) {
                    closeMenu();
                }
                return b;
            }

            private void closeMenu() {
                if (scrimPopupWindow != null) {
                    scrimPopupWindow.dismiss();
                }
            }
        };
        scrimPopupContainerLayout.addView(popupLayout, LayoutHelper.createLinearRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 0, 0, 0));
        scrimPopupContainerLayout.setPopupWindowLayout(popupLayout);

        scrimPopupWindow = new ActionBarPopupWindow(scrimPopupContainerLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                if (scrimPopupWindow != this) {
                    return;
                }
                Bulletin.hideVisible();
                scrimPopupWindow = null;
            }
        };
        scrimPopupWindow.setPauseNotifications(true);
        scrimPopupWindow.setDismissAnimationDuration(220);
        scrimPopupWindow.setOutsideTouchable(true);
        scrimPopupWindow.setClippingEnabled(true);
        scrimPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        scrimPopupWindow.setFocusable(true);
        scrimPopupContainerLayout.measure(View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST));
        scrimPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        scrimPopupWindow.getContentView().setFocusableInTouchMode(true);
        popupLayout.setFitItems(true);

        int[] listLocation = new int[2];
        listView.getLocationInWindow(listLocation);

        int popupX = listLocation[0] + v.getLeft() + (int) x - scrimPopupContainerLayout.getMeasuredWidth() - dp(28);
        if (popupX < dp(6)) {
            popupX = dp(6);
        } else if (popupX > listView.getMeasuredWidth() - dp(6) - scrimPopupContainerLayout.getMeasuredWidth()) {
            popupX = listView.getMeasuredWidth() - dp(6) - scrimPopupContainerLayout.getMeasuredWidth();
        }

        int height = scrimPopupContainerLayout.getMeasuredHeight();
        int totalHeight = fragmentView.getHeight();
        int popupY;
        if (height < totalHeight) {
            popupY = listLocation[1] + v.getTop() + (int) y - height - dp(8);
            if (popupY < dp(24)) {
                popupY = dp(24);
            } else if (popupY > totalHeight - height - dp(8)) {
                popupY = totalHeight - height - dp(8);
            }
        } else {
            popupY = AndroidUtilities.getStatusBarHeight(getContext());
        }

        scrimPopupContainerLayout.setMaxHeight(totalHeight - popupY);
        scrimPopupWindow.showAtLocation(listView, Gravity.LEFT | Gravity.TOP, popupX, popupY);
        scrimPopupWindow.dimBehind();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;
        private final int currentAccount;

        public ListAdapter(Context context, int currentAccount) {
            this.context = context;
            this.currentAccount = currentAccount;
        }

        @Override
        public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof AyuMessageCell) {
                ((AyuMessageCell) holder.itemView).setAyuDelegate(null);
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new AyuMessageCell(context, currentAccount));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 1) {
                var ayuMessageDetailCell = (AyuMessageCell) holder.itemView;

                var editedMessage = messages.get(position);
                var msg = createMessageObject(editedMessage);

                ayuMessageDetailCell.setAyuDelegate(AyuMessageHistory.this);
                ayuMessageDetailCell.setMessageObject(msg, null, false, false, false);
                ayuMessageDetailCell.setEditedMessage(editedMessage);
                ayuMessageDetailCell.setId(position);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return position >= 0 && position < messages.size() ? 1 : 0;
        }

        private MessageObject createMessageObject(EditedMessage editedMessage) {
            var msg = new TLRPC.TL_message();
            AyuMessageUtils.map(editedMessage, msg, currentAccount);
            AyuMessageUtils.mapMedia(editedMessage, msg);

            msg.date = editedMessage.entityCreateDate;
            msg.edit_hide = true;

            // let file be downloaded
            if (editedMessage.documentType == AyuConstants.DOCUMENT_TYPE_FILE && Objects.equals(editedMessage.mediaPath, FileLoader.getInstance(currentAccount).getPathToMessage(messageObject.messageOwner).getAbsolutePath())) {
                msg.media.document = messageObject.messageOwner.media.document;
            }

            // fix reply state
            if (messageObject.messageOwner.replyMessage != null) {
                msg.replyMessage = messageObject.messageOwner.replyMessage;
                msg.reply_to = messageObject.messageOwner.reply_to;
            }

            return new MessageObject(getCurrentAccount(), msg, false, true);
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return NaConfig.INSTANCE.getForceEdgeToEdge().Bool();
    }

}