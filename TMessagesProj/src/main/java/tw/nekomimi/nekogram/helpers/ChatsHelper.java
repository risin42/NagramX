/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 * <p>
 * Copyright github.com/arsLan4k1390, 2022-2025.
 */

package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BaseController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.LaunchActivity;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;

import xyz.nextalone.nagram.NaConfig;

public class ChatsHelper extends BaseController {
    public static final int LEFT_BUTTON_NOQUOTE = 0;
    public static final int LEFT_BUTTON_REPLY = 1;
    public static final int LEFT_BUTTON_SAVE_MESSAGE = 2;
    public static final int LEFT_BUTTON_DIRECT_SHARE = 3;
    private static final ChatsHelper[] Instance = new ChatsHelper[UserConfig.MAX_ACCOUNT_COUNT];
    public ChatActivity.ThemeDelegate themeDelegate;

    public ChatsHelper(int num) {
        super(num);
    }

    public static ChatsHelper getInstance(int num) {
        ChatsHelper localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (ChatsHelper.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new ChatsHelper(num);
                }
            }
        }
        return localInstance;
    }

    public static String getLeftButtonText(boolean noForwards) {
        if (noForwards) {
            return getString(R.string.Reply);
        }
        return switch (NaConfig.INSTANCE.getLeftBottomButton().Int()) {
            case LEFT_BUTTON_REPLY -> getString(R.string.Reply);
            case LEFT_BUTTON_SAVE_MESSAGE -> getString(R.string.AddToSavedMessages);
            case LEFT_BUTTON_DIRECT_SHARE -> getString(R.string.DirectShare);
            default -> getString(R.string.NoQuoteForward);
        };
    }

    public static int getLeftButtonDrawable(boolean noForwards) {
        if (noForwards) {
            return R.drawable.input_reply;
        }
        return switch (NaConfig.INSTANCE.getLeftBottomButton().Int()) {
            case LEFT_BUTTON_SAVE_MESSAGE -> R.drawable.msg_saved;
            case LEFT_BUTTON_DIRECT_SHARE -> R.drawable.msg_share;
            default -> R.drawable.input_reply;
        };
    }

    private void createReplyAction(ChatActivity chatActivity) {
        MessageObject messageObject = null;
        for (int a = 1; a >= 0; a--) {
            if (messageObject == null && chatActivity.selectedMessagesIds[a].size() != 0) {
                messageObject = chatActivity.messagesDict[a].get(chatActivity.selectedMessagesIds[a].keyAt(0));
            }
            chatActivity.selectedMessagesIds[a].clear();
            chatActivity.selectedMessagesCanCopyIds[a].clear();
            chatActivity.selectedMessagesCanStarIds[a].clear();
        }
        chatActivity.hideActionMode();
        if (messageObject != null && (messageObject.messageOwner.id > 0 || messageObject.messageOwner.id < 0 && chatActivity.getCurrentEncryptedChat() != null)) {
            chatActivity.showFieldPanelForReply(messageObject);
        }
        chatActivity.updatePinnedMessageView(true);
        chatActivity.updateVisibleRows();
        chatActivity.updateSelectedMessageReactions();
    }

    public void makeReplyButtonClick(ChatActivity chatActivity, boolean noForwards) {
        if (noForwards) {
            createReplyAction(chatActivity);
        }
        switch (NaConfig.INSTANCE.getLeftBottomButton().Int()) {
            case LEFT_BUTTON_REPLY:
                createReplyAction(chatActivity);
                break;
            case LEFT_BUTTON_SAVE_MESSAGE:
                createSaveMessagesSelected(chatActivity);
                break;
            case LEFT_BUTTON_DIRECT_SHARE:
                createShareAlertSelected(chatActivity);
                break;
            case LEFT_BUTTON_NOQUOTE:
            default:
                ChatActivity.noForwardQuote = true;
                if (chatActivity.messagePreviewParams != null) {
                    chatActivity.messagePreviewParams.setHideForwardSendersName(true);
                }
                chatActivity.openForward(false);
                break;
        }
    }

    public void makeReplyButtonLongClick(ChatActivity chatActivity, boolean noForwards, Theme.ResourcesProvider resourcesProvider) {
        ArrayList<String> configStringKeys = new ArrayList<>();
        ArrayList<Integer> configValues = new ArrayList<>();

        configStringKeys.add(getString(R.string.NoQuoteForward));
        configValues.add(LEFT_BUTTON_NOQUOTE);

        configStringKeys.add(getString(R.string.Reply));
        configValues.add(LEFT_BUTTON_REPLY);

        configStringKeys.add(getString(R.string.AddToSavedMessages));
        configValues.add(LEFT_BUTTON_SAVE_MESSAGE);

        configStringKeys.add(getString(R.string.DirectShare));
        configValues.add(LEFT_BUTTON_DIRECT_SHARE);

        PopupHelper.show(configStringKeys, getString(R.string.LeftBottomButtonAction), configValues.indexOf(NaConfig.INSTANCE.getLeftBottomButton().Int()), chatActivity.getContext(), i -> {
            NaConfig.INSTANCE.getLeftBottomButton().setConfigInt(configValues.get(i));

            if (chatActivity.replyButton == null) return;

            if (chatActivity.bottomMessagesActionContainer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                LaunchActivity.makeRipple(chatActivity.bottomMessagesActionContainer.getLeft(), chatActivity.bottomMessagesActionContainer.getBottom(), 5);
            }

            chatActivity.replyButton.setText(getLeftButtonText(noForwards));

            @SuppressLint("UseCompatLoadingForDrawables") Drawable image = chatActivity.getContext().getResources().getDrawable(getLeftButtonDrawable(noForwards)).mutate();
            image.setColorFilter(new PorterDuffColorFilter(chatActivity.getThemedColor(Theme.key_actionBarActionModeDefaultIcon), PorterDuff.Mode.MULTIPLY));
            chatActivity.replyButton.setCompoundDrawablesWithIntrinsicBounds(image, null, null, null);
        }, resourcesProvider);
    }

    private ArrayList<MessageObject> getSelectedMessages(ChatActivity chatActivity) {
        ArrayList<MessageObject> fmessages = new ArrayList<>();

        for (int a = 1; a >= 0; a--) {
            ArrayList<Integer> ids = new ArrayList<>();
            for (int b = 0; b < chatActivity.selectedMessagesIds[a].size(); b++) {
                ids.add(chatActivity.selectedMessagesIds[a].keyAt(b));
            }
            Collections.sort(ids);
            for (int b = 0; b < ids.size(); b++) {
                Integer id = ids.get(b);
                MessageObject messageObject = chatActivity.selectedMessagesIds[a].get(id);
                if (messageObject != null) {
                    fmessages.add(messageObject);
                }
            }
            chatActivity.selectedMessagesCanCopyIds[a].clear();
            chatActivity.selectedMessagesCanStarIds[a].clear();
            chatActivity.selectedMessagesIds[a].clear();
        }

        chatActivity.hideActionMode();
        chatActivity.updatePinnedMessageView(true);
        chatActivity.updateVisibleRows();

        return fmessages;
    }

    public void forwardMessages(ChatActivity chatActivity, ArrayList<MessageObject> arrayList, boolean fromMyName, boolean notify, int scheduleDate, long did) {
        if (arrayList == null || arrayList.isEmpty()) {
            return;
        }
        if ((scheduleDate != 0) == (chatActivity.getChatMode() == ChatActivity.MODE_SCHEDULED)) {
            chatActivity.waitingForSendingMessageLoad = true;
        }
        AlertsCreator.showSendMediaAlert(getSendMessagesHelper().sendMessage(arrayList, did == 0 ? chatActivity.getDialogId() : did, fromMyName, false, notify, scheduleDate, 0), chatActivity, chatActivity.getResourceProvider());
    }

    private void createShareAlertSelected(ChatActivity chatActivity) {
        if (chatActivity.forwardingMessage == null && chatActivity.selectedMessagesIds[0].size() == 0 && chatActivity.selectedMessagesIds[1].size() == 0) {
            return;
        }
        ArrayList<MessageObject> fmessages = new ArrayList<>();
        if (chatActivity.forwardingMessage != null) {
            if (chatActivity.forwardingMessageGroup != null) {
                fmessages.addAll(chatActivity.forwardingMessageGroup.messages);
            } else {
                fmessages.add(chatActivity.forwardingMessage);
            }
            chatActivity.forwardingMessage = null;
            chatActivity.forwardingMessageGroup = null;
        } else {
            for (int a = 1; a >= 0; a--) {
                ArrayList<Integer> ids = new ArrayList<>();
                for (int b = 0; b < chatActivity.selectedMessagesIds[a].size(); b++) {
                    ids.add(chatActivity.selectedMessagesIds[a].keyAt(b));
                }
                Collections.sort(ids);
                for (int b = 0; b < ids.size(); b++) {
                    MessageObject messageObject = chatActivity.selectedMessagesIds[a].get(ids.get(b));
                    if (messageObject != null) {
                        fmessages.add(messageObject);
                    }
                }
                chatActivity.selectedMessagesCanCopyIds[a].clear();
                chatActivity.selectedMessagesCanStarIds[a].clear();
                chatActivity.selectedMessagesIds[a].clear();
            }
        }
        chatActivity.hideActionMode();
        chatActivity.updatePinnedMessageView(true);
        chatActivity.updateVisibleRows();

        chatActivity.showDialog(new ShareAlert(chatActivity.getContext(), chatActivity, fmessages, null, null, ChatObject.isChannel(chatActivity.getCurrentChat()), null, null, false, false, false, null, themeDelegate) {
            @Override
            public void dismissInternal() {
                super.dismissInternal();
                AndroidUtilities.requestAdjustResize(chatActivity.getParentActivity(), chatActivity.getClassGuid());
                if (chatActivity.getChatActivityEnterView().getVisibility() == View.VISIBLE) {
                    chatActivity.fragmentView.requestLayout();
                }
            }

            @Override
            protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {
                chatActivity.createUndoView();
                if (chatActivity.getUndoView() == null || !showToast) {
                    return;
                }
                if (dids.size() == 1) {
                    chatActivity.getUndoView().showWithAction(dids.valueAt(0).id, UndoView.ACTION_FWD_MESSAGES, count, topic, null, null);
                } else {
                    chatActivity.getUndoView().showWithAction(0, UndoView.ACTION_FWD_MESSAGES, count, dids.size(), null, null);
                }
            }
        });
        AndroidUtilities.setAdjustResizeToNothing(chatActivity.getParentActivity(), chatActivity.getClassGuid());
        chatActivity.fragmentView.requestLayout();
    }

    private void createSaveMessagesSelected(ChatActivity chatActivity) {
        try {
            long chatID = getUserConfig().getClientUserId();

            ArrayList<MessageObject> messages = getSelectedMessages(chatActivity);
            forwardMessages(chatActivity, messages, false, true, 0, chatID);
            chatActivity.createUndoView();
            if (chatActivity.getUndoView() == null) {
                return;
            }
            if (!BulletinFactory.of(chatActivity).showForwardedBulletinWithTag(chatID, messages.size())) {
                chatActivity.getUndoView().showWithAction(chatID, UndoView.ACTION_FWD_MESSAGES, messages.size());
            }
        } catch (Exception ignore) {
            chatActivity.clearSelectionMode();
            Toast.makeText(chatActivity.getParentActivity(), getString(R.string.ErrorOccurred), Toast.LENGTH_SHORT).show();
        }
    }

    public static long getChatId() {
        long chatId = -1;
        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        if (lastFragment instanceof ChatActivity) {
            TLRPC.Chat chat = ((ChatActivity) lastFragment).getCurrentChat();
            TLRPC.User user = ((ChatActivity) lastFragment).getCurrentUser();
            if (chat != null) {
                chatId = chat.id;
            } else if (user != null) {
                chatId = user.id;
            }
        }
        return chatId;
    }

    public static String getChatFolderName(MessageObject message) {
        String chatName = "Unknown";

        if (message == null) {
            return chatName;
        }

        long peerId = MessageObject.getPeerId(message.messageOwner.peer_id);
        int currentAccount = message.currentAccount;

        if (DialogObject.isUserDialog(peerId)) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
            if (user != null) {
                chatName = UserObject.getUserName(user);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-peerId);
            if (chat != null) {
                chatName = chat.title;
            }
        }

        // Normalize Unicode to avoid issues with combined characters
        chatName = Normalizer.normalize(chatName, Normalizer.Form.NFKC);

        // Remove all invisible characters (U+200B - U+206F)
        chatName = chatName.replaceAll("[\\u200B-\\u206F]", "");

        // Replace invalid file system characters
        chatName = chatName.replaceAll("[\\p{Cc}\\p{Cf}\\\\/:*?\"<>|]", "_");

        // Trim spaces and remove leading/trailing dots (Windows does not allow filenames ending with '.')
        chatName = chatName.trim().replaceAll("^\\.+|\\.+$", "");

        // If the cleaned name is empty, use the peer ID instead
        if (TextUtils.isEmpty(chatName)) {
            chatName = String.valueOf(peerId);
        }

        return chatName;
    }
}
