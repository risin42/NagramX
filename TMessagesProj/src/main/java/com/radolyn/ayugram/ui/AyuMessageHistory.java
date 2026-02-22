package com.radolyn.ayugram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.radolyn.ayugram.AyuConstants;
import com.radolyn.ayugram.AyuUtils;
import com.radolyn.ayugram.database.entities.EditedMessage;
import com.radolyn.ayugram.messages.AyuMessagesController;
import com.radolyn.ayugram.proprietary.AyuMessageUtils;
import com.radolyn.ayugram.utils.AyuFileLocation;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatScrimPopupContainerLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.inset.WindowInsetsStateHolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import kotlin.Unit;
import tw.nekomimi.nekogram.helpers.MessageHelper;
import tw.nekomimi.nekogram.llm.LlmConfig;
import tw.nekomimi.nekogram.translate.Translator;
import tw.nekomimi.nekogram.ui.MessageDetailsActivity;
import tw.nekomimi.nekogram.ui.NekoDelegateFragment;
import tw.nekomimi.nekogram.ui.cells.NekoMessageCell;

public class AyuMessageHistory extends NekoDelegateFragment {
    private static final int OPTION_DELETE = 1;
    private static final int OPTION_COPY = 2;
    private static final int OPTION_COPY_PHOTO = 3;
    private static final int OPTION_COPY_PHOTO_AS_STICKER = 4;
    private static final int OPTION_DETAILS = 5;
    private static final int OPTION_SAVE_TO_GALLERY = 6;
    private static final int OPTION_SAVE_TO_DOWNLOADS = 7;
    private static final int OPTION_TRANSLATE = 8;
    private final MessageObject messageObject;
    private List<EditedMessage> messages;
    private final ArrayList<MessageObject> messageObjects = new ArrayList<>();
    private int rowCount;
    private RecyclerListView listView;
    private TextView emptyView;
    private Runnable showEmptyViewRunnable;
    private ActionBarPopupWindow scrimPopupWindow;
    private final WindowInsetsStateHolder windowInsetsStateHolder = new WindowInsetsStateHolder(this::checkInsets);
    private String[] cachedAttachmentFileNames;

    public AyuMessageHistory(MessageObject messageObject) {
        this.messageObject = messageObject;
        updateHistory();
    }

    @Override
    protected RecyclerListView getMessageListView() {
        return listView;
    }

    private void checkInsets() {
        if (listView != null) {
            listView.setPadding(0, 0, 0, windowInsetsStateHolder.getCurrentNavigationBarInset() + dp(8));
        }
    }

    private void updateHistory() {
        messages = AyuMessagesController.getInstance().getRevisions(getUserConfig().clientUserId, messageObject.messageOwner.dialog_id, messageObject.messageOwner.id);
        if (messages == null) {
            messages = new ArrayList<>();
        }
        rowCount = messages.size();
        cacheAttachmentFileNames();
        rebuildMessageObjects();
        updateEmptyView();
    }

    private void cacheAttachmentFileNames() {
        File attachmentsDir = AyuMessagesController.attachmentsPath;
        if (attachmentsDir.exists()) {
            cachedAttachmentFileNames = attachmentsDir.list();
        } else {
            cachedAttachmentFileNames = null;
        }
    }

    @Override
    public View createView(Context context) {
        long dialogId = messageObject.messageOwner.dialog_id;
        var peer = getMessagesController().getUserOrChat(dialogId);
        int currentAccount = UserConfig.selectedAccount;

        String name = switch (peer) {
            case null -> getString(R.string.EditsHistoryMenuText);
            case TLRPC.User user -> user.first_name;
            case TLRPC.Chat chat -> chat.title;
            default -> getString(R.string.EditsHistoryMenuText);
        };

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(name);
        actionBar.setSubtitle(String.valueOf(messageObject.getId()));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        SizeNotifierFrameLayout frameLayout = new ScrimFrameLayout(context) {
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
        listView.setLayoutAnimation(null);

        LinearLayoutManager layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        layoutManager.setStackFromEnd(true);

        listView.setLayoutManager(layoutManager);
        listView.setVerticalScrollBarEnabled(true);
        listView.setAdapter(new ListAdapter(context, currentAccount));
        setupMessageListItemAnimator(listView);
        listView.setSelectorType(9);
        listView.setSelectorDrawableColor(0);
        listView.setClipToPadding(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (rowCount > 0) {
            listView.scrollToPosition(rowCount - 1);
        }

        listView.setOnItemClickListener((view, position, x, y) -> {
            if (view instanceof NekoMessageCell) {
                createMenu(view, x, y, position);
            }
        });

        emptyView = new AppCompatTextView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                Theme.applyServiceShaderMatrix(getMeasuredWidth(), frameLayout.getBackgroundSizeY(), getX(), getY());
                Paint backgroundPaint = getThemedPaint(Theme.key_paint_chatActionBackground);
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(30), dp(30), backgroundPaint);
                if (Theme.hasGradientService()) {
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(30), dp(30), Theme.getThemePaint(Theme.key_paint_chatActionBackgroundDarken, getResourceProvider()));
                }
                super.onDraw(canvas);
            }
        };
        emptyView.setText(getString(R.string.NoMessages));
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyView.setTypeface(AndroidUtilities.bold());
        emptyView.setTextColor(Theme.getColor(Theme.key_chat_serviceText, getResourceProvider()));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        emptyView.setPadding(dp(20), dp(4), dp(20), dp(6));
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        updateEmptyView();

        return fragmentView;
    }

    private void updateEmptyView() {
        updateEmptyView(false);
    }

    private void updateEmptyView(boolean delayIfEmpty) {
        showEmptyViewRunnable = updateListEmptyView(() -> emptyView, () -> listView, rowCount == 0, delayIfEmpty, showEmptyViewRunnable, () -> showEmptyViewRunnable = null);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, AyuConstants.MESSAGE_EDITED_NOTIFICATION);
        NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.voiceTranscriptionUpdate);

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

        NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, AyuConstants.MESSAGE_EDITED_NOTIFICATION);
        NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, NotificationCenter.voiceTranscriptionUpdate);
        Bulletin.removeDelegate(this);

        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
            scrimPopupWindow = null;
        }

        if (showEmptyViewRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(showEmptyViewRunnable);
            showEmptyViewRunnable = null;
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
        } else if (id == NotificationCenter.voiceTranscriptionUpdate) {
            handleVoiceTranscriptionUpdate(args);
        }
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

        if ((msg.isPhoto() || msg.isVideo() || msg.isGif()) && !msg.needDrawBluredPreview()) {
            items.add(getString(R.string.SaveToGallery));
            icons.add(R.drawable.msg_gallery);
            options.add(OPTION_SAVE_TO_GALLERY);
        }

        if (msg.isDocument() || msg.isMusic() || msg.isVoice()) {
            items.add(msg.isMusic() ? getString(R.string.SaveToMusic) : getString(R.string.SaveToDownloads));
            icons.add(R.drawable.msg_download);
            options.add(OPTION_SAVE_TO_DOWNLOADS);
        }

        String textToTranslate = msg.messageOwner != null ? msg.messageOwner.message : null;
        if (!TextUtils.isEmpty(textToTranslate) || msg.isPoll()) {
            boolean translated = msg.messageOwner != null && (msg.messageOwner.translated || msg.messageOwner.translatedPoll != null);
            items.add(getString(translated ? R.string.HideTranslation : R.string.Translate));
            icons.add(LlmConfig.llmIsDefaultProvider() ? R.drawable.magic_stick_solar : R.drawable.ic_translate);
            options.add(OPTION_TRANSLATE);
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
                        if (pos < messageObjects.size()) {
                            messageObjects.remove(pos);
                        }
                        rowCount = messages.size();
                        notifyMessageListItemRemoved(listView, pos);
                        updateEmptyView(rowCount == 0);
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
                } else if (option == OPTION_SAVE_TO_GALLERY) {
                    String path = null;
                    if (!TextUtils.isEmpty(msg.messageOwner.attachPath)) {
                        File temp = new File(msg.messageOwner.attachPath);
                        if (temp.exists()) {
                            path = msg.messageOwner.attachPath;
                        }
                    }
                    if (TextUtils.isEmpty(path)) {
                        File f = FileLoader.getInstance(getCurrentAccount()).getPathToMessage(msg.messageOwner);
                        if (f != null && f.exists()) {
                            path = f.getPath();
                        }
                    }
                    if (!TextUtils.isEmpty(path)) {
                        MediaController.saveFile(msg, path, getParentActivity(), msg.isVideo() ? 1 : 0, null, null, uri -> {
                            if (getParentActivity() != null) {
                                BulletinFactory.of(this).createDownloadBulletin(
                                        msg.isVideo() ? BulletinFactory.FileType.VIDEO : BulletinFactory.FileType.PHOTO,
                                        getResourceProvider()
                                ).show();
                            }
                        });
                    }
                } else if (option == OPTION_SAVE_TO_DOWNLOADS) {
                    ArrayList<MessageObject> messageObjects = new ArrayList<>();
                    messageObjects.add(msg);
                    MediaController.saveFilesFromMessages(getParentActivity(), getAccountInstance(), messageObjects, (count) -> {
                        if (count > 0) {
                            BulletinFactory.of(this).createDownloadBulletin(
                                    msg.isMusic() ? BulletinFactory.FileType.AUDIOS : BulletinFactory.FileType.UNKNOWNS,
                                    count,
                                    getResourceProvider()
                            ).show();
                        }
                    });
                } else if (option == OPTION_DETAILS) {
                    presentFragment(new MessageDetailsActivity(msg, null));
                } else if (option == OPTION_TRANSLATE) {
                    toggleOrTranslate((ChatMessageCell) v, msg, null);
                }
                if (scrimPopupWindow != null) {
                    scrimPopupWindow.dismiss();
                }
            });
            if (option == OPTION_TRANSLATE) {
                cell.setOnLongClickListener(v1 -> {
                    if (msg.messageOwner != null && (msg.messageOwner.translated || msg.messageOwner.translatedPoll != null)) {
                        return true;
                    }
                    Translator.showTargetLangSelect(cell, false, false, (locale) -> {
                        if (scrimPopupWindow != null) {
                            scrimPopupWindow.dismiss();
                            scrimPopupWindow = null;
                        }
                        toggleOrTranslate((ChatMessageCell) v, msg, locale);
                        return Unit.INSTANCE;
                    });
                    return true;
                });
            }
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
                dimBehindView(false);
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
        dimBehindView(v, true);
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
            if (holder.itemView instanceof NekoMessageCell) {
                ((NekoMessageCell) holder.itemView).setAyuDelegate(null);
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
            NekoMessageCell cell = new NekoMessageCell(context, currentAccount);
            cell.setShowAyuDeletedMark(false);
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 1) {
                var ayuMessageDetailCell = (NekoMessageCell) holder.itemView;

                var editedMessage = messages.get(position);
                MessageObject msg;
                if (position >= 0 && position < messageObjects.size()) {
                    msg = messageObjects.get(position);
                    if (msg == null) {
                        msg = createMessageObject(editedMessage);
                        messageObjects.set(position, msg);
                    }
                } else {
                    msg = createMessageObject(editedMessage);
                }

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
    }

    private MessageObject createMessageObject(EditedMessage editedMessage) {
        int currentAccount = getCurrentAccount();
        var msg = new TLRPC.TL_message();
        AyuMessageUtils.map(editedMessage, msg, currentAccount);
        AyuMessageUtils.mapMedia(editedMessage, msg, currentAccount);

        msg.ayuDeleted = true;
        msg.date = editedMessage.entityCreateDate;
        msg.edit_hide = true;
        if (msg.media != null) {
            if (messageObject.isVoiceOnce() || messageObject.isRoundOnce()) {
                inheritTtlSeconds(msg, messageObject.messageOwner);
            } else {
                msg.media.ttl_seconds = 0;
            }
        }
        // fix reply state
        if (messageObject.messageOwner.replyMessage != null) {
            msg.replyMessage = messageObject.messageOwner.replyMessage;
            msg.reply_to = messageObject.messageOwner.reply_to;
        }
        // prefer the current message's cached media only if the original file still exists
        boolean localFileFound = false;
        if (messageObject.isVoice()) {
            msg.media = messageObject.messageOwner.media;
        } else if (editedMessage.documentType == AyuConstants.DOCUMENT_TYPE_FILE) {
            File originalPath = FileLoader.getInstance(currentAccount).getPathToMessage(messageObject.messageOwner);
            if (!messageObject.isAyuDeleted() && originalPath.exists() && Objects.equals(editedMessage.mediaPath, originalPath.getAbsolutePath())) {
                msg.media.document = messageObject.messageOwner.media.document;
                localFileFound = true;
            } else { // try to find local file for media that was saved
                File localFile = findSavedMedia(editedMessage);
                if (localFile != null) {
                    updateDocumentMediaWithLocalFile(msg, localFile, editedMessage);
                    localFileFound = true;
                }
            }
        } else if (editedMessage.documentType == AyuConstants.DOCUMENT_TYPE_STORY) {
            File localFile = findSavedMedia(editedMessage);
            if (localFile != null) {
                updateStoryMediaWithLocalFile(msg, localFile);
                localFileFound = true;
            }
        } else if (editedMessage.documentType == AyuConstants.DOCUMENT_TYPE_PHOTO) {
            File localFile = findSavedMedia(editedMessage);
            if (localFile != null) {
                updatePhotoMediaWithLocalFile(msg, localFile);
                localFileFound = true;
            }
        }
        MessageObject messageObj = new MessageObject(getCurrentAccount(), msg, false, true);
        if (localFileFound && msg.attachPath != null) {
            messageObj.attachPathExists = true;
        }
        return messageObj;
    }

    private File findSavedMedia(EditedMessage editedMessage) {
        File attachmentsDir = AyuMessagesController.attachmentsPath;
        if (!attachmentsDir.exists() && !attachmentsDir.mkdirs()) {
            return null;
        }
        String[] fileNames = cachedAttachmentFileNames;
        if (fileNames == null) {
            return null;
        }
        String ttlPrefix = "ttl_" + editedMessage.dialogId + "_" + editedMessage.messageId + "_";
        ArrayList<File> ttlMatches = new ArrayList<>();
        for (String name : fileNames) {
            if (name.startsWith(ttlPrefix)) {
                ttlMatches.add(new File(attachmentsDir, name));
            }
        }
        if (!ttlMatches.isEmpty()) {
            File ttlMatch = AyuMessageUtils.getLargestNonEmpty(ttlMatches.toArray(new File[0]));
            if (ttlMatch != null) {
                return ttlMatch;
            }
        }
        if (editedMessage.mediaPath != null && !editedMessage.mediaPath.isEmpty()) {
            String baseName = new File(editedMessage.mediaPath).getName();
            return findExistingFileByBaseNameCached(attachmentsDir, fileNames, baseName);
        }
        return null;
    }

    private File findExistingFileByBaseNameCached(File attachmentsDir, String[] fileNames, String baseName) {
        // exact match
        File exactMatch = new File(attachmentsDir, baseName);
        if (exactMatch.exists()) {
            return exactMatch;
        }
        String nameWithoutExtension = AyuUtils.removeExtension(baseName);
        String extension = AyuUtils.getExtension(baseName);
        // find matching files from cache
        ArrayList<File> matchingFiles = new ArrayList<>();
        for (String name : fileNames) {
            if (!name.endsWith(extension)) {
                continue;
            }
            if (name.equals(baseName)) {
                matchingFiles.add(new File(attachmentsDir, name));
                continue;
            }
            if (!name.startsWith(nameWithoutExtension)) {
                continue;
            }
            int length = nameWithoutExtension.length();
            if (name.length() <= length) {
                continue;
            }
            char ch = name.charAt(length);
            if (ch == '@' || ch == '#') {
                matchingFiles.add(new File(attachmentsDir, name));
            }
        }
        if (matchingFiles.isEmpty()) {
            return null;
        }
        return AyuMessageUtils.getLargestNonEmpty(matchingFiles.toArray(new File[0]));
    }

    private void updatePhotoMediaWithLocalFile(TLRPC.Message msg, File localFile) {
        Pair<Integer, Integer> size = AyuUtils.extractImageSizeFromFile(localFile.getAbsolutePath());
        if (size == null) {
            size = new Pair<>(500, 500); // fallback
        }
        TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
        photoSize.size = (int) localFile.length();
        photoSize.w = size.first;
        photoSize.h = size.second;
        photoSize.type = "y";
        photoSize.location = new AyuFileLocation(localFile.getAbsolutePath());
        if (msg.media instanceof TLRPC.TL_messageMediaPhoto mediaPhoto && msg.media.photo != null) {
            mediaPhoto.photo.sizes.clear();
            mediaPhoto.photo.sizes.add(photoSize);
        } else {
            TLRPC.TL_messageMediaPhoto mediaPhoto = new TLRPC.TL_messageMediaPhoto();
            mediaPhoto.flags = 1;
            mediaPhoto.photo = new TLRPC.TL_photo();
            mediaPhoto.photo.has_stickers = false;
            mediaPhoto.photo.date = msg.date;
            mediaPhoto.photo.sizes.add(photoSize);
            msg.media = mediaPhoto;
        }
        msg.attachPath = localFile.getAbsolutePath();
    }

    private void updateStoryMediaWithLocalFile(TLRPC.Message msg, File localFile) {
        if (!(msg.media instanceof TLRPC.TL_messageMediaStory story) || story.storyItem == null || story.storyItem.media == null) {
            return;
        }
        String filePath = localFile.getAbsolutePath();
        msg.attachPath = filePath;
        story.storyItem.attachPath = filePath;
        TLRPC.MessageMedia storyMedia = story.storyItem.media;
        if (storyMedia.document != null) {
            storyMedia.document.localPath = filePath;
            return;
        }
        if (storyMedia.photo != null) {
            Pair<Integer, Integer> size = AyuUtils.extractImageSizeFromFile(filePath);
            if (size == null) {
                size = new Pair<>(500, 500);
            }
            TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
            photoSize.size = (int) localFile.length();
            photoSize.w = size.first;
            photoSize.h = size.second;
            photoSize.type = "y";
            photoSize.location = new AyuFileLocation(filePath);
            if (storyMedia.photo.sizes != null) {
                storyMedia.photo.sizes.clear();
                storyMedia.photo.sizes.add(photoSize);
            }
        }
    }

    private void updateDocumentMediaWithLocalFile(TLRPC.Message msg, File localFile, EditedMessage editedMessage) {
        String filePath = localFile.getAbsolutePath();
        msg.attachPath = filePath;
        // if media already exists just update the path
        if (msg.media instanceof TLRPC.TL_messageMediaDocument && msg.media.document != null) {
            msg.media.document.localPath = filePath;
            return;
        }
        // create new document media structure for video
        TLRPC.TL_messageMediaDocument mediaDocument = new TLRPC.TL_messageMediaDocument();
        mediaDocument.flags = 1;
        mediaDocument.document = new TLRPC.TL_document();
        mediaDocument.document.date = msg.date;
        mediaDocument.document.localPath = filePath;
        mediaDocument.document.file_name = AyuUtils.getReadableFilename(localFile.getName());
        mediaDocument.document.file_name_fixed = AyuUtils.getReadableFilename(localFile.getName());
        mediaDocument.document.size = localFile.length();
        mediaDocument.document.mime_type = editedMessage.mimeType != null ? editedMessage.mimeType : "video/mp4";
        // restore document attributes from serialized data
        if (editedMessage.documentAttributesSerialized != null && editedMessage.documentAttributesSerialized.length > 0) {
            mediaDocument.document.attributes = AyuMessageUtils.deserializeMultiple(editedMessage.documentAttributesSerialized, nativeByteBuffer -> TLRPC.DocumentAttribute.TLdeserialize(nativeByteBuffer, nativeByteBuffer.readInt32(false), false));
        }
        // if mime_type indicates video but no video attribute exists, create one
        String mimeType = mediaDocument.document.mime_type;
        if (mimeType != null && mimeType.startsWith("video/")) {
            boolean hasVideoAttr = false;
            for (TLRPC.DocumentAttribute attr : mediaDocument.document.attributes) {
                if (attr instanceof TLRPC.TL_documentAttributeVideo) {
                    hasVideoAttr = true;
                    break;
                }
            }
            if (!hasVideoAttr) {
                TLRPC.TL_documentAttributeVideo videoAttr = new TLRPC.TL_documentAttributeVideo();
                videoAttr.supports_streaming = true;
                // extract video dimensions and duration from file
                try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                    retriever.setDataSource(filePath);
                    String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                    String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                    String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (width != null) {
                        videoAttr.w = Integer.parseInt(width);
                    }
                    if (height != null) {
                        videoAttr.h = Integer.parseInt(height);
                    }
                    if (duration != null) {
                        videoAttr.duration = Long.parseLong(duration) / 1000.0;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                mediaDocument.document.attributes.add(videoAttr);
            }
        }
        // restore thumbnails from serialized data
        if (editedMessage.thumbsSerialized != null && editedMessage.thumbsSerialized.length > 0) {
            ArrayList<TLRPC.PhotoSize> thumbs = AyuMessageUtils.deserializeMultiple(editedMessage.thumbsSerialized, nativeByteBuffer -> TLRPC.PhotoSize.TLdeserialize(0L, 0L, 0L, nativeByteBuffer, nativeByteBuffer.readInt32(false), false));
            for (TLRPC.PhotoSize photoSize : thumbs) {
                if (photoSize != null) {
                    mediaDocument.document.thumbs.add(photoSize);
                }
            }
        }
        msg.media = mediaDocument;
    }

    private void rebuildMessageObjects() {
        messageObjects.clear();
        if (messages == null) {
            return;
        }
        for (int i = 0; i < messages.size(); i++) {
            messageObjects.add(createMessageObject(messages.get(i)));
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void handleVoiceTranscriptionUpdate(Object... args) {
        if (listView == null || listView.getAdapter() == null || messageObjects.isEmpty()) {
            return;
        }

        MessageObject updated = args != null && args.length > 0 && args[0] instanceof MessageObject ? (MessageObject) args[0] : null;
        long transcriptionId = 0;
        String transcriptionText = null;
        if (args != null && args.length > 1 && args[1] != null) {
            transcriptionId = (Long) args[1];
            transcriptionText = (String) args[2];
        }

        int indexToUpdate = -1;
        for (int i = 0; i < messageObjects.size(); i++) {
            MessageObject local = messageObjects.get(i);
            if (local == null || local.messageOwner == null) {
                continue;
            }
            if (updated == local) {
                indexToUpdate = i;
                break;
            }
            if (transcriptionId != 0 && local.messageOwner.voiceTranscriptionId == transcriptionId) {
                indexToUpdate = i;
                break;
            }
            if (updated != null && updated.getId() == local.getId() && updated.getDialogId() == local.getDialogId()) {
                indexToUpdate = i;
                break;
            }
        }

        if (indexToUpdate >= 0) {
            MessageObject local = messageObjects.get(indexToUpdate);
            if (local != null && local.messageOwner != null) {
                if (transcriptionText != null) {
                    local.messageOwner.voiceTranscription = transcriptionText;
                }
                if (args.length > 3 && args[3] != null) {
                    local.messageOwner.voiceTranscriptionOpen = (Boolean) args[3];
                }
                if (args.length > 4 && args[4] != null) {
                    local.messageOwner.voiceTranscriptionFinal = (Boolean) args[4];
                }
            }
            listView.getAdapter().notifyItemChanged(indexToUpdate);
        } else {
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    static void inheritTtlSeconds(TLRPC.Message targetMessage, TLRPC.Message sourceMessage) {
        if (targetMessage == null || targetMessage.media == null) {
            return;
        }
        if (sourceMessage != null && sourceMessage.media != null) {
            targetMessage.media.ttl_seconds = sourceMessage.media.ttl_seconds;
        } else {
            targetMessage.media.ttl_seconds = 0;
        }
    }

}
