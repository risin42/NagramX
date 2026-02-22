package tw.nekomimi.nekogram.ui;

import static org.telegram.messenger.LocaleController.getString;
import static tw.nekomimi.nekogram.parts.MessageTransKt.TRANSLATION_SEPARATOR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ChatListItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.ContactAddActivity;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.MessageHelper;
import tw.nekomimi.nekogram.translate.Translator;
import tw.nekomimi.nekogram.translate.TranslatorKt;
import tw.nekomimi.nekogram.ui.cells.NekoMessageCell;
import tw.nekomimi.nekogram.utils.AlertUtil;
import tw.nekomimi.nekogram.utils.AndroidUtil;
import xyz.nextalone.nagram.NaConfig;

public abstract class NekoDelegateFragment extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, NekoMessageCell.NekoMessageCellDelegate {

    private final WeakHashMap<ChatMessageCell, ValueAnimator> cellChangeAnimators = new WeakHashMap<>();
    private final WeakHashMap<ChatMessageCell, ValueAnimator> cellTopClipAnimators = new WeakHashMap<>();
    private final WeakHashMap<RecyclerView, ValueAnimator> overlaySnapshotAnimators = new WeakHashMap<>();
    private final WeakHashMap<RecyclerView, ValueAnimator> visiblePartAnimators = new WeakHashMap<>();
    private final WeakHashMap<RecyclerView, OwnedBitmapDrawable> overlayDrawables = new WeakHashMap<>();
    private final WeakHashMap<RecyclerView, AnchorShiftPreDrawListener> pendingShiftListeners = new WeakHashMap<>();

    private static final float DEFAULT_SCRIM_DIM_AMOUNT = 0.2f;

    @Nullable
    private Paint scrimPaint;
    @Nullable
    private View scrimView;
    @Nullable
    private ValueAnimator scrimAnimator;
    private final int[] scrimTmpLocation = new int[2];
    private final int[] scrimTmpLocation2 = new int[2];
    private final Rect scrimTmpRect = new Rect();

    protected class ScrimFrameLayout extends SizeNotifierFrameLayout {

        public ScrimFrameLayout(Context context) {
            super(context);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            NekoDelegateFragment.this.drawScrimOverlay(this, canvas);
        }
    }

    protected void setupMessageListItemAnimator(@NonNull RecyclerListView listView) {
        if (!MessagesController.getGlobalMainSettings().getBoolean("view_animations", true)) {
            if (listView.getItemAnimator() != null) {
                listView.setItemAnimator(null);
            }
            return;
        }

        RecyclerView.ItemAnimator currentAnimator = listView.getItemAnimator();
        if (!(currentAnimator instanceof ChatListItemAnimator)) {
            listView.setItemAnimator(new ChatListItemAnimator(null, listView, getResourceProvider()));
        }
    }

    protected void notifyMessageListItemRemoved(@Nullable RecyclerListView listView, int position) {
        if (listView == null) {
            return;
        }
        RecyclerView.Adapter<?> adapter = listView.getAdapter();
        if (adapter == null) {
            return;
        }
        setupMessageListItemAnimator(listView);
        int newCount = adapter.getItemCount();
        if (position < 0 || position > newCount) {
            return;
        }

        adapter.notifyItemRemoved(position);
        if (newCount > 0) {
            int start = Math.max(0, position - 1);
            int end = Math.min(newCount - 1, position);
            if (end >= start) {
                adapter.notifyItemRangeChanged(start, end - start + 1);
            }
        }
    }

    protected long getEmptyViewDelayMs(@Nullable RecyclerListView listView) {
        if (listView == null) {
            return 0;
        }
        RecyclerView.ItemAnimator itemAnimator = listView.getItemAnimator();
        if (itemAnimator == null) {
            return 0;
        }
        return Math.max(itemAnimator.getRemoveDuration(), ChatListItemAnimator.DEFAULT_DURATION);
    }

    @Nullable
    protected Runnable updateListEmptyView(@NonNull Supplier<? extends View> emptyViewProvider, @NonNull Supplier<? extends RecyclerListView> listViewProvider, boolean isEmpty, boolean delayIfEmpty, @Nullable Runnable showEmptyViewRunnable, @NonNull Runnable clearEmptyViewRunnable) {
        View emptyView = emptyViewProvider.get();
        RecyclerListView listView = listViewProvider.get();
        if (emptyView == null || listView == null) {
            return showEmptyViewRunnable;
        }
        if (showEmptyViewRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(showEmptyViewRunnable);
            clearEmptyViewRunnable.run();
        }
        if (isEmpty) {
            if (delayIfEmpty) {
                long delayMs = getEmptyViewDelayMs(listView);
                if (delayMs <= 0) {
                    emptyView.setVisibility(View.VISIBLE);
                    listView.setVisibility(View.GONE);
                    return null;
                }
                Runnable newRunnable = () -> {
                    clearEmptyViewRunnable.run();
                    View updatedEmptyView = emptyViewProvider.get();
                    if (updatedEmptyView != null) {
                        updatedEmptyView.setVisibility(View.VISIBLE);
                    }
                    RecyclerListView updatedListView = listViewProvider.get();
                    if (updatedListView != null) {
                        updatedListView.setVisibility(View.GONE);
                    }
                };
                AndroidUtilities.runOnUIThread(newRunnable, delayMs);
                return newRunnable;
            } else {
                emptyView.setVisibility(View.VISIBLE);
                listView.setVisibility(View.GONE);
                return null;
            }
        } else {
            emptyView.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            return null;
        }
    }

    @Override
    public void onTextCopied() {
        BulletinFactory.of(this).createCopyBulletin(getString(R.string.MessageCopied)).show();
    }

    @Override
    public void onImagePressed(ChatMessageCell cell) {
        if (cell.getMessageObject() != null) {
            MessageObject messageObject = cell.getMessageObject();
            if (messageObject.isSticker() || messageObject.isAnimatedSticker()) {
                var inputStickerSet = messageObject.getInputStickerSet();
                if (inputStickerSet != null) {
                    showDialog(new StickersAlert(getParentActivity(), this, inputStickerSet, null, null, false));
                }
            } else {
                AndroidUtil.openForView(messageObject, getParentActivity(), getResourceProvider());
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
    public void didPressInstantButton(ChatMessageCell cell, int type) {
        MessageObject messageObject = cell.getMessageObject();
        if (messageObject == null || getParentActivity() == null) return;
        try {
            if (type == 0 && messageObject.messageOwner != null && messageObject.messageOwner.media != null && messageObject.messageOwner.media.webpage != null && messageObject.messageOwner.media.webpage.cached_page != null) {
                createArticleViewer(false).open(messageObject);
                return;
            }
            if (type == ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_VIEW) {
                long uid = messageObject.messageOwner.media.user_id;
                Bundle args = new Bundle();
                if (uid > 0) args.putLong("user_id", uid);
                else args.putLong("chat_id", -uid);
                presentFragment(new ProfileActivity(args));
                return;
            } else if (type == ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_SEND_MESSAGE) {
                long uid = messageObject.messageOwner.media.user_id;
                Bundle args = new Bundle();
                args.putLong("user_id", uid);
                presentFragment(new ChatActivity(args));
                return;
            } else if (type == ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_ADD) {
                long uid = messageObject.messageOwner.media.user_id;
                TLRPC.User user = null;
                if (uid != 0) {
                    user = getMessagesController().getUser(uid);
                }
                if (user != null) {
                    String phone;
                    if (!TextUtils.isEmpty(messageObject.vCardData)) {
                        phone = messageObject.vCardData.toString();
                    } else {
                        if (!TextUtils.isEmpty(user.phone)) {
                            phone = PhoneFormat.getInstance().format("+" + user.phone);
                        } else {
                            phone = MessageObject.getMedia(messageObject.messageOwner).phone_number;
                            if (!TextUtils.isEmpty(phone)) {
                                phone = PhoneFormat.getInstance().format(phone);
                            } else {
                                phone = getString(R.string.NumberUnknown);
                            }
                        }
                    }
                    Bundle args = new Bundle();
                    args.putLong("user_id", user.id);
                    args.putString("phone", phone);
                    args.putBoolean("addContact", true);
                    presentFragment(new ContactAddActivity(args));
                }
                return;
            }
            TLRPC.WebPage webPage = messageObject.getStoryMentionWebpage();
            if (webPage == null && messageObject.messageOwner != null && messageObject.messageOwner.media != null) {
                webPage = messageObject.messageOwner.media.webpage;
            }
            if (webPage == null || webPage.url == null) {
                return;
            }
            Browser.openUrl(getParentActivity(), Uri.parse(webPage.url), true, true, false, null, null, false, true, false);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void didPressBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button) {
        if (button == null || getParentActivity() == null) return;
        try {
            if (button instanceof TLRPC.TL_keyboardButtonUrl) {
                String url = button.url;
                if (!TextUtils.isEmpty(url)) {
                    Browser.openUrl(getParentActivity(), url);
                }
            } else if (button instanceof TLRPC.TL_keyboardButtonSwitchInline) {
                // show toast since we can't switch
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.ErrorOccurred)).show();
            } else {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.ErrorOccurred)).show();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public boolean didLongPressBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button) {
        if (button == null || getParentActivity() == null) return false;
        try {
            if (!TextUtils.isEmpty(button.url)) {
                AndroidUtilities.addToClipboard(button.url);
                BulletinFactory.of(this).createCopyLinkBulletin().show();
            } else {
                BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity(), false, getResourceProvider());
                builder.setTitle(button.text);
                builder.setItems(new CharSequence[]{
                        getString(R.string.Copy),
                        button.data != null ? getString(R.string.CopyCallback) : null,
                        button.query != null ? getString(R.string.CopyInlineQuery) : null,
                        button.user_id != 0 ? getString(R.string.CopyID) : null
                }, (dialog, which) -> {
                    if (which == 0) {
                        AndroidUtilities.addToClipboard(button.text);
                    } else if (which == 1) {
                        AndroidUtilities.addToClipboard(MessageHelper.getTextOrBase64(button.data));
                    } else if (which == 2) {
                        AndroidUtilities.addToClipboard(button.query);
                    } else if (which == 3) {
                        AndroidUtilities.addToClipboard(String.valueOf(button.user_id));
                    }
                    BulletinFactory.of(this).createCopyBulletin(getString(R.string.TextCopied)).show();
                });
                showDialog(builder.create());
            }
            try {
                if (!NekoConfig.disableVibration.Bool()) cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            } catch (Exception ignore) {
            }
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    @Override
    public boolean needPlayMessage(ChatMessageCell cell, MessageObject messageObject, boolean muted) {
        if (messageObject == null) {
            return false;
        }
        if (messageObject.isVoice() || messageObject.isRoundVideo() || messageObject.isMusic()) {
            return MediaController.getInstance().playMessage(messageObject, muted);
        }
        return false;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Nullable
    protected RecyclerListView getMessageListView() {
        return null;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        RecyclerListView lv = getMessageListView();
        if (lv != null) {
            lv.setPadding(0, 0, 0, bottom);
            lv.setClipToPadding(false);
        }
    }

    protected TranslateController getTranslateController() {
        return getMessagesController().getTranslateController();
    }

    protected void toggleOrTranslate(@NonNull ChatMessageCell messageCell, @NonNull MessageObject messageObject, Locale targetLocale) {
        if (messageObject.messageOwner == null || messageCell.getMessageObject() != messageObject) {
            return;
        }

        final boolean isPollMessage = MessageObject.getMedia(messageObject.messageOwner) instanceof TLRPC.TL_messageMediaPoll;

        if (messageObject.messageOwner.translatedPoll != null) {
            prepareMessageCellForSnapshot(messageCell);
            final Bitmap snapshotBefore = shouldCaptureSnapshot(messageCell) ? captureCellSnapshot(messageCell) : null;

            getTranslateController().removeAsTranslatingItem(messageObject);
            getTranslateController().removeAsManualTranslate(messageObject);
            messageObject.messageOwner.translated = false;
            messageObject.messageOwner.translatedPoll = null;
            messageObject.messageOwner.translatedToLanguage = null;
            messageObject.translated = false;

            updateMessageCellAnimated(messageCell, messageObject, snapshotBefore);
            return;
        }

        String originalText = messageObject.messageOwner.message;
        if (TextUtils.isEmpty(originalText) && !isPollMessage) {
            return;
        }

        if (messageObject.messageOwner.translated) {
            prepareMessageCellForSnapshot(messageCell);
            final Bitmap snapshotBefore = shouldCaptureSnapshot(messageCell) ? captureCellSnapshot(messageCell) : null;

            getTranslateController().removeAsTranslatingItem(messageObject);
            getTranslateController().removeAsManualTranslate(messageObject);
            messageObject.messageOwner.translated = false;
            messageObject.messageOwner.translatedMessage = null;
            messageObject.messageOwner.translatedText = null;
            messageObject.messageOwner.translatedToLanguage = null;
            messageObject.translated = false;
            messageObject.caption = null;
            messageObject.applyNewText(originalText);
            messageObject.generateCaption();

            updateMessageCellAnimated(messageCell, messageObject, snapshotBefore);
            return;
        }

        final Locale resolvedTargetLocale;
        if (targetLocale == null) {
            String lang = NekoConfig.translateToLang.String();
            resolvedTargetLocale = TranslatorKt.getCode2Locale(lang == null ? "" : lang);
        } else {
            resolvedTargetLocale = targetLocale;
        }

        if (isPollMessage) {
            final TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) MessageObject.getMedia(messageObject.messageOwner);
            final TranslateController.PollText pollText = TranslateController.PollText.fromPoll(mediaPoll);

            getTranslateController().addAsTranslatingItem(messageObject);
            getTranslateController().addAsManualTranslate(messageObject);
            messageCell.invalidate();

            Translator.translatePoll(resolvedTargetLocale, pollText, new Translator.Companion.TranslateCallBack3() {
                @Override
                public void onSuccess(@NonNull TranslateController.PollText poll) {
                    if (messageCell.getMessageObject() != messageObject) {
                        return;
                    }
                    getTranslateController().removeAsTranslatingItem(messageObject);

                    prepareMessageCellForSnapshot(messageCell);
                    final Bitmap snapshotBefore = shouldCaptureSnapshot(messageCell) ? captureCellSnapshot(messageCell) : null;

                    messageObject.messageOwner.translated = true;
                    messageObject.messageOwner.translatedToLanguage = TranslatorKt.getLocale2code(resolvedTargetLocale).toLowerCase(Locale.getDefault());
                    messageObject.messageOwner.translatedText = null;
                    messageObject.messageOwner.translatedPoll = poll;
                    messageObject.translated = true;

                    updateMessageCellAnimated(messageCell, messageObject, snapshotBefore);
                }

                @Override
                public void onFailed(boolean unsupported, @NonNull String message) {
                    if (messageCell.getMessageObject() != messageObject) {
                        return;
                    }
                    getTranslateController().removeAsTranslatingItem(messageObject);
                    getTranslateController().removeAsManualTranslate(messageObject);
                    messageCell.invalidate();
                    if (getParentActivity() != null) {
                        AlertUtil.showTransFailedDialog(getParentActivity(), unsupported, message, () -> toggleOrTranslate(messageCell, messageObject, resolvedTargetLocale));
                    }
                }
            });
            return;
        }

        int mode = NaConfig.INSTANCE.getTranslatorMode().Int();
        ArrayList<TLRPC.MessageEntity> entities = messageObject.messageOwner.entities;
        if (entities == null) {
            entities = new ArrayList<>();
        }

        getTranslateController().addAsTranslatingItem(messageObject);
        getTranslateController().addAsManualTranslate(messageObject);
        messageCell.invalidate();

        Translator.translate(resolvedTargetLocale, originalText, entities, new Translator.Companion.TranslateCallBack2() {
            @Override
            public void onSuccess(@NonNull TLRPC.TL_textWithEntities finalText) {
                if (messageCell.getMessageObject() != messageObject) {
                    return;
                }
                getTranslateController().removeAsTranslatingItem(messageObject);

                String translatedText = finalText.text;
                if (TextUtils.isEmpty(translatedText)) {
                    messageCell.invalidate();
                    return;
                }

                prepareMessageCellForSnapshot(messageCell);
                final Bitmap snapshotBefore = shouldCaptureSnapshot(messageCell) ? captureCellSnapshot(messageCell) : null;

                messageObject.messageOwner.translated = true;
                messageObject.messageOwner.translatedToLanguage = TranslatorKt.getLocale2code(resolvedTargetLocale).toLowerCase(Locale.getDefault());
                if (mode == 0) {
                    String finalMessageText = originalText + TRANSLATION_SEPARATOR + translatedText;
                    messageObject.messageOwner.translatedMessage = finalMessageText;
                    messageObject.messageOwner.translatedText = finalText;
                    messageObject.translated = false;
                    messageObject.applyNewText(finalMessageText);
                } else {
                    messageObject.messageOwner.translatedMessage = translatedText;
                    messageObject.messageOwner.translatedText = finalText;
                    messageObject.translated = true;
                    messageObject.applyNewText(translatedText);
                }
                messageObject.caption = null;
                messageObject.generateCaption();

                updateMessageCellAnimated(messageCell, messageObject, snapshotBefore);
            }

            @Override
            public void onFailed(boolean unsupported, @NonNull String message) {
                if (messageCell.getMessageObject() != messageObject) {
                    return;
                }
                getTranslateController().removeAsTranslatingItem(messageObject);
                getTranslateController().removeAsManualTranslate(messageObject);
                messageCell.invalidate();
                if (getParentActivity() != null) {
                    AlertUtil.showTransFailedDialog(getParentActivity(), unsupported, message, () -> toggleOrTranslate(messageCell, messageObject, resolvedTargetLocale));
                }
            }
        });
    }

    protected void updateVisibleChatMessageCells(@NonNull RecyclerView recyclerView) {
        if (fragmentView == null) {
            return;
        }
        int parentHeight = recyclerView.getMeasuredHeight();
        if (parentHeight <= 0) {
            return;
        }
        int parentWidth = fragmentView.getMeasuredWidth();
        if (parentWidth <= 0) {
            parentWidth = recyclerView.getMeasuredWidth();
        }
        int backgroundHeight = fragmentView.getMeasuredHeight();
        if (fragmentView instanceof SizeNotifierFrameLayout frameLayout) {
            backgroundHeight = frameLayout.getBackgroundSizeY();
        }

        float listY = recyclerView.getY();
        for (int i = 0, count = recyclerView.getChildCount(); i < count; i++) {
            View child = recyclerView.getChildAt(i);
            if (!(child instanceof ChatMessageCell cell)) {
                continue;
            }
            int top = (int) child.getY();
            int viewTop = Math.max(0, -top);
            int viewBottom = Math.min(child.getMeasuredHeight(), parentHeight - top);
            int visibleHeight = viewBottom - viewTop;
            if (visibleHeight <= 0) continue;
            cell.setParentBounds(0, parentHeight);
            cell.setVisiblePart(viewTop, visibleHeight, parentHeight, 0f, child.getY() + listY, parentWidth, backgroundHeight, 0, 0, 0);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        cancelAyuMessageAnimations();
        dimBehindView(false);
    }

    @Override
    public void onFragmentDestroy() {
        cancelAyuMessageAnimations();
        dimBehindView(false);
        super.onFragmentDestroy();
    }

    protected void dimBehindView(@Nullable View view, boolean enable) {
        if (enable) {
            setScrimView(view);
        }
        dimBehindView(enable ? DEFAULT_SCRIM_DIM_AMOUNT : 0f);
    }

    protected void dimBehindView(boolean enable) {
        dimBehindView(null, enable);
    }

    protected void dimBehindView(float value) {
        ensureScrimPaint();
        if (scrimPaint == null) {
            return;
        }

        if (scrimAnimator != null) {
            scrimAnimator.cancel();
            scrimAnimator = null;
        }

        final int startAlpha = scrimPaint.getAlpha();
        final int targetAlpha = Math.round(255f * value);
        if (startAlpha == targetAlpha) {
            if (targetAlpha == 0) {
                setScrimView(null);
            } else if (fragmentView != null) {
                fragmentView.invalidate();
            }
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(startAlpha, targetAlpha);
        animator.setDuration(targetAlpha > startAlpha ? 150 : 220);
        animator.addUpdateListener(a -> {
            if (scrimPaint != null) {
                scrimPaint.setAlpha((int) a.getAnimatedValue());
            }
            if (fragmentView != null) {
                fragmentView.invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (scrimAnimator == animation) {
                    scrimAnimator = null;
                }
                if (targetAlpha == 0) {
                    setScrimView(null);
                }
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEnd(animation);
            }
        });
        scrimAnimator = animator;
        animator.start();
    }

    private void ensureScrimPaint() {
        if (scrimPaint != null) {
            return;
        }
        scrimPaint = new Paint();
        scrimPaint.setAlpha(0);
    }

    private void setScrimView(@Nullable View view) {
        if (scrimView == view) {
            return;
        }
        if (scrimView instanceof ChatMessageCell cell) {
            cell.setInvalidatesParent(false);
        }
        scrimView = view;
        if (scrimView instanceof ChatMessageCell cell) {
            cell.setInvalidatesParent(true);
        }
        if (fragmentView != null) {
            fragmentView.invalidate();
        }
    }

    private void drawScrimOverlay(@NonNull ViewGroup container, @NonNull Canvas canvas) {
        if (scrimPaint == null || scrimPaint.getAlpha() <= 0) {
            return;
        }

        canvas.drawRect(0, 0, container.getWidth(), container.getHeight(), scrimPaint);

        if (scrimView == null || scrimView.getParent() == null) {
            return;
        }

        if (!scrimView.getGlobalVisibleRect(scrimTmpRect)) {
            return;
        }
        container.getLocationInWindow(scrimTmpLocation);
        scrimView.getLocationInWindow(scrimTmpLocation2);

        int viewLeft = scrimTmpLocation2[0] - scrimTmpLocation[0];
        int viewTop = scrimTmpLocation2[1] - scrimTmpLocation[1];

        scrimTmpRect.offset(-scrimTmpLocation[0], -scrimTmpLocation[1]);

        int save = canvas.save();
        canvas.translate(viewLeft, viewTop);
        canvas.clipRect(
            scrimTmpRect.left - viewLeft,
            scrimTmpRect.top - viewTop,
            scrimTmpRect.right - viewLeft,
            scrimTmpRect.bottom - viewTop
        );
        scrimView.draw(canvas);
        canvas.restoreToCount(save);
    }

    private HashMap<View, Float> captureChildY(@NonNull RecyclerView recyclerView) {
        HashMap<View, Float> map = new HashMap<>();
        for (int i = 0, count = recyclerView.getChildCount(); i < count; i++) {
            View child = recyclerView.getChildAt(i);
            map.put(child, child.getY());
        }
        return map;
    }

    @Nullable
    private Bitmap captureCellSnapshot(@NonNull ChatMessageCell cell) {
        int w = cell.getWidth();
        int h = cell.getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            cell.draw(canvas);
            return bitmap;
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean shouldCaptureSnapshot(@NonNull ChatMessageCell messageCell) {
        if (!MessagesController.getGlobalMainSettings().getBoolean("view_animations", true)) {
            return false;
        }
        return messageCell.getParent() instanceof RecyclerView;
    }

    private void prepareMessageCellForSnapshot(@NonNull ChatMessageCell messageCell) {
        RecyclerView recyclerView = messageCell.getParent() instanceof RecyclerView ? (RecyclerView) messageCell.getParent() : null;
        if (recyclerView != null) {
            cancelChildShiftAnimations(recyclerView);
            AnchorShiftPreDrawListener pending = pendingShiftListeners.remove(recyclerView);
            if (pending != null) {
                pending.dispose();
            }
            ValueAnimator runningOverlay = overlaySnapshotAnimators.remove(recyclerView);
            if (runningOverlay != null) {
                runningOverlay.cancel();
            }
            OwnedBitmapDrawable runningDrawable = overlayDrawables.remove(recyclerView);
            if (runningDrawable != null) {
                recyclerView.getOverlay().remove(runningDrawable);
                runningDrawable.dispose();
            }
            ValueAnimator runningVisiblePart = visiblePartAnimators.remove(recyclerView);
            if (runningVisiblePart != null) {
                runningVisiblePart.cancel();
            }
        }
        ValueAnimator runningChange = cellChangeAnimators.remove(messageCell);
        if (runningChange != null) {
            runningChange.cancel();
        }
        ValueAnimator runningClip = cellTopClipAnimators.remove(messageCell);
        if (runningClip != null) {
            runningClip.cancel();
        }
        messageCell.setClipBounds(null);
    }

    private static void cancelChildShiftAnimations(@NonNull RecyclerView recyclerView) {
        for (int i = 0, count = recyclerView.getChildCount(); i < count; i++) {
            View child = recyclerView.getChildAt(i);
            child.animate().cancel();
            child.setTranslationY(0f);
        }
    }

    private void updateMessageCellAnimated(@NonNull ChatMessageCell messageCell, @NonNull MessageObject messageObject, @Nullable Bitmap snapshotBefore) {
        final boolean animate = MessagesController.getGlobalMainSettings().getBoolean("view_animations", true);
        final RecyclerView recyclerView = messageCell.getParent() instanceof RecyclerView ? (RecyclerView) messageCell.getParent() : null;

        if (recyclerView != null) {
            cancelChildShiftAnimations(recyclerView);
            AnchorShiftPreDrawListener pending = pendingShiftListeners.remove(recyclerView);
            if (pending != null) {
                pending.dispose();
            }
        }

        ValueAnimator runningChange = cellChangeAnimators.remove(messageCell);
        if (runningChange != null) {
            runningChange.cancel();
        }
        ValueAnimator runningClip = cellTopClipAnimators.remove(messageCell);
        if (runningClip != null) {
            runningClip.cancel();
        }

        messageCell.setClipBounds(null);

        if (recyclerView != null) {
            ValueAnimator runningOverlay = overlaySnapshotAnimators.remove(recyclerView);
            if (runningOverlay != null) {
                runningOverlay.cancel();
            }
            OwnedBitmapDrawable runningDrawable = overlayDrawables.remove(recyclerView);
            if (runningDrawable != null) {
                recyclerView.getOverlay().remove(runningDrawable);
                runningDrawable.dispose();
            }
            ValueAnimator runningVisiblePart = visiblePartAnimators.remove(recyclerView);
            if (runningVisiblePart != null) {
                runningVisiblePart.cancel();
            }
        }

        final HashMap<View, Float> beforeY;
        final float anchorBottomBefore;
        final int anchorHeightBefore;
        final int anchorLeftBefore;
        final int anchorWidthBefore;
        if (animate && recyclerView != null) {
            beforeY = captureChildY(recyclerView);
            anchorBottomBefore = messageCell.getY() + messageCell.getHeight();
            anchorHeightBefore = messageCell.getHeight();
            anchorLeftBefore = Math.round(messageCell.getX());
            anchorWidthBefore = messageCell.getWidth();
        } else {
            beforeY = null;
            anchorBottomBefore = 0f;
            anchorHeightBefore = 0;
            anchorLeftBefore = 0;
            anchorWidthBefore = 0;
        }

        messageObject.forceUpdate = true;
        messageCell.setMessageObject(messageObject, messageCell.getCurrentMessagesGroup(), messageCell.isPinnedBottom(), messageCell.isPinnedTop(), messageCell.isFirstInChat(), messageCell.isLastInChatList());
        messageObject.forceUpdate = false;

        if (animate) {
            animateCellChange(messageCell);
        }
        if (animate && recyclerView != null) {
            animateRecyclerChildrenShift(recyclerView, messageCell, beforeY, anchorBottomBefore, anchorHeightBefore, anchorLeftBefore, anchorWidthBefore, snapshotBefore);
        } else {
            safeRecycle(snapshotBefore);
        }
    }

    private void cancelAyuMessageAnimations() {
        HashMap<RecyclerView, Boolean> recyclerViews = new HashMap<>();
        for (RecyclerView recyclerView : overlaySnapshotAnimators.keySet()) {
            recyclerViews.put(recyclerView, true);
        }
        for (RecyclerView recyclerView : visiblePartAnimators.keySet()) {
            recyclerViews.put(recyclerView, true);
        }
        for (RecyclerView recyclerView : overlayDrawables.keySet()) {
            recyclerViews.put(recyclerView, true);
        }
        for (RecyclerView recyclerView : pendingShiftListeners.keySet()) {
            recyclerViews.put(recyclerView, true);
        }
        for (RecyclerView recyclerView : recyclerViews.keySet()) {
            if (recyclerView != null) {
                cancelChildShiftAnimations(recyclerView);
            }
        }

        for (AnchorShiftPreDrawListener listener : new ArrayList<>(pendingShiftListeners.values())) {
            listener.dispose();
        }
        pendingShiftListeners.clear();

        for (ValueAnimator animator : new ArrayList<>(cellChangeAnimators.values())) {
            animator.cancel();
        }
        cellChangeAnimators.clear();

        for (ChatMessageCell cell : new ArrayList<>(cellTopClipAnimators.keySet())) {
            ValueAnimator animator = cellTopClipAnimators.get(cell);
            if (animator != null) {
                animator.cancel();
            }
            if (cell != null) {
                cell.setClipBounds(null);
            }
        }
        cellTopClipAnimators.clear();

        for (ValueAnimator animator : new ArrayList<>(overlaySnapshotAnimators.values())) {
            animator.cancel();
        }
        overlaySnapshotAnimators.clear();

        for (RecyclerView recyclerView : new ArrayList<>(overlayDrawables.keySet())) {
            OwnedBitmapDrawable drawable = overlayDrawables.get(recyclerView);
            if (drawable != null) {
                if (recyclerView != null) {
                    recyclerView.getOverlay().remove(drawable);
                }
                drawable.dispose();
            }
        }
        overlayDrawables.clear();

        for (ValueAnimator animator : new ArrayList<>(visiblePartAnimators.values())) {
            animator.cancel();
        }
        visiblePartAnimators.clear();
    }

    private void animateCellChange(@NonNull ChatMessageCell messageCell) {
        ChatMessageCell.TransitionParams params = messageCell.getTransitionParams();
        if (!params.supportChangeAnimation()) {
            return;
        }
        ValueAnimator running = cellChangeAnimators.remove(messageCell);
        if (running != null) {
            running.cancel();
        }

        if (!params.animateChange()) {
            return;
        }

        params.animateChange = true;
        params.animateChangeProgress = 0f;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ChatListItemAnimator.DEFAULT_DURATION);
        animator.setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            params.animateChangeProgress = (float) animation.getAnimatedValue();
            messageCell.invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (cellChangeAnimators.get(messageCell) == animator) {
                    cellChangeAnimators.remove(messageCell);
                }
                params.resetAnimation();
                messageCell.invalidate();
            }
        });
        cellChangeAnimators.put(messageCell, animator);
        animator.start();
    }

    private void animateRecyclerChildrenShift(
            @NonNull RecyclerView recyclerView,
            @NonNull ChatMessageCell anchorCell,
            @NonNull HashMap<View, Float> beforeY,
            float anchorBottomBefore,
            int anchorHeightBefore,
            int anchorLeftBefore,
            int anchorWidthBefore,
            @Nullable Bitmap snapshotBefore
    ) {
        AnchorShiftPreDrawListener pending = pendingShiftListeners.remove(recyclerView);
        if (pending != null) {
            pending.dispose();
        }

        ViewTreeObserver viewTreeObserver = recyclerView.getViewTreeObserver();
        if (!viewTreeObserver.isAlive()) {
            safeRecycle(snapshotBefore);
            return;
        }

        AnchorShiftPreDrawListener listener = new AnchorShiftPreDrawListener(recyclerView, anchorCell, beforeY, anchorBottomBefore, anchorHeightBefore, anchorLeftBefore, anchorWidthBefore, snapshotBefore);
        pendingShiftListeners.put(recyclerView, listener);
        viewTreeObserver.addOnPreDrawListener(listener);
    }

    private void animateAnchorTopChange(
            @NonNull RecyclerView recyclerView,
            @NonNull ChatMessageCell anchorCell,
            float anchorBottomBefore,
            int anchorHeightBefore,
            int anchorLeftBefore,
            int anchorWidthBefore,
            int anchorHeightAfter,
            int heightDelta,
            @Nullable Bitmap snapshotBefore
    ) {
        ValueAnimator runningClip = cellTopClipAnimators.remove(anchorCell);
        if (runningClip != null) {
            runningClip.cancel();
        }
        anchorCell.setClipBounds(null);

        if (heightDelta == 0) {
            safeRecycle(snapshotBefore);
            return;
        }

        if (heightDelta > 0) {
            int width = anchorCell.getWidth();
            if (width <= 0 || anchorHeightAfter <= 0) {
                anchorCell.setClipBounds(null);
                safeRecycle(snapshotBefore);
                return;
            }

            Rect clip = new Rect(0, heightDelta, width, anchorHeightAfter);
            anchorCell.setClipBounds(clip);

            safeRecycle(snapshotBefore);

            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(ChatListItemAnimator.DEFAULT_DURATION);
            animator.setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR);
            animator.addUpdateListener(animation -> {
                float p = (float) animation.getAnimatedValue();
                clip.top = Math.round(heightDelta * (1f - p));
                anchorCell.setClipBounds(clip);
                anchorCell.invalidate();
                recyclerView.invalidate();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (cellTopClipAnimators.get(anchorCell) == animator) {
                        cellTopClipAnimators.remove(anchorCell);
                    }
                    anchorCell.setClipBounds(null);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    onAnimationEnd(animation);
                }
            });
            cellTopClipAnimators.put(anchorCell, animator);
            animator.start();
            return;
        }

        if (snapshotBefore == null || snapshotBefore.isRecycled() || anchorWidthBefore <= 0 || anchorHeightBefore <= 0) {
            safeRecycle(snapshotBefore);
            return;
        }

        ValueAnimator runningOverlay = overlaySnapshotAnimators.remove(recyclerView);
        if (runningOverlay != null) {
            runningOverlay.cancel();
        }
        OwnedBitmapDrawable runningDrawable = overlayDrawables.remove(recyclerView);
        if (runningDrawable != null) {
            recyclerView.getOverlay().remove(runningDrawable);
            runningDrawable.dispose();
        }

        int collapse = -heightDelta;
        int top = Math.round(anchorBottomBefore - anchorHeightBefore);
        int right = anchorLeftBefore + anchorWidthBefore;
        int bottom = top + anchorHeightBefore;

        OwnedBitmapDrawable drawable = new OwnedBitmapDrawable(snapshotBefore);
        drawable.setBounds(anchorLeftBefore, top, right, bottom);
        recyclerView.getOverlay().add(drawable);
        overlayDrawables.put(recyclerView, drawable);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ChatListItemAnimator.DEFAULT_DURATION);
        animator.setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            float p = (float) animation.getAnimatedValue();
            drawable.setClipTop(Math.round(collapse * p));
            drawable.setAlpha(Math.round(255f * (1f - p)));
            recyclerView.invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                recyclerView.getOverlay().remove(drawable);
                if (overlayDrawables.get(recyclerView) == drawable) {
                    overlayDrawables.remove(recyclerView);
                }
                drawable.dispose();
                if (overlaySnapshotAnimators.get(recyclerView) == animator) {
                    overlaySnapshotAnimators.remove(recyclerView);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEnd(animation);
            }
        });
        overlaySnapshotAnimators.put(recyclerView, animator);
        animator.start();
    }

    private void startVisiblePartTick(@NonNull RecyclerView recyclerView) {
        ValueAnimator running = visiblePartAnimators.remove(recyclerView);
        if (running != null) {
            running.cancel();
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ChatListItemAnimator.DEFAULT_DURATION);
        animator.setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            updateVisibleChatMessageCells(recyclerView);
            recyclerView.invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (visiblePartAnimators.get(recyclerView) == animator) {
                    visiblePartAnimators.remove(recyclerView);
                }
                updateVisibleChatMessageCells(recyclerView);
            }
        });
        visiblePartAnimators.put(recyclerView, animator);
        animator.start();
    }

    private static void safeRecycle(@Nullable Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private static class OwnedBitmapDrawable extends Drawable {
        private final Paint paint;
        private final Rect srcRect;
        @Nullable
        private Bitmap bitmap;
        private int clipTop;

        private OwnedBitmapDrawable(@NonNull Bitmap bitmap) {
            this.bitmap = bitmap;
            paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            srcRect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        }

        public void dispose() {
            Bitmap b = bitmap;
            bitmap = null;
            if (b != null && !b.isRecycled()) {
                b.recycle();
            }
        }

        public void setClipTop(int clipTop) {
            this.clipTop = clipTop;
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }
            Rect bounds = getBounds();
            int save = canvas.save();
            canvas.clipRect(bounds.left, bounds.top + clipTop, bounds.right, bounds.bottom);
            canvas.drawBitmap(bitmap, srcRect, bounds, paint);
            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private final class AnchorShiftPreDrawListener implements ViewTreeObserver.OnPreDrawListener {

        private final RecyclerView recyclerView;
        private final ChatMessageCell anchorCell;
        private final HashMap<View, Float> beforeY;
        private final float anchorBottomBefore;
        private final int anchorHeightBefore;
        private final int anchorLeftBefore;
        private final int anchorWidthBefore;
        @Nullable
        private Bitmap snapshotBefore;

        private AnchorShiftPreDrawListener(
                @NonNull RecyclerView recyclerView,
                @NonNull ChatMessageCell anchorCell,
                @NonNull HashMap<View, Float> beforeY,
                float anchorBottomBefore,
                int anchorHeightBefore,
                int anchorLeftBefore,
                int anchorWidthBefore,
                @Nullable Bitmap snapshotBefore
        ) {
            this.recyclerView = recyclerView;
            this.anchorCell = anchorCell;
            this.beforeY = beforeY;
            this.anchorBottomBefore = anchorBottomBefore;
            this.anchorHeightBefore = anchorHeightBefore;
            this.anchorLeftBefore = anchorLeftBefore;
            this.anchorWidthBefore = anchorWidthBefore;
            this.snapshotBefore = snapshotBefore;
        }

        void dispose() {
            ViewTreeObserver observer = recyclerView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnPreDrawListener(this);
            }
            safeRecycle(snapshotBefore);
            snapshotBefore = null;
        }

        @Override
        public boolean onPreDraw() {
            ViewTreeObserver observer = recyclerView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnPreDrawListener(this);
            }
            if (pendingShiftListeners.get(recyclerView) == this) {
                pendingShiftListeners.remove(recyclerView);
            }

            Bitmap snapshot = snapshotBefore;
            snapshotBefore = null;

            if (anchorCell.getParent() != recyclerView) {
                safeRecycle(snapshot);
                return true;
            }

            float anchorBottomAfter = anchorCell.getY() + anchorCell.getHeight();
            int dyToFixBottom = Math.round(anchorBottomAfter - anchorBottomBefore);
            if (dyToFixBottom != 0) {
                recyclerView.scrollBy(0, dyToFixBottom);
            }

            final float anchorY = anchorCell.getY();
            final int anchorHeightAfter = anchorCell.getHeight();
            final int heightDelta = anchorHeightAfter - anchorHeightBefore;

            for (int i = 0, count = recyclerView.getChildCount(); i < count; i++) {
                View child = recyclerView.getChildAt(i);
                child.animate().cancel();

                if (child == anchorCell) {
                    child.setTranslationY(0f);
                    continue;
                }

                Float oldY = beforeY.get(child);
                if (oldY == null) {
                    child.setTranslationY(0f);
                    continue;
                }

                final boolean isAbove = child.getY() < anchorY;
                if (!isAbove) {
                    child.setTranslationY(0f);
                    continue;
                }

                float dy = oldY - child.getY();
                if (dy == 0f) {
                    child.setTranslationY(0f);
                    continue;
                }

                child.setTranslationY(dy);
                child.animate()
                        .translationY(0f)
                        .setDuration(ChatListItemAnimator.DEFAULT_DURATION)
                        .setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR)
                        .start();
            }

            animateAnchorTopChange(recyclerView, anchorCell, anchorBottomBefore, anchorHeightBefore, anchorLeftBefore, anchorWidthBefore, anchorHeightAfter, heightDelta, snapshot);
            updateVisibleChatMessageCells(recyclerView);
            startVisiblePartTick(recyclerView);
            return true;
        }
    }
}
