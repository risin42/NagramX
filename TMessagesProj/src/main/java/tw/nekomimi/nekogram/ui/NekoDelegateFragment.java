package tw.nekomimi.nekogram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.ContactAddActivity;
import org.telegram.ui.ProfileActivity;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.MessageHelper;
import tw.nekomimi.nekogram.ui.cells.NekoMessageCell;
import tw.nekomimi.nekogram.utils.AndroidUtil;

public abstract class NekoDelegateFragment extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, NekoMessageCell.NekoMessageCellDelegate {

    @Override
    public void onTextCopied() {
        BulletinFactory.of(this).createCopyBulletin(getString(R.string.MessageCopied)).show();
    }

    @Override
    public void onImagePressed(ChatMessageCell cell) {
        if (cell.getMessageObject() != null) {
            MessageObject messageObject = cell.getMessageObject();
            if (messageObject.isSticker()) {
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
                if (!NekoConfig.disableVibration.Bool())
                    cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
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

}