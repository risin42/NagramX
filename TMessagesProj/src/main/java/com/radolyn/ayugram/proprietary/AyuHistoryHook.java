package com.radolyn.ayugram.proprietary;

import android.text.TextUtils;
import android.util.SparseArray;
import androidx.collection.LongSparseArray;
import androidx.core.util.Pair;
import com.google.android.exoplayer2.util.Log;
import com.radolyn.ayugram.database.entities.DeletedMessageFull;
import com.radolyn.ayugram.database.entities.DeletedMessageReaction;
import com.radolyn.ayugram.messages.AyuMessagesController;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.MessageReplyHeader;

public abstract class AyuHistoryHook {
    private static final String NAX = "nu.gpu.nagram_AyuHistoryHook";
    public static void doHook(
        int currentAccount,
        ArrayList<MessageObject> messArr,
        SparseArray[] messagesDict,
        int startId,
        int endId,
        long dialogId,
        int limit,
        long topicId,
        boolean isSecretChat
    ) {
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doHook START");
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "messArr.size(): " + messArr.size());
        {
            if (BuildVars.LOGS_ENABLED) Log.d(NAX, "collection at start:");
            for (MessageObject msg : messArr) {
                if (BuildVars.LOGS_ENABLED) Log.d(NAX, "id: " + msg.getId() + " (date: " + msg.messageOwner.date + ")");
            }
        }
        Iterator<TLRPC.User> it;
        Iterator<TLRPC.Chat> it2;
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        long currentClientUserId = UserConfig.getInstance(currentAccount).clientUserId;
        AyuMessagesController ayuMessagesController = AyuMessagesController.getInstance();
        LongSparseArray longSparseArray = new LongSparseArray();
        ArrayList messageGroupsIds = new ArrayList();
        ArrayList replyToMessageIds = new ArrayList();
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "ayuMessagesController.getMessages: " + "currentClientUserId: " + currentClientUserId + ", " + "dialogId: " + dialogId + ", " + "topicId: " + topicId + ", " + "startId: " + startId + ", " + "endId: " + endId + ", " + "limit: " + limit);
        List<DeletedMessageFull> deletedMessages = ayuMessagesController.getMessages(currentClientUserId, dialogId, topicId, startId, endId, limit);
        if (deletedMessages.isEmpty()) {
            if (BuildVars.LOGS_ENABLED) Log.d(NAX, "deletedMessages.isEmpty() return");
            return;
        }
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "deletedMessages.size(): " + deletedMessages.size());
        ArrayList<Long> usersToLoad = new ArrayList();
        ArrayList<Long> chatsToLoad = new ArrayList();
        for (DeletedMessageFull deletedMessageFull : deletedMessages) {
            if (
                !TextUtils.isEmpty(deletedMessageFull.message.text) ||
                !TextUtils.isEmpty(deletedMessageFull.message.mediaPath) ||
                deletedMessageFull.message.documentSerialized != null
            ) {
                TLRPC.TL_message map = map(deletedMessageFull, currentAccount);
                long groupedMessagesId = map.grouped_id;
                if (groupedMessagesId != 0) {
                    if (BuildVars.LOGS_ENABLED) Log.d(NAX, "messageGroupsIds.add(" + Long.valueOf(groupedMessagesId) + ")");
                    messageGroupsIds.add(Long.valueOf(groupedMessagesId));
                }
                // TLRPC.TL_messageReplyHeader tLRPC$TL_messageReplyHeader = map.reply_to;
                // if (tLRPC$TL_messageReplyHeader != null) {
                //     if (BuildVars.LOGS_ENABLED) Log.d(NAX, "replyToMessageIds.add(" + Integer.valueOf(tLRPC$TL_messageReplyHeader.reply_to_msg_id) + ")");
                //     replyToMessageIds.add(Integer.valueOf(tLRPC$TL_messageReplyHeader.reply_to_msg_id));
                // }

                MessageReplyHeader replyTo = map.reply_to; // Use the abstract class
                // Check for specific types to access fields
                if (replyTo instanceof TLRPC.TL_messageReplyHeader) {
                    TLRPC.TL_messageReplyHeader replyHeader = (TLRPC.TL_messageReplyHeader) replyTo;
                    Integer replyToMsgId = replyHeader.reply_to_msg_id; // Access fields directly
                    if (replyToMsgId != null) {
                        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "replyToMessageIds.add(" + Integer.valueOf(replyToMsgId) + ")");
                        replyToMessageIds.add(Integer.valueOf(replyToMsgId));
                    }
                }

                if (BuildVars.LOGS_ENABLED) Log.d(NAX, "longSparseArray.put(" + map.id + ", map);");
                longSparseArray.put(map.id, map);
                MessagesStorage.addUsersAndChatsFromMessage(map, usersToLoad, chatsToLoad, null);
            }
        }
        Iterator it4 = messageGroupsIds.iterator();
        while (it4.hasNext()) {
            for (DeletedMessageFull deletedMessageFull2 : ayuMessagesController.getMessagesGrouped(currentClientUserId, dialogId, ((Long) it4.next()).longValue())) {
                if (!TextUtils.isEmpty(deletedMessageFull2.message.text) || !TextUtils.isEmpty(deletedMessageFull2.message.mediaPath) || deletedMessageFull2.message.documentSerialized != null) {
                    if (!longSparseArray.containsKey(deletedMessageFull2.message.messageId)) {
                        TLRPC.TL_message map2 = map(deletedMessageFull2, currentAccount);
                        MessageReplyHeader replyTo2 = map2.reply_to;
                        if (replyTo2 instanceof TLRPC.TL_messageReplyHeader) {
                            TLRPC.TL_messageReplyHeader replyHeader = (TLRPC.TL_messageReplyHeader) replyTo2;
                            Integer replyToMsgId = replyHeader.reply_to_msg_id; // Access fields directly
                            if (replyToMsgId != null) {
                                replyToMessageIds.add(Integer.valueOf(replyToMsgId));
                            }
                        }
                        // TLRPC.TL_messageReplyHeader tLRPC$TL_messageReplyHeader2 = map2.reply_to;
                        // if (tLRPC$TL_messageReplyHeader2 != null) {
                        //     replyToMessageIds.add(Integer.valueOf(tLRPC$TL_messageReplyHeader2.reply_to_msg_id));
                        // }
                        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "longSparseArray.put(" + map2.id + ", map2);");
                        longSparseArray.put(map2.id, map2);
                        MessagesStorage.addUsersAndChatsFromMessage(map2, usersToLoad, chatsToLoad, null);
                    }
                }
            }
        }
        Iterator it5 = replyToMessageIds.iterator();
        while (it5.hasNext()) {
            DeletedMessageFull message = ayuMessagesController.getMessage(currentClientUserId, dialogId, ((Integer) it5.next()).intValue());
            if (message != null && (!TextUtils.isEmpty(message.message.text) || !TextUtils.isEmpty(message.message.mediaPath) || message.message.documentSerialized != null)) {
                if (!longSparseArray.containsKey(message.message.messageId)) {
                    TLRPC.TL_message map3 = map(message, currentAccount);
                    if (BuildVars.LOGS_ENABLED) Log.d(NAX, "longSparseArray.put(" + map3.id + ", map3);");
                    longSparseArray.put(map3.id, map3);
                    MessagesStorage.addUsersAndChatsFromMessage(map3, usersToLoad, chatsToLoad, null);
                }
            }
        }
        ArrayList<TLRPC.User> someUsersFrom_usersAndChatsFromDeletedMessages = new ArrayList<>();
        ArrayList<TLRPC.Chat> deletedChats = new ArrayList<>();
        try {
            if (!usersToLoad.isEmpty()) {
                // messagesStorage.getUsersInternal(TextUtils.join(",", usersToLoad), someUsersFrom_usersAndChatsFromDeletedMessages);
                if (BuildVars.LOGS_ENABLED) Log.d(NAX, "messagesStorage.getUsersInternal(" + TextUtils.join(",", usersToLoad) + ", someUsersFrom_usersAndChatsFromDeletedMessages);");
                messagesStorage.getUsersInternal(usersToLoad, someUsersFrom_usersAndChatsFromDeletedMessages);
            }
        } catch (Exception e2) {
            Log.e(NAX, String.valueOf(e2));
        }
        try {
            if (!chatsToLoad.isEmpty()) {
                if (BuildVars.LOGS_ENABLED) Log.d(NAX, "messagesStorage.getChatsInternal(" + TextUtils.join(",", chatsToLoad) + ", deletedChats);");
                messagesStorage.getChatsInternal(TextUtils.join(",", chatsToLoad), deletedChats);
            }
        } catch (Exception e3) {
            Log.e(NAX, String.valueOf(e3));
        }
        LongSparseArray newMessageObjectUsers = new LongSparseArray();
        LongSparseArray newMessageObjectChats = new LongSparseArray();
        it = someUsersFrom_usersAndChatsFromDeletedMessages.iterator();
        while (it.hasNext()) {
            TLRPC.User next = it.next();
            newMessageObjectUsers.put(next.id, next);
            if (BuildVars.LOGS_ENABLED) Log.d(NAX, "newMessageObjectUsers.put(" + next.id + ", next);");
        }
        it2 = deletedChats.iterator();
        while (it2.hasNext()) {
            TLRPC.Chat next2 = it2.next();
            newMessageObjectChats.put(next2.id, next2);
            if (BuildVars.LOGS_ENABLED) Log.d(NAX, "newMessageObjectChats.put(" + next2.id + ", next2);");
        }
        Comparator comparator2 = new Comparator() { // from class: com.radolyn.ayugram.proprietary.AyuHistoryHook$$ExternalSyntheticLambda0
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                int lambda$doHook$0;
                lambda$doHook$0 = AyuHistoryHook.doHook_compareMessages((MessageObject) obj, (MessageObject) obj2);
                return lambda$doHook$0;
            }
        };
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "before i6 messArr.size(): " + messArr.size());
        // if (messArr.size() > 1) { why?
            // Iterator it6 = messArr.iterator();
            // int i7 = ConnectionsManager.DEFAULT_DATACENTER_ID;
            // int i8 = Integer.MIN_VALUE;
            // while (it6.hasNext()) {
            //     MessageObject messageObject = (MessageObject) it6.next();
            //     if (BuildVars.LOGS_ENABLED) Log.d(NAX, "it6.next() => " + messageObject.getId());
            //     if (!messageObject.isSending() && i8 == Integer.MIN_VALUE) {
            //         if (BuildVars.LOGS_ENABLED) Log.d(NAX, "i8 = " + messageObject.getId() + " [[messageObject.getId()]];");
            //         i8 = messageObject.getId();
            //     } else if (!messageObject.isSending()) {
            //         if (BuildVars.LOGS_ENABLED) Log.d(NAX, "i7 = " + messageObject.getId() + " [[messageObject.getId()]];");
            //         i7 = messageObject.getId();
            //     }
            // }
            // if (BuildVars.LOGS_ENABLED) Log.d(NAX, "if (" + i8 + " [[i8]] > " + i7 + " [[i7]])");
            // if (i8 > i7) {
            //     comparator2 = comparator2.reversed();
                if (isSecretChat) {
                    // TODO: not sure if it's needed
                    // but .reversed() method doesn't
                    // affect comparator2 :thinking:
                    comparator2 = comparator2.reversed();
                }
                if (BuildVars.LOGS_ENABLED) Log.d(NAX, "longSparseArray.size(): " + longSparseArray.size());
                for (int i6 = 0; i6 < longSparseArray.size(); i6++) {
                    if (BuildVars.LOGS_ENABLED) Log.d(NAX, "i6 = " + i6 + ";messArr.add(...)");
                    messArr.add(
                        new MessageObject(
                            currentAccount,
                            (TLRPC.Message) longSparseArray.get(longSparseArray.keyAt(i6)),
                            newMessageObjectUsers,
                            newMessageObjectChats,
                            false,
                            true
                        )
                    );
                }
                if (BuildVars.LOGS_ENABLED) Log.d(NAX, "after i6 messArr.size(): " + messArr.size());
                Iterator<MessageObject> it3 = messArr.iterator();
                while (it3.hasNext()) {
                    MessageObject messageObject2 = it3.next();

                    MessageReplyHeader replyTo3 = messageObject2.messageOwner.reply_to;
                    if (replyTo3 instanceof TLRPC.TL_messageReplyHeader) {
                        TLRPC.TL_messageReplyHeader replyHeader = (TLRPC.TL_messageReplyHeader) replyTo3;
                        Integer replyToMsgId = replyHeader.reply_to_msg_id; // Access fields directly
                        MessageObject messageObject3 = null;
                        if (replyToMsgId != null) {
                            messageObject3 = (MessageObject) messagesDict[0].get(replyToMsgId);
                        }
                        if (messageObject3 == null) {
                            messageObject3 = (MessageObject) messagesDict[1].get(replyToMsgId);
                        }
                        if (messageObject3 == null) {
                            Iterator<MessageObject> it7 = messArr.iterator();
                            while (true) {
                                if (!it7.hasNext()) {
                                    break;
                                }
                                MessageObject messageObject4 = it7.next();
                                if (messageObject2.messageOwner.reply_to != null && messageObject4.getId() == replyToMsgId) {
                                    messageObject3 = messageObject4;
                                    break;
                                }
                            }
                        }
                        if (messageObject3 != null) {
                            messageObject2.messageOwner.replyMessage = messageObject3.messageOwner;
                            messageObject2.replyMessageObject = messageObject3;
                        }
                    }

                    // TLRPC.TL_messageReplyHeader tLRPC$TL_messageReplyHeader3 = messageObject2.messageOwner.reply_to;
                    // if (tLRPC$TL_messageReplyHeader3 != null) {
                    //     MessageObject messageObject3 = (MessageObject) messagesDict[0].get(tLRPC$TL_messageReplyHeader3.reply_to_msg_id);
                    //     if (messageObject3 == null) {
                    //         messageObject3 = (MessageObject) messagesDict[1].get(messageObject2.messageOwner.reply_to.reply_to_msg_id);
                    //     }
                    //     if (messageObject3 == null) {
                    //         Iterator<MessageObject> it7 = messArr.iterator();
                    //         while (true) {
                    //             if (!it7.hasNext()) {
                    //                 break;
                    //             }
                    //             MessageObject messageObject4 = it7.next();
                    //             if (messageObject2.messageOwner.reply_to != null && messageObject4.getId() == messageObject2.messageOwner.reply_to.reply_to_msg_id) {
                    //                 messageObject3 = messageObject4;
                    //                 break;
                    //             }
                    //         }
                    //     }
                    //     if (messageObject3 != null) {
                    //         messageObject2.messageOwner.replyMessage = messageObject3.messageOwner;
                    //         messageObject2.replyMessageObject = messageObject3;
                    //     }
                    // }
                }
                // {
                //     if (BuildVars.LOGS_ENABLED) Log.d(NAX, "before collection sort:");
                //     for (MessageObject msg : messArr) {
                //         if (BuildVars.LOGS_ENABLED) Log.d(NAX, "id: " + msg.getId() + " (date: " + msg.messageOwner.date + ")");
                //     }
                // }
                Collections.sort(messArr, comparator2);
                {
                    if (BuildVars.LOGS_ENABLED) Log.d(NAX, "after collection sort:");
                    for (MessageObject msg : messArr) {
                        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "id: " + msg.getId() + " (date: " + msg.messageOwner.date + ")");
                    }
                }
            // }
        // }
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doHook END");
    }

    public static Pair<Integer, Integer> getMinAndMaxIds(ArrayList arrayList) {
        Iterator it = arrayList.iterator();
        int minId = ConnectionsManager.DEFAULT_DATACENTER_ID;
        int maxId = Integer.MIN_VALUE;
        while (it.hasNext()) {
            MessageObject messageObject = (MessageObject) it.next();
            if (!messageObject.isSending()) {
                int id = messageObject.getId();
                if (id < minId) {
                    minId = id;
                }
                if (id > maxId) {
                    maxId = id;
                }
            }
        }
        return new Pair(minId, maxId);
    }

    private static int doHook_compareMessages(MessageObject a, MessageObject b) {
        int id = a.getId();
        int id2 = b.getId();
        int i = a.messageOwner.date;
        int i2 = b.messageOwner.date;
        if (id > 0 && id2 > 0) {
            if (id > id2) {
                return -1;
            }
            return id < id2 ? 1 : 0;
        } else if (id >= 0 || id2 >= 0) {
            if (i > i2) {
                return -1;
            }
            return i < i2 ? 1 : 0;
        } else if (id < id2) {
            return -1;
        } else {
            return id > id2 ? 1 : 0;
        }
    }

    private static TLRPC.TL_message map(DeletedMessageFull deletedMessageFull, int i) {
        TLRPC.Reaction reaction;
        TLRPC.TL_message tLRPC$TL_message = new TLRPC.TL_message();
        AyuMessageUtils.map(deletedMessageFull.message, tLRPC$TL_message, i);
        List list = deletedMessageFull.reactions;
        if (list != null && !list.isEmpty()) {
            tLRPC$TL_message.reactions = new TLRPC.TL_messageReactions();
            int i2 = 0;
            for (DeletedMessageReaction deletedMessageReaction : deletedMessageFull.reactions) {
                TLRPC.TL_reactionCount tLRPC$TL_reactionCount = new TLRPC.TL_reactionCount();
                tLRPC$TL_reactionCount.count = deletedMessageReaction.count;
                tLRPC$TL_reactionCount.chosen = deletedMessageReaction.selfSelected;
                i2++;
                tLRPC$TL_reactionCount.chosen_order = i2;
                if (deletedMessageReaction.isCustom) {
                    var customEmoji = new TLRPC.TL_reactionCustomEmoji();
                    customEmoji.document_id = deletedMessageReaction.documentId;
                    reaction = customEmoji;
                } else {
                    var emoji = new TLRPC.TL_reactionEmoji();
                    emoji.emoticon = deletedMessageReaction.emoticon;
                    reaction = emoji;
                }
                tLRPC$TL_reactionCount.reaction = reaction;
                tLRPC$TL_message.reactions.results.add(tLRPC$TL_reactionCount);
            }
        }
        tLRPC$TL_message.ayuDeleted = true;
        AyuMessageUtils.mapMedia(deletedMessageFull.message, tLRPC$TL_message);
        return tLRPC$TL_message;
    }
}
