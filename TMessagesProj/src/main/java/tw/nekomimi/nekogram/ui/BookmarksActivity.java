package tw.nekomimi.nekogram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.radolyn.ayugram.database.entities.DeletedMessageFull;
import com.radolyn.ayugram.messages.AyuMessagesController;
import com.radolyn.ayugram.proprietary.AyuMessageUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatScrimPopupContainerLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProviderThemed;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.chat.layouts.ChatActivitySideControlsButtonsLayout;
import org.telegram.ui.Components.inset.WindowInsetsStateHolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

import kotlin.Unit;
import tw.nekomimi.nekogram.helpers.MessageHelper;
import tw.nekomimi.nekogram.llm.LlmConfig;
import tw.nekomimi.nekogram.translate.Translator;
import tw.nekomimi.nekogram.ui.cells.NekoMessageCell;
import xyz.nextalone.nagram.helper.BookmarksHelper;

public class BookmarksActivity extends NekoDelegateFragment {
    private static final int OPTION_SHOW_IN_CHAT = 1;
    private static final int OPTION_DELETE_BOOKMARK = 2;
    private static final int OPTION_COPY = 3;
    private static final int OPTION_COPY_PHOTO = 4;
    private static final int OPTION_COPY_PHOTO_AS_STICKER = 5;
    private static final int OPTION_DETAILS = 6;
    private static final int OPTION_SAVE_TO_GALLERY = 7;
    private static final int OPTION_SAVE_TO_DOWNLOADS = 8;
    private static final int OPTION_TRANSLATE = 9;

    private final long dialogId;
    private final ArrayList<MessageObject> bookmarkedMessages = new ArrayList<>();
    private final ArrayList<MessageObject> filteredMessages = new ArrayList<>();

    private int rowCount;
    private RecyclerListView listView;
    private ChatActivitySideControlsButtonsLayout sideControlsButtonsLayout;
    private boolean pagedownButtonManuallyHidden;
    private ActionBarPopupWindow scrimPopupWindow;
    private ChatActionCell floatingDateView;
    private TextView emptyView;
    private Runnable showEmptyViewRunnable;
    private ActionBarMenuItem searchItem;
    private String searchQuery = "";
    private AnimatorSet floatingDateAnimation;
    private boolean scrollingFloatingDate;
    private final Runnable updateFloatingDateRunnable = this::updateFloatingDateView;
    private final WindowInsetsStateHolder windowInsetsStateHolder = new WindowInsetsStateHolder(this::checkInsets);

    public BookmarksActivity(long dialogId) {
        this.dialogId = dialogId;
    }

    @Override
    protected RecyclerListView getMessageListView() {
        return listView;
    }

    private void checkInsets() {
        if (listView != null) {
            listView.setPadding(0, 0, 0, windowInsetsStateHolder.getCurrentNavigationBarInset() + dp(8));
        }
        updatePagedownButtonPosition();
    }

    private void updatePagedownButtonPosition() {
        if (sideControlsButtonsLayout == null) {
            return;
        }
        ViewGroup.LayoutParams lp = sideControlsButtonsLayout.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams params)) {
            return;
        }
        int bottomMargin = windowInsetsStateHolder.getCurrentNavigationBarInset() + dp(16);
        if (params.bottomMargin != bottomMargin) {
            params.bottomMargin = bottomMargin;
            sideControlsButtonsLayout.setLayoutParams(params);
        }
    }

    private void updatePagedownButtonVisibility(boolean animated) {
        if (sideControlsButtonsLayout == null || listView == null) {
            return;
        }
        boolean canScrollDown = rowCount > 0 && listView.canScrollVertically(1);
        if (!canScrollDown) {
            pagedownButtonManuallyHidden = false;
        }
        boolean show = canScrollDown && !pagedownButtonManuallyHidden;
        sideControlsButtonsLayout.showButton(ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN, show, animated);
    }

    private void onPageDownClicked() {
        if (listView == null || rowCount <= 0) {
            return;
        }
        pagedownButtonManuallyHidden = true;
        updatePagedownButtonVisibility(true);
        listView.smoothScrollToPosition(rowCount - 1);
    }

    private final RecyclerView.OnScrollListener listScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                pagedownButtonManuallyHidden = false;
                scrollingFloatingDate = true;
                updateFloatingDateView();
                showFloatingDateView();
            } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                pagedownButtonManuallyHidden = false;
                scrollingFloatingDate = false;
                hideFloatingDateView(true);
            }
            updatePagedownButtonVisibility(true);
            updateVisibleMessageCells();
        }

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            updateFloatingDateView();
            updatePagedownButtonVisibility(true);
            updateVisibleMessageCells();
        }
    };

    private void updateVisibleMessageCells() {
        if (listView != null) {
            updateVisibleChatMessageCells(listView);
        }
    }

    private static boolean hasAyuDeletedContent(DeletedMessageFull messageFull) {
        return messageFull != null && messageFull.message != null && (!TextUtils.isEmpty(messageFull.message.text) || !TextUtils.isEmpty(messageFull.message.mediaPath) || messageFull.message.documentSerialized != null);
    }

    private void updateBookmarks() {
        updateBookmarks(null);
    }

    private void updateBookmarks(Runnable onComplete) {
        int accountId = getCurrentAccount();
        long userId = getUserConfig().getClientUserId();
        Utilities.globalQueue.postRunnable(() -> {
            int[] messageIds = BookmarksHelper.getBookmarkedMessageIds(accountId, dialogId);
            ArrayList<MessageObject> loaded = new ArrayList<>(messageIds.length);

            for (int messageId : messageIds) {
                TLRPC.Message message = MessagesStorage.getInstance(accountId).getMessage(dialogId, messageId);
                MessageObject messageObject = null;
                if (message != null) {
                    messageObject = new MessageObject(accountId, message, false, true);
                    if (messageObject.messageOwner.media != null) {
                        messageObject.messageOwner.media.ttl_seconds = 0;
                    }
                } else {
                    DeletedMessageFull deleted = AyuMessagesController.getInstance().getMessage(userId, dialogId, messageId);
                    if (hasAyuDeletedContent(deleted)) {
                        var base = deleted.message;
                        var tl = new TLRPC.TL_message();
                        AyuMessageUtils.map(base, tl, accountId);
                        AyuMessageUtils.mapMedia(base, tl, accountId);
                        tl.ayuDeleted = true;
                        messageObject = new MessageObject(accountId, tl, false, true);
                    }
                }
                if (messageObject == null) {
                    messageObject = createMissingMessagePlaceholder(accountId, userId, messageId);
                }
                if (messageObject != null) {
                    messageObject.forceAvatar = true;
                    loaded.add(messageObject);
                }
            }

            loaded.sort(Comparator.comparingInt(MessageObject::getId));

            AndroidUtilities.runOnUIThread(() -> {
                bookmarkedMessages.clear();
                bookmarkedMessages.addAll(loaded);
                applySearchFilter();
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    private MessageObject createMissingMessagePlaceholder(int accountId, long userId, int messageId) {
        if (messageId == 0) {
            return null;
        }

        TLRPC.TL_message tl = new TLRPC.TL_message();
        tl.id = messageId;
        tl.dialog_id = dialogId;
        tl.date = ConnectionsManager.getInstance(accountId).getCurrentTime();
        tl.out = true;

        tl.from_id = new TLRPC.TL_peerUser();
        tl.from_id.user_id = userId;

        tl.peer_id = createPeerId(dialogId);

        String link = buildMessageLink(dialogId, messageId);
        tl.message = link == null ? getString(R.string.ShowInChat) : (getString(R.string.ShowInChat) + "\n" + link);
        if (link != null) {
            int offset = tl.message.indexOf(link);
            if (offset >= 0) {
                TLRPC.TL_messageEntityUrl entity = new TLRPC.TL_messageEntityUrl();
                entity.offset = offset;
                entity.length = link.length();
                tl.entities.add(entity);
            }
        }

        return new MessageObject(accountId, tl, false, true);
    }

    private TLRPC.Peer createPeerId(long dialogId) {
        TLRPC.Peer peerId = null;
        var peer = getMessagesController().getUserOrChat(dialogId);
        if (peer instanceof TLRPC.User user) {
            TLRPC.TL_peerUser p = new TLRPC.TL_peerUser();
            p.user_id = user.id;
            peerId = p;
        } else if (peer instanceof TLRPC.Chat chat) {
            if (ChatObject.isChannel(chat)) {
                TLRPC.TL_peerChannel p = new TLRPC.TL_peerChannel();
                p.channel_id = chat.id;
                peerId = p;
            } else {
                TLRPC.TL_peerChat p = new TLRPC.TL_peerChat();
                p.chat_id = chat.id;
                peerId = p;
            }
        }

        if (peerId != null) {
            return peerId;
        }

        if (DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.TL_peerUser p = new TLRPC.TL_peerUser();
            p.user_id = getUserConfig().getClientUserId();
            return p;
        } else if (dialogId > 0) {
            TLRPC.TL_peerUser p = new TLRPC.TL_peerUser();
            p.user_id = dialogId;
            return p;
        } else {
            TLRPC.TL_peerChat p = new TLRPC.TL_peerChat();
            p.chat_id = -dialogId;
            return p;
        }
    }

    private String buildMessageLink(long dialogId, int messageId) {
        if (messageId == 0 || DialogObject.isEncryptedDialog(dialogId)) {
            return null;
        }

        var peer = getMessagesController().getUserOrChat(dialogId);
        if (peer instanceof TLRPC.Chat chat) {
            if (!TextUtils.isEmpty(chat.username)) {
                return "https://t.me/" + chat.username + "/" + messageId;
            }
            if (ChatObject.isChannel(chat)) {
                return "https://t.me/c/" + chat.id + "/" + messageId;
            }
            return "tg://openmessage?chat_id=" + chat.id + "&message_id=" + messageId;
        } else if (peer instanceof TLRPC.User user) {
            return "tg://openmessage?user_id=" + user.id + "&message_id=" + messageId;
        }

        if (dialogId > 0) {
            return "tg://openmessage?user_id=" + dialogId + "&message_id=" + messageId;
        }
        return "https://t.me/c/" + (-dialogId) + "/" + messageId;
    }

    @Override
    public View createView(Context context) {
        var peer = getMessagesController().getUserOrChat(dialogId);
        String name = switch (peer) {
            case null -> getString(R.string.BookmarksManager);
            case TLRPC.User user -> user.first_name;
            case TLRPC.Chat chat -> chat.title;
            default -> getString(R.string.BookmarksManager);
        };

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(name);
        updateActionBarCount();
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true);
        searchItem.setSearchFieldHint(getString(R.string.Search));
        searchItem.setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searchItem.getSearchField().setText(searchQuery);
                searchItem.getSearchField().setSelection(searchItem.getSearchField().length());
            }

            @Override
            public void onSearchCollapse() {
                searchQuery = "";
                applySearchFilter();
            }

            @Override
            public void onTextChanged(EditText editText) {
                String newQuery = editText.getText().toString();
                if (!TextUtils.equals(searchQuery, newQuery)) {
                    searchQuery = newQuery;
                    applySearchFilter();
                }
            }

            @Override
            public void onSearchPressed(EditText editText) {
                searchQuery = editText.getText().toString();
                applySearchFilter();
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
        listView.setAdapter(new ListAdapter(context, getCurrentAccount()));
        setupMessageListItemAnimator(listView);
        listView.setSelectorType(9);
        listView.setSelectorDrawableColor(0);
        listView.setClipToPadding(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position, x, y) -> {
            if (view instanceof NekoMessageCell) {
                createMenu(view, x, y, position);
            }
        });
        listView.addOnScrollListener(listScrollListener);

        floatingDateView = new ChatActionCell(context) {
            @Override
            public boolean isFloating() {
                return true;
            }
        };
        floatingDateView.setCustomDate((int) (System.currentTimeMillis() / 1000), false, false);
        floatingDateView.setAlpha(0.0f);
        floatingDateView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        floatingDateView.setInvalidateColors(true);
        frameLayout.addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

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
        emptyView.setText(getString(R.string.NoBookmarks));
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyView.setTypeface(AndroidUtilities.bold());
        emptyView.setTextColor(Theme.getColor(Theme.key_chat_serviceText, getResourceProvider()));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        emptyView.setPadding(dp(20), dp(4), dp(20), dp(6));
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        BlurredBackgroundSourceColor pagedownSourceColor = new BlurredBackgroundSourceColor();
        pagedownSourceColor.setColor(Color.TRANSPARENT);
        BlurredBackgroundDrawableViewFactory pagedownBackgroundDrawableFactory = new BlurredBackgroundDrawableViewFactory(pagedownSourceColor);
        BlurredBackgroundColorProviderThemed pagedownColorProvider = new BlurredBackgroundColorProviderThemed(getResourceProvider(), Theme.key_chat_messagePanelBackground);
        sideControlsButtonsLayout = new ChatActivitySideControlsButtonsLayout(context, getResourceProvider(), pagedownColorProvider, pagedownBackgroundDrawableFactory);
        sideControlsButtonsLayout.setOnClickListener((buttonId, v) -> {
            if (buttonId == ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN) {
                onPageDownClicked();
            }
        });
        frameLayout.addView(sideControlsButtonsLayout, LayoutHelper.createFrame(57, 300, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 0, 16));
        updatePagedownButtonPosition();

        listView.post(updateFloatingDateRunnable);

        updateBookmarks(() -> {
            if (rowCount > 0 && listView != null) {
                listView.scrollToPosition(rowCount - 1);
                listView.post(this::updateVisibleMessageCells);
            }
            updatePagedownButtonVisibility(false);
        });

        return fragmentView;
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

        updateActionBarCount();
        updateBookmarks();
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

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

        Bulletin.removeDelegate(this);

        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
            scrimPopupWindow = null;
        }

        if (floatingDateAnimation != null) {
            floatingDateAnimation.cancel();
            floatingDateAnimation = null;
        }

        if (showEmptyViewRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(showEmptyViewRunnable);
            showEmptyViewRunnable = null;
        }

        AndroidUtilities.cancelRunOnUIThread(updateFloatingDateRunnable);

        if (listView != null) {
            listView.removeCallbacks(updateFloatingDateRunnable);
            listView.removeOnScrollListener(listScrollListener);
            listView.setAdapter(null);
        }

        if (searchItem != null) {
            searchItem.setActionBarMenuItemSearchListener(null);
            searchItem = null;
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

        items.add(getString(R.string.ShowInChat));
        icons.add(R.drawable.msg_openin);
        options.add(OPTION_SHOW_IN_CHAT);

        items.add(getString(R.string.RemoveBookmark));
        icons.add(R.drawable.msg_unfave);
        options.add(OPTION_DELETE_BOOKMARK);

        String textToCopy = msg.messageOwner != null ? msg.messageOwner.message : null;
        if (!TextUtils.isEmpty(textToCopy)) {
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
            cell.setOnClickListener(v1 -> {
                if (option == OPTION_SHOW_IN_CHAT) {
                    Bundle args = new Bundle();
                    long did = msg.getDialogId();
                    if (DialogObject.isEncryptedDialog(did)) {
                        args.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                    } else if (DialogObject.isUserDialog(did)) {
                        args.putLong("user_id", did);
                    } else {
                        TLRPC.Chat chat = getMessagesController().getChat(-did);
                        if (chat != null && chat.migrated_to != null) {
                            args.putLong("migrated_to", did);
                            did = -chat.migrated_to.channel_id;
                        }
                        args.putLong("chat_id", -did);
                    }
                    args.putInt("message_id", msg.getId());
                    NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.closeChats);
                    presentFragment(new ChatActivity(args), false, false);
                } else if (option == OPTION_DELETE_BOOKMARK) {
                    BookmarksHelper.removeBookmark(getCurrentAccount(), dialogId, msg.getId());
                    if (position >= 0 && position < filteredMessages.size()) {
                        MessageObject toRemove = filteredMessages.remove(position);
                        bookmarkedMessages.remove(toRemove);
                        rowCount = filteredMessages.size();
                        notifyMessageListItemRemoved(listView, position);
                        updateActionBarCount();
                        updateEmptyView(rowCount == 0);
                        if (listView != null) {
                            listView.post(() -> {
                                updatePagedownButtonVisibility(false);
                                updateVisibleMessageCells();
                            });
                        } else {
                            updatePagedownButtonVisibility(false);
                        }
                    } else {
                        updateBookmarks();
                    }
                } else if (option == OPTION_COPY) {
                    String text = msg.messageOwner != null ? msg.messageOwner.message : null;
                    if (!TextUtils.isEmpty(text)) {
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

    private void updateActionBarCount() {
        if (actionBar == null) {
            return;
        }
        int count = bookmarkedMessages.size();
        actionBar.setSubtitle(getString(R.string.BookmarksManager) + " (" + count + "/" + BookmarksHelper.MAX_PER_CHAT + ")");
    }

    private void updateFloatingDateView() {
        if (floatingDateView == null || listView == null) {
            return;
        }
        MessageObject messageObject = getTopVisibleMessageObject();
        if (messageObject == null || messageObject.messageOwner == null) {
            hideFloatingDateView(false);
            return;
        }
        floatingDateView.setCustomDate(messageObject.messageOwner.date, false, true);
        if (scrollingFloatingDate) {
            showFloatingDateView();
        }
    }

    private MessageObject getTopVisibleMessageObject() {
        if (listView == null) {
            return null;
        }
        MessageObject result = null;
        int minTop = Integer.MAX_VALUE;
        for (int i = 0, count = listView.getChildCount(); i < count; i++) {
            View child = listView.getChildAt(i);
            if (!(child instanceof ChatMessageCell)) {
                continue;
            }
            int top = child.getTop();
            if (top < minTop) {
                minTop = top;
                result = ((ChatMessageCell) child).getMessageObject();
            }
        }
        return result;
    }

    private void showFloatingDateView() {
        if (floatingDateView == null) {
            return;
        }
        if (floatingDateAnimation != null) {
            floatingDateAnimation.cancel();
            floatingDateAnimation = null;
        }
        if (floatingDateView.getTag() != null) {
            floatingDateView.setAlpha(1f);
            return;
        }
        floatingDateView.setTag(1);
        floatingDateAnimation = new AnimatorSet();
        floatingDateAnimation.setDuration(150);
        floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, View.ALPHA, 1f));
        floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation == floatingDateAnimation) {
                    floatingDateAnimation = null;
                }
            }
        });
        floatingDateAnimation.start();
    }

    private void hideFloatingDateView(boolean animated) {
        if (floatingDateView == null || floatingDateView.getTag() == null) {
            return;
        }
        floatingDateView.setTag(null);
        if (floatingDateAnimation != null) {
            floatingDateAnimation.cancel();
            floatingDateAnimation = null;
        }
        if (animated) {
            floatingDateAnimation = new AnimatorSet();
            floatingDateAnimation.setDuration(150);
            floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, View.ALPHA, 0f));
            floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation == floatingDateAnimation) {
                        floatingDateAnimation = null;
                    }
                }
            });
            floatingDateAnimation.setStartDelay(200);
            floatingDateAnimation.start();
        } else {
            floatingDateView.setAlpha(0f);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void notifyAdapterDataChanged() {
        var adapter = listView == null ? null : listView.getAdapter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void applySearchFilter() {
        filteredMessages.clear();
        if (TextUtils.isEmpty(searchQuery)) {
            filteredMessages.addAll(bookmarkedMessages);
        } else {
            String q = searchQuery.toLowerCase(Locale.getDefault());
            for (MessageObject msg : bookmarkedMessages) {
                String text = msg.messageOwner != null ? msg.messageOwner.message : null;
                if (!TextUtils.isEmpty(text) && text.toLowerCase(Locale.getDefault()).contains(q)) {
                    filteredMessages.add(msg);
                    continue;
                }
                String attachPath = msg.messageOwner != null ? msg.messageOwner.attachPath : null;
                if (!TextUtils.isEmpty(attachPath) && attachPath.toLowerCase(Locale.getDefault()).contains(q)) {
                    filteredMessages.add(msg);
                }
            }
        }
        rowCount = filteredMessages.size();
        notifyAdapterDataChanged();
        updateActionBarCount();
        updateEmptyView();
        if (listView != null) {
            listView.post(() -> {
                updatePagedownButtonVisibility(false);
                updateVisibleMessageCells();
            });
        } else {
            updatePagedownButtonVisibility(false);
        }
    }

    private void updateEmptyView() {
        updateEmptyView(false);
    }

    private void updateEmptyView(boolean delayIfEmpty) {
        showEmptyViewRunnable = updateListEmptyView(() -> emptyView, () -> listView, rowCount == 0, delayIfEmpty, showEmptyViewRunnable, () -> showEmptyViewRunnable = null);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {

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
            return new RecyclerListView.Holder(new NekoMessageCell(context, currentAccount));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 1) {
                var cell = (NekoMessageCell) holder.itemView;
                var msg = filteredMessages.get(position);
                msg.forceAvatar = true;
                cell.setAyuDelegate(BookmarksActivity.this);
                cell.setMessageObject(msg, null, false, false, false);
                cell.setAlpha(1f);
                cell.setId(position);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return position >= 0 && position < filteredMessages.size() ? 1 : 0;
        }
    }
}
