package com.radolyn.ayugram.proprietary;

import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseArray;

import androidx.collection.LongSparseArray;

import com.radolyn.ayugram.database.entities.DeletedMessageFull;
import com.radolyn.ayugram.database.entities.DeletedMessageReaction;
import com.radolyn.ayugram.messages.AyuMessagesController;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.MessageReplyHeader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class AyuHistoryHook {

    public static void doHookAsync(int currentAccount, long startId, long endId, long dialogId, int limit, long topicId, int loadType, boolean isChannelComment, long threadMessageId, boolean isTopic) {
        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            long clientUserId = UserConfig.getInstance(currentAccount).clientUserId;
            AyuMessagesController ayuController = AyuMessagesController.getInstance();
            LongSparseArray<TLRPC.TL_message> messagesById = new LongSparseArray<>();
            Set<Long> groupIds = new HashSet<>();
            Set<Integer> replyIds = new HashSet<>();
            List<DeletedMessageFull> deletedMessages;

            if (isChannelComment) {
                deletedMessages = ayuController.getThreadMessages(clientUserId, dialogId, threadMessageId, startId, endId, limit);
            } else if (isTopic && topicId != 0) {
                deletedMessages = ayuController.getTopicMessages(clientUserId, dialogId, topicId, startId, endId, limit);
            } else {
                deletedMessages = ayuController.getMessages(clientUserId, dialogId, startId, endId, limit);
            }

            if (deletedMessages.isEmpty()) {
                return;
            }

            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            for (DeletedMessageFull deletedMessageFull : deletedMessages) {
                if (hasContent(deletedMessageFull)) {
                    TLRPC.TL_message mappedMessage = map(deletedMessageFull, currentAccount);
                    long groupId = mappedMessage.grouped_id;
                    if (groupId != 0) {
                        groupIds.add(groupId);
                    }
                    MessageReplyHeader replyHeader = mappedMessage.reply_to;
                    if (replyHeader != null) {
                        replyIds.add(replyHeader.reply_to_msg_id);
                    }

                    messagesById.put(mappedMessage.id, mappedMessage);
                    MessagesStorage.addUsersAndChatsFromMessage(mappedMessage, usersToLoad, chatsToLoad, null);
                }
            }

            ArrayList<Long> uniqueGroupIds = new ArrayList<>(groupIds);
            for (DeletedMessageFull groupedMessageFull : ayuController.getMessagesGroupedIn(clientUserId, dialogId, uniqueGroupIds)) {
                if (hasContent(groupedMessageFull)) {
                    if (!messagesById.containsKey(groupedMessageFull.message.messageId)) {
                        TLRPC.TL_message mappedMessage = map(groupedMessageFull, currentAccount);
                        MessageReplyHeader replyHeader = mappedMessage.reply_to;
                        if (replyHeader != null) {
                            replyIds.add(replyHeader.reply_to_msg_id);
                        }
                        messagesById.put(mappedMessage.id, mappedMessage);
                        MessagesStorage.addUsersAndChatsFromMessage(mappedMessage, usersToLoad, chatsToLoad, null);
                    }
                }
            }

            ArrayList<Integer> uniqueReplyIds = new ArrayList<>(replyIds);
            for (DeletedMessageFull replyMessageFull : ayuController.getMessagesByIds(clientUserId, dialogId, uniqueReplyIds)) {
                if (hasContent(replyMessageFull)) {
                    if (!messagesById.containsKey(replyMessageFull.message.messageId)) {
                        TLRPC.TL_message mappedMessage = map(replyMessageFull, currentAccount);
                        messagesById.put(mappedMessage.id, mappedMessage);
                        MessagesStorage.addUsersAndChatsFromMessage(mappedMessage, usersToLoad, chatsToLoad, null);
                    }
                }
            }

            ArrayList<TLRPC.User> fetchedUsers = new ArrayList<>();
            ArrayList<TLRPC.Chat> fetchedChats = new ArrayList<>();
            try {
                if (!usersToLoad.isEmpty()) {
                    storage.getUsersInternal(usersToLoad, fetchedUsers);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                if (!chatsToLoad.isEmpty()) {
                    storage.getChatsInternal(TextUtils.join(",", chatsToLoad), fetchedChats);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            LongSparseArray<TLRPC.User> usersById = new LongSparseArray<>();
            LongSparseArray<TLRPC.Chat> chatsById = new LongSparseArray<>();
            for (TLRPC.User user : fetchedUsers) {
                usersById.put(user.id, user);
            }
            for (TLRPC.Chat chat : fetchedChats) {
                chatsById.put(chat.id, chat);
            }

            Comparator<MessageObject> messageComparator = AyuHistoryHook::doHook_compareMessages;
            if (loadType == 1) {
                messageComparator = messageComparator.reversed();
            }

            final ArrayList<MessageObject> messageObjects = new ArrayList<>();
            for (int i = 0; i < messagesById.size(); i++) {
                messageObjects.add(new MessageObject(currentAccount, messagesById.get(messagesById.keyAt(i)), usersById, chatsById, false, true));
            }

            SparseArray<MessageObject> indexById = new SparseArray<>(messageObjects.size());
            for (MessageObject messageObj : messageObjects) {
                indexById.put(messageObj.getId(), messageObj);
            }
            for (int idx = 0; idx < messageObjects.size(); idx++) {
                MessageObject messageObj = messageObjects.get(idx);
                messageObj.setIsRead();
                messageObj.setContentIsRead();
                if (messageObj.messageOwner.reply_to != null) {
                    int replyId = messageObj.messageOwner.reply_to.reply_to_msg_id;
                    MessageObject candidate = indexById.get(replyId);
                    if (candidate != null) {
                        messageObj.messageOwner.replyMessage = candidate.messageOwner;
                        messageObj.replyMessageObject = candidate;
                    }
                }
            }

            messageObjects.sort(messageComparator);

            AndroidUtilities.runOnUIThread(() -> {
                // MessagesController.getInstance(currentAccount).updateInterfaceWithMessages(dialogId, messageObjects, 0);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didReceiveNewMessages, dialogId, messageObjects, false, 0);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.replyMessagesDidLoad, dialogId, messageObjects, null);
            });
        });
    }

    public static Pair<Integer, Integer> getMinAndMaxIds(ArrayList<MessageObject> messages) {
        int minId = ConnectionsManager.DEFAULT_DATACENTER_ID;
        int maxId = Integer.MIN_VALUE;
        for (MessageObject messageObj : messages) {
            if (!messageObj.isSending()) {
                int messageId = messageObj.getId();
                if (messageId < minId) {
                    minId = messageId;
                }
                if (messageId > maxId) {
                    maxId = messageId;
                }
            }
        }
        return new Pair<>(minId, maxId);
    }

    private static int doHook_compareMessages(MessageObject a, MessageObject b) {
        int aId = a.getId();
        int bId = b.getId();
        int aDate = a.messageOwner.date;
        int bDate = b.messageOwner.date;
        if (aId > 0 && bId > 0) {
            return Integer.compare(bId, aId);
        } else if (aId >= 0 || bId >= 0) {
            return Integer.compare(bDate, aDate);
        } else {
            if (aDate != bDate) {
                return Integer.compare(bDate, aDate);
            } else {
                return Integer.compare(aId, bId);
            }
        }
    }

    private static TLRPC.TL_message map(DeletedMessageFull deletedMessageFull, int accountId) {
        TLRPC.Reaction reaction;
        TLRPC.TL_message tlMessage = new TLRPC.TL_message();
        AyuMessageUtils.map(deletedMessageFull.message, tlMessage, accountId);
        List<DeletedMessageReaction> reactionsList = deletedMessageFull.reactions;
        if (reactionsList != null && !reactionsList.isEmpty()) {
            tlMessage.reactions = new TLRPC.TL_messageReactions();
            int orderIndex = 0;
            for (DeletedMessageReaction deletedMessageReaction : deletedMessageFull.reactions) {
                TLRPC.TL_reactionCount reactionCount = new TLRPC.TL_reactionCount();
                reactionCount.count = deletedMessageReaction.count;
                reactionCount.chosen = deletedMessageReaction.selfSelected;
                orderIndex++;
                reactionCount.chosen_order = orderIndex;
                if (deletedMessageReaction.isCustom) {
                    var customEmoji = new TLRPC.TL_reactionCustomEmoji();
                    customEmoji.document_id = deletedMessageReaction.documentId;
                    reaction = customEmoji;
                } else {
                    var emoji = new TLRPC.TL_reactionEmoji();
                    emoji.emoticon = deletedMessageReaction.emoticon;
                    reaction = emoji;
                }
                reactionCount.reaction = reaction;
                tlMessage.reactions.results.add(reactionCount);
            }
        }
        tlMessage.ayuDeleted = true;
        AyuMessageUtils.mapMedia(deletedMessageFull.message, tlMessage);
        return tlMessage;
    }

    private static boolean hasContent(DeletedMessageFull messageFull) {
        return messageFull != null && messageFull.message != null && (!TextUtils.isEmpty(messageFull.message.text) || !TextUtils.isEmpty(messageFull.message.mediaPath) || messageFull.message.documentSerialized != null);
    }
}
