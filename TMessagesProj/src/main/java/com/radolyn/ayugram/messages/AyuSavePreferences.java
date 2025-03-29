/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram.messages;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import xyz.nextalone.nagram.NaConfig;

public class AyuSavePreferences {
    private final TLRPC.Message message;
    private final int accountId;
    private final long userId;
    private long dialogId = -1;
    private long topicId = -1;
    private int messageId = -1;
    private int requestCatchTime = -1;

    public AyuSavePreferences(TLRPC.Message msg, int accountId, long dialogId, long topicId, int messageId, int requestCatchTime) {
        this.message = msg;
        this.accountId = accountId;
        this.userId = UserConfig.getInstance(accountId).getClientUserId();

        if (msg == null) {
            return;
        }

        this.dialogId = dialogId;
        this.topicId = topicId;
        this.messageId = messageId;
        this.requestCatchTime = requestCatchTime;
    }

    public AyuSavePreferences(TLRPC.Message msg, int accountId) {
        this.message = msg;
        this.accountId = accountId;
        this.userId = UserConfig.getInstance(accountId).getClientUserId();

        if (msg == null) {
            return;
        }

        this.dialogId = msg.dialog_id;
        this.topicId = MessageObject.getTopicId(accountId, msg, false);
        this.messageId = msg.id;
        this.requestCatchTime = (int) (System.currentTimeMillis() / 1000);
    }

    public static boolean saveDeletedMessageFor(int accountId, long dialogId) {
        return saveDeletedMessageFor(accountId, dialogId, null);
    }

    public static boolean saveDeletedMessageFor(int accountId, long dialogId, MessageObject messageObject) {
        if (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.from_id != null) {
            return saveDeletedMessageFor(accountId, dialogId, messageObject.messageOwner.from_id.user_id);
        }
        return saveDeletedMessageFor(accountId, dialogId, 0);
    }

    public static boolean saveDeletedMessageFor(int accountId, long dialogId, long userId) {
        if (!NaConfig.INSTANCE.getEnableSaveDeletedMessages().Bool()) {
            return false;
        }

        if (userId != 0) {
            var fromUser = MessagesController.getInstance(accountId).getUser(userId);
            if (fromUser != null && fromUser.bot && !NaConfig.INSTANCE.getSaveDeletedMessageForBotUser().Bool()) {
                return false;
            }
        }

        var user = MessagesController.getInstance(accountId).getUser(Math.abs(dialogId));
        if (user == null) {
            return true;
        }

        return !user.bot || NaConfig.INSTANCE.getSaveDeletedMessageForBot().Bool();
    }

    public TLRPC.Message getMessage() {
        return message;
    }

    public int getAccountId() {
        return accountId;
    }

    public long getUserId() {
        return userId;
    }

    public long getDialogId() {
        return dialogId;
    }

    public void setDialogId(long dialogId) {
        if (dialogId == 0) {
            return;
        }

        this.dialogId = dialogId;
    }

    public long getTopicId() {
        return topicId;
    }

    public int getMessageId() {
        return messageId;
    }

    public int getRequestCatchTime() {
        return requestCatchTime;
    }

    public long getFromUserId() {
        if (message == null || message.from_id == null) {
            return 0;
        }
        return message.from_id.user_id;
    }
}
