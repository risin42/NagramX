/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.radolyn.ayugram.database.entities.EditedMessage;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Cells.ChatMessageCell;

import java.util.Optional;

public class AyuMessageCell extends ChatMessageCell {

    private final int touchSlop;
    private EditedMessage editedMessage;
    private Runnable longPressRunnable;
    private boolean longPressScheduled;
    private float downX, downY;
    private boolean urlLongPressHandled;
    @Nullable
    private AyuMessageCellDelegate ayuDelegate;

    public AyuMessageCell(Context context, int currentAccount) {
        super(context, currentAccount);

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        setFullyDraw(true);
        isChat = false;
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
                        AyuMessageCell.this.removeCallbacks(longPressRunnable);
                        longPressScheduled = false;
                    }
                    AndroidUtilities.addToClipboard(((URLSpan) urlSpan).getURL());
                    Optional.ofNullable(ayuDelegate).ifPresent(AyuMessageCellDelegate::onTextCopied);
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
            public void didPressImage(ChatMessageCell cell, float x, float y, boolean fullPreview) {
                Optional.ofNullable(ayuDelegate).ifPresent(d -> d.onImagePressed(cell));
            }

            @Override
            public void didLongPress(ChatMessageCell cell, float x, float y) {
                if (longPressScheduled && longPressRunnable != null) {
                    AyuMessageCell.this.removeCallbacks(longPressRunnable);
                    longPressScheduled = false;
                }
                copyText();
            }

            @Override
            public void didPressOther(ChatMessageCell cell, float otherX, float otherY) {
                if (editedMessage == null || TextUtils.isEmpty(editedMessage.mediaPath)) {
                    copyText();
                }
            }

            @Override
            public void forceUpdate(ChatMessageCell cell, boolean anchorScroll) {
                org.telegram.messenger.MessageObject mo = cell.getMessageObject();
                if (mo != null) {
                    mo.forceUpdate = true;
                    AyuMessageCell.this.setMessageObject(mo, null, AyuMessageCell.this.pinnedBottom, AyuMessageCell.this.pinnedTop, AyuMessageCell.this.firstInChat);
                    mo.forceUpdate = false;
                }
                AyuMessageCell.this.requestLayout();
                AyuMessageCell.this.invalidate();
                ViewParent p = AyuMessageCell.this.getParent();
                if (p instanceof RecyclerView) {
                    p.requestLayout();
                }
            }
        });
    }

    public void setEditedMessage(EditedMessage editedMessage) {
        this.editedMessage = editedMessage;
    }

    public void setAyuDelegate(@Nullable AyuMessageCellDelegate ayuDelegate) {
        this.ayuDelegate = ayuDelegate;
    }

    private void copyText() {
        if (editedMessage == null || TextUtils.isEmpty(editedMessage.text)) {
            return;
        }
        AndroidUtilities.addToClipboard(editedMessage.text);
        Optional.ofNullable(ayuDelegate).ifPresent(AyuMessageCellDelegate::onTextCopied);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (editedMessage == null) {
            return super.onTouchEvent(event);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                urlLongPressHandled = false;
                downX = event.getX();
                downY = event.getY();
                if (!TextUtils.isEmpty(editedMessage.text) && !isInImageArea(event) && isInMessageBubble(event)) {
                    if (longPressRunnable == null) {
                        longPressRunnable = () -> {
                            if (!urlLongPressHandled) {
                                copyText();
                            }
                        };
                    }
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                    longPressScheduled = true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (longPressScheduled) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        removeCallbacks(longPressRunnable);
                        longPressScheduled = false;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (longPressScheduled) {
                    removeCallbacks(longPressRunnable);
                    longPressScheduled = false;
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    private boolean isInImageArea(MotionEvent event) {
        if (getPhotoImage() == null || getPhotoImage().getImageWidth() <= 0 || getPhotoImage().getImageHeight() <= 0) {
            return false;
        }
        float ix = getPhotoImage().getImageX();
        float iy = getPhotoImage().getImageY();
        float iw = getPhotoImage().getImageWidth();
        float ih = getPhotoImage().getImageHeight();
        float x = event.getX();
        float y = event.getY();
        return x >= ix && x <= ix + iw && y >= iy && y <= iy + ih;
    }

    private boolean isInMessageBubble(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int left = getBackgroundDrawableLeft();
        int right = getBackgroundDrawableRight();
        int top = getBackgroundDrawableTop();
        int bottom = getBackgroundDrawableBottom();
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (editedMessage != null && !TextUtils.isEmpty(editedMessage.hqThumbPath)) {
            getPhotoImage().setImage(editedMessage.hqThumbPath, null, null, null, 0);
        }
    }

    public interface AyuMessageCellDelegate {
        void onTextCopied();

        void onImagePressed(ChatMessageCell cell);
    }
}
