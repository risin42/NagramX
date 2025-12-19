package tw.nekomimi.nekogram.ui.cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.CodeHighlighting;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.URLSpanMono;

import java.util.Optional;

@SuppressLint("ViewConstructor")
public class NekoMessageCell extends ChatMessageCell {

    private final int touchSlop;
    private Runnable longPressRunnable;
    private float downX, downY;
    private boolean urlLongPressHandled;
    private boolean avatarPressedDown;
    @Nullable
    private NekoMessageCellDelegate ayuDelegate;

    public NekoMessageCell(Context context, int currentAccount) {
        super(context, currentAccount);

        isChat = true;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        setFullyDraw(true);
        setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
            @Override
            public boolean canPerformActions() {
                return true;
            }

            @Override
            public void didPressUrl(ChatMessageCell cell, CharacterStyle urlSpan, boolean longPress) {
                if (longPress) {
                    urlLongPressHandled = true;
                    if (longPressRunnable != null) {
                        NekoMessageCell.this.removeCallbacks(longPressRunnable);
                    }
                    if (urlSpan instanceof URLSpanMono) {
                        ((URLSpanMono) urlSpan).copyToClipboard();
                    } else if (urlSpan instanceof URLSpan) {
                        AndroidUtilities.addToClipboard(((URLSpan) urlSpan).getURL());
                    }
                    Optional.ofNullable(ayuDelegate).ifPresent(NekoMessageCellDelegate::onTextCopied);
                    return;
                }
                try {
                    if (urlSpan instanceof URLSpan) {
                        Browser.openUrl(cell.getContext(), ((URLSpan) urlSpan).getURL());
                    } else if (urlSpan instanceof ClickableSpan) {
                        ((ClickableSpan) urlSpan).onClick(cell);
                    }
                } catch (Exception ignored) {
                }
            }

            @Override
            public void didPressCodeCopy(ChatMessageCell cell, MessageObject.TextLayoutBlock block) {
                if (block == null || block.textLayout == null) {
                    return;
                } else {
                    block.textLayout.getText();
                }
                String string = block.textLayout.getText().toString();
                if (TextUtils.isEmpty(string)) {
                    return;
                }
                SpannableString code = new SpannableString(string);
                code.setSpan(new CodeHighlighting.Span(false, 0, null, block.language, string), 0, code.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                AndroidUtilities.addToClipboard(code);
                Optional.ofNullable(ayuDelegate).ifPresent(NekoMessageCellDelegate::onTextCopied);
            }

            @Override
            public void didPressImage(ChatMessageCell cell, float x, float y, boolean fullPreview) {
                Optional.ofNullable(ayuDelegate).ifPresent(d -> d.onImagePressed(cell));
            }

            @Override
            public void didPressUserAvatar(ChatMessageCell cell, TLRPC.User user, float touchX, float touchY, boolean asForward) {
                if (user != null) {
                    Optional.ofNullable(ayuDelegate).ifPresent(d -> d.onAvatarPressed(cell, user.id));
                }
            }

            @Override
            public void didPressInstantButton(ChatMessageCell cell, int type) {
                Optional.ofNullable(ayuDelegate).ifPresent(d -> d.didPressInstantButton(cell, type));
            }

            @Override
            public void didPressBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button) {
                Optional.ofNullable(ayuDelegate).ifPresent(d -> d.didPressBotButton(cell, button));
            }

            @Override
            public boolean didLongPressBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button) {
                if (ayuDelegate != null) {
                    return ayuDelegate.didLongPressBotButton(cell, button);
                }
                return false;
            }

            @Override
            public boolean needPlayMessage(ChatMessageCell cell, MessageObject messageObject, boolean muted) {
                if (ayuDelegate != null) {
                    return ayuDelegate.needPlayMessage(cell, messageObject, muted);
                }
                return false;
            }

            @Override
            public void didLongPress(ChatMessageCell cell, float x, float y) {
                boolean hasText = getMessageObject() != null && getMessageObject().messageOwner != null && !TextUtils.isEmpty(getMessageObject().messageOwner.message);
                if (hasText && isInMessageBubble(x, y) && !isInImageArea(x, y)) {
                    copyText();
                }
            }

            @Override
            public void forceUpdate(ChatMessageCell cell, boolean anchorScroll) {
                MessageObject msg = cell.getMessageObject();
                if (msg != null) {
                    msg.forceUpdate = true;
                    NekoMessageCell.this.setMessageObject(msg, null, NekoMessageCell.this.pinnedBottom, NekoMessageCell.this.pinnedTop, NekoMessageCell.this.firstInChat);
                    msg.forceUpdate = false;
                }
                NekoMessageCell.this.requestLayout();
                NekoMessageCell.this.invalidate();
                ViewParent p = NekoMessageCell.this.getParent();
                if (p instanceof RecyclerView) {
                    p.requestLayout();
                }
            }
        });
    }

    public void setAyuDelegate(@Nullable NekoMessageCellDelegate ayuDelegate) {
        this.ayuDelegate = ayuDelegate;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        ImageReceiver avatar = getAvatarImage();
        if (avatar != null) {
            avatar.setParentView(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (longPressRunnable != null) {
            removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    private void copyText() {
        String text = null;
        if (getMessageObject() != null && getMessageObject().messageOwner != null && !TextUtils.isEmpty(getMessageObject().messageOwner.message)) {
            text = getMessageObject().messageOwner.message;
        }
        if (TextUtils.isEmpty(text)) return;
        AndroidUtilities.addToClipboard(text);
        Optional.ofNullable(ayuDelegate).ifPresent(NekoMessageCellDelegate::onTextCopied);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                urlLongPressHandled = false;
                downX = event.getX();
                downY = event.getY();
                if (isInAvatarArea(downX, downY)) {
                    avatarPressedDown = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (avatarPressedDown) {
                    float x = event.getX();
                    float y = event.getY();
                    if (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop) {
                        avatarPressedDown = false;
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (avatarPressedDown) {
                    float upX = event.getX();
                    float upY = event.getY();
                    avatarPressedDown = false;
                    if (!urlLongPressHandled && Math.abs(upX - downX) < touchSlop && Math.abs(upY - downY) < touchSlop && isInAvatarArea(upX, upY)) {
                        MessageObject msg = getMessageObject();
                        if (msg != null && msg.messageOwner != null && msg.messageOwner.from_id != null) {
                            long uid = DialogObject.getPeerDialogId(msg.messageOwner.from_id);
                            Optional.ofNullable(ayuDelegate).ifPresent(d -> d.onAvatarPressed(this, uid));
                            return true;
                        }
                    }
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        MessageObject msg = getMessageObject();
        if (msg == null || msg.isOutOwner()) {
            return;
        }
        ImageReceiver avatar = getAvatarImage();
        if (avatar != null) {
            float wasX = avatar.getImageX();
            float wasY = avatar.getImageY();
            float wasW = avatar.getImageWidth();
            float wasH = avatar.getImageHeight();
            float wasA = avatar.getAlpha();
            boolean wasV = avatar.getVisible();

            int size = AndroidUtilities.dp(42);
            int x = AndroidUtilities.dp(6);
            int y = getBackgroundDrawableBottom() - size - AndroidUtilities.dp(3);
            avatar.setVisible(true, false);
            avatar.setImageCoords(x, y, size, size);
            avatar.setAlpha(1f);
            drawStatusWithImage(canvas, avatar, dp(7));
            avatar.setImageCoords(wasX, wasY, wasW, wasH);
            avatar.setAlpha(wasA);
            avatar.setVisible(wasV, false);
        }
    }

    private boolean isInAvatarArea(float x, float y) {
        MessageObject msg = getMessageObject();
        if (msg == null || msg.isOutOwner()) {
            return false;
        }
        int size = AndroidUtilities.dp(42);
        int left = AndroidUtilities.dp(6);
        int top = getBackgroundDrawableBottom() - size - AndroidUtilities.dp(3);
        return x >= left && x <= left + size && y >= top && y <= top + size;
    }

    private boolean isInImageArea(float x, float y) {
        ImageReceiver photo = getPhotoImage();
        if (photo == null || photo.getImageWidth() <= 0 || photo.getImageHeight() <= 0) {
            return false;
        }
        return photo.isInsideImage(x, y);
    }

    private boolean isInMessageBubble(float x, float y) {
        int left = getBackgroundDrawableLeft();
        int right = getBackgroundDrawableRight();
        int top = getBackgroundDrawableTop();
        int bottom = getBackgroundDrawableBottom();
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        MessageObject msg = getMessageObject();
        if (msg != null && msg.isVideo()) {
            String videoPath = msg.messageOwner.attachPath;
            if (TextUtils.isEmpty(videoPath) && msg.messageOwner.media != null && msg.messageOwner.media.document != null) {
                videoPath = msg.messageOwner.media.document.localPath;
            }
            if (!TextUtils.isEmpty(videoPath)) {
                getPhotoImage().setAllowStartAnimation(true);
                getPhotoImage().setImage(
                        ImageLocation.getForPath(videoPath),
                        ImageLoader.AUTOPLAY_FILTER,
                        null, null, null, null,
                        null, 0, null, msg, 0
                );
                getPhotoImage().startAnimation();
            }
        }
    }

    public interface NekoMessageCellDelegate {
        void onTextCopied();

        void onImagePressed(ChatMessageCell cell);

        void onAvatarPressed(ChatMessageCell cell, long userId);

        void didPressInstantButton(ChatMessageCell cell, int type);

        void didPressBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button);

        boolean didLongPressBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button);

        boolean needPlayMessage(ChatMessageCell cell, MessageObject messageObject, boolean muted);
    }
}