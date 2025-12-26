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
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import tw.nekomimi.nekogram.NekoConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import xyz.nextalone.nagram.NaConfig;

public class AyuSavePreferences {
    public static final String saveExclusionPrefix = "saveDeletedExclusion_";
    public static ConcurrentHashMap<Long, Boolean> saveDeletedExclusions = new ConcurrentHashMap<>();
    public static boolean isSaveDeletedExclusionsLoaded = false;
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

        if (getSaveDeletedExclusion(dialogId)) {
            return false;
        }

        if (userId != 0) {
            if (getSaveDeletedExclusion(userId)) {
                return false;
            }
            var fromUser = MessagesController.getInstance(accountId).getUser(userId);
            if (fromUser != null) {
                return !fromUser.bot || NaConfig.INSTANCE.getSaveDeletedMessageForBotUser().Bool();
            } else {
                final MessagesStorage messagesStorage = MessagesStorage.getInstance(accountId);
                final CountDownLatch countDownLatch = new CountDownLatch(1);
                final TLRPC.User[] user = {null};
                messagesStorage.getStorageQueue().postRunnable(() -> {
                    user[0] = messagesStorage.getUser(userId);
                    countDownLatch.countDown();
                });
                try {
                    countDownLatch.await();
                } catch (Exception ignored) {
                }
                if (user[0] != null) {
                    return !user[0].bot || NaConfig.INSTANCE.getSaveDeletedMessageForBotUser().Bool();
                }
            }
        }

        var user = MessagesController.getInstance(accountId).getUser(Math.abs(dialogId));
        if (user == null) {
            return true;
        }

        return !user.bot || NaConfig.INSTANCE.getSaveDeletedMessageForBot().Bool();
    }

    public static void setSaveDeletedExclusion(long chatId, boolean value) {
        saveDeletedExclusions.put(Math.abs(chatId), value);
        NekoConfig.getPreferences().edit().putBoolean(saveExclusionPrefix + Math.abs(chatId), value).apply();
    }

    public static boolean getSaveDeletedExclusion(long chatId) {
        if (isSaveDeletedExclusionsLoaded) {
            return Boolean.TRUE.equals(saveDeletedExclusions.getOrDefault(Math.abs(chatId), false));
        } else {
            return saveDeletedExclusions.computeIfAbsent(Math.abs(chatId), k -> NekoConfig.getPreferences().getBoolean(saveExclusionPrefix + Math.abs(chatId), false));
        }
    }

    public static void loadAllExclusions() {
        Utilities.stageQueue.postRunnable(() -> {
            Map<String, ?> allEntries = NekoConfig.getPreferences().getAll();
            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                if (entry.getKey().startsWith(saveExclusionPrefix)) {
                    try {
                        long chatId = Long.parseLong(entry.getKey().substring(saveExclusionPrefix.length()));
                        if (entry.getValue() instanceof Boolean) {
                            saveDeletedExclusions.put(chatId, (Boolean) entry.getValue());
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            isSaveDeletedExclusionsLoaded = true;
        });
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
