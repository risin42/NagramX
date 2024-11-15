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
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

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
}
