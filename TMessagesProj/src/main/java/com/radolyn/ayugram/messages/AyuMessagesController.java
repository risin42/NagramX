/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram.messages;

import android.os.Environment;
import android.text.TextUtils;
import com.google.android.exoplayer2.util.Log;
import com.radolyn.ayugram.AyuConstants;
import com.radolyn.ayugram.database.AyuData;
import com.radolyn.ayugram.database.dao.DeletedMessageDao;
import com.radolyn.ayugram.database.dao.EditedMessageDao;
import com.radolyn.ayugram.database.entities.DeletedMessage;
import com.radolyn.ayugram.database.entities.DeletedMessageFull;
import com.radolyn.ayugram.database.entities.DeletedMessageReaction;
import com.radolyn.ayugram.database.entities.EditedMessage;
import com.radolyn.ayugram.proprietary.AyuMessageUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AyuMessagesController {
    private static final String NAX = "AyuMessagesController";

    public static final String attachmentsSubfolder = "Saved Attachments";
    public static final File attachmentsPath = new File(
            new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), AyuConstants.APP_NAME),
            attachmentsSubfolder
    );
    private static AyuMessagesController instance;
    private final EditedMessageDao editedMessageDao;
    private final DeletedMessageDao deletedMessageDao;

    private AyuMessagesController() {
        initializeAttachmentsFolder();

        editedMessageDao = AyuData.getEditedMessageDao();
        deletedMessageDao = AyuData.getDeletedMessageDao();
    }

    private static void initializeAttachmentsFolder() {
        if (!attachmentsPath.exists()) {
            attachmentsPath.mkdirs();
            try {
                new File(attachmentsPath, ".nomedia").createNewFile();
            } catch (IOException e) {
                // ignored, I hate java
            }
        }
    }

    public static AyuMessagesController getInstance() {
        if (instance == null) {
            instance = new AyuMessagesController();
        }
        return instance;
    }

    public void onMessageEdited(AyuSavePreferences prefs, TLRPC.Message newMessage) {
        try {
            onMessageEditedInner(prefs, newMessage, false);
        } catch (Exception e) {
            Log.e(NAX, "error onMessageEdited", e);
            FileLog.e("onMessageEdited", e);
        }
    }

    public void onMessageEditedForce(AyuSavePreferences prefs) {
        try {
            onMessageEditedInner(prefs, prefs.getMessage(), true);
        } catch (Exception e) {
            Log.e(NAX, "error onMessageEditedForce", e);
            FileLog.e("onMessageEditedForce", e);
        }
    }

    private void onMessageEditedInner(AyuSavePreferences prefs, TLRPC.Message newMessage, boolean force) {
        // if (!AyuConfig.saveEditedMessageFor(prefs.getAccountId(), prefs.getDialogId())) {
        //     return;
        // }

        var oldMessage = prefs.getMessage();

        boolean sameMedia = oldMessage.media == newMessage.media ||
                (oldMessage.media != null && newMessage.media != null && oldMessage.media.getClass() == newMessage.media.getClass());
        if (oldMessage.media instanceof TLRPC.TL_messageMediaPhoto && newMessage.media instanceof TLRPC.TL_messageMediaPhoto && oldMessage.media.photo != null && newMessage.media.photo != null) {
            sameMedia = oldMessage.media.photo.id == newMessage.media.photo.id;
        } else if (oldMessage.media instanceof TLRPC.TL_messageMediaDocument && newMessage.media instanceof TLRPC.TL_messageMediaDocument && oldMessage.media.document != null && newMessage.media.document != null) {
            sameMedia = oldMessage.media.document.id == newMessage.media.document.id;
        }

        if (force) {
            sameMedia = false;
        }

        if (sameMedia && TextUtils.equals(oldMessage.message, newMessage.message)) {
            return;
        }

        var revision = new EditedMessage();
        AyuMessageUtils.map(prefs, revision);
        AyuMessageUtils.mapMedia(prefs, revision, !sameMedia);

        if (!sameMedia && !TextUtils.isEmpty(revision.mediaPath)) {
            var lastRevision = editedMessageDao.getLastRevision(prefs.getUserId(), prefs.getDialogId(), prefs.getMessageId());

            if (lastRevision != null && !TextUtils.equals(revision.mediaPath, lastRevision.mediaPath) && lastRevision.mediaPath != null && !lastRevision.mediaPath.contains(attachmentsSubfolder)) {
                // update previous revisions to reflect media change
                // like, there's no previous file, so replace it with one we copied before...
                editedMessageDao.updateAttachmentForRevisionsBetweenDates(prefs.getUserId(), prefs.getDialogId(), prefs.getMessageId(), lastRevision.mediaPath, revision.mediaPath);
            }
        }

        editedMessageDao.insert(revision);

        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getInstance(prefs.getAccountId()).postNotificationName(AyuConstants.MESSAGE_EDITED_NOTIFICATION, prefs.getDialogId(), prefs.getMessageId());
        });
    }

    public void onMessageDeleted(AyuSavePreferences prefs) {
        if (prefs.getMessage() == null) {
            if (BuildVars.LOGS_ENABLED) Log.d(NAX, "null msg ?");
            return;
        }

        try {
            onMessageDeletedInner(prefs);
        } catch (Exception e) {
            Log.e(NAX, "error onMessageDeleted", e);
            FileLog.e("onMessageDeleted", e);
        }
    }

    private void onMessageDeletedInner(AyuSavePreferences prefs) {
         if (!AyuSavePreferences.saveDeletedMessageFor(prefs.getAccountId(), prefs.getDialogId())) {
             return;
         }

        if (deletedMessageDao.exists(prefs.getUserId(), prefs.getDialogId(), prefs.getTopicId(), prefs.getMessageId())) {
            return;
        }

        var deletedMessage = new DeletedMessage();
        deletedMessage.userId = prefs.getUserId();
        deletedMessage.dialogId = prefs.getDialogId();
        deletedMessage.messageId = prefs.getMessageId();
        deletedMessage.entityCreateDate = prefs.getRequestCatchTime();

        var msg = prefs.getMessage();

        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "saving message " + prefs.getMessageId() + " for " + prefs.getDialogId() + " with topic " + prefs.getTopicId());

        AyuMessageUtils.map(prefs, deletedMessage);
        AyuMessageUtils.mapMedia(prefs, deletedMessage, true);

        var fakeMsgId = deletedMessageDao.insert(deletedMessage);

        // if (msg != null && msg.reactions != null && AyuConfig.saveReactions) {
        if (msg != null && msg.reactions != null) {
            processDeletedReactions(fakeMsgId, msg.reactions);
        }
    }

    private void processDeletedReactions(long fakeMessageId, TLRPC.TL_messageReactions reactions) {
        for (var reaction : reactions.results) {
            if (reaction.reaction instanceof TLRPC.TL_reactionEmpty) {
                continue;
            }

            var deletedReaction = new DeletedMessageReaction();
            deletedReaction.deletedMessageId = fakeMessageId;
            deletedReaction.count = reaction.count;
            deletedReaction.selfSelected = reaction.chosen;

            if (reaction.reaction instanceof TLRPC.TL_reactionEmoji) {
                deletedReaction.emoticon = ((TLRPC.TL_reactionEmoji) reaction.reaction).emoticon;
            } else if (reaction.reaction instanceof TLRPC.TL_reactionCustomEmoji) {
                deletedReaction.documentId = ((TLRPC.TL_reactionCustomEmoji) reaction.reaction).document_id;
                deletedReaction.isCustom = true;
            } else {
                if (BuildVars.LOGS_ENABLED) Log.d(NAX, "fake news emoji");
                continue;
            }

            deletedMessageDao.insertReaction(deletedReaction);
        }
    }

    public boolean hasAnyRevisions(long userId, long dialogId, int messageId) {
        return editedMessageDao.hasAnyRevisions(userId, dialogId, messageId);
    }

    public List<EditedMessage> getRevisions(long userId, long dialogId, int messageId) {
        return editedMessageDao.getAllRevisions(userId, dialogId, messageId);
    }

    public DeletedMessageFull getMessage(long userId, long dialogId, int messageId) {
        return deletedMessageDao.getMessage(userId, dialogId, messageId);
    }

    public List<DeletedMessageFull> getMessages(long userId, long dialogId, long topicId, long startId, long endId, int limit) {
        return deletedMessageDao.getMessages(userId, dialogId, topicId, startId, endId, limit);
    }

    public List<DeletedMessageFull> getThreadMessages(long userId, long dialogId, long threadMessageId, long startId, long endId, int limit) {
        return deletedMessageDao.getThreadMessages(userId, dialogId, threadMessageId, startId, endId, limit);
    }

    public List<DeletedMessageFull> getMessagesGrouped(long userId, long dialogId, long groupedId) {
        return deletedMessageDao.getMessagesGrouped(userId, dialogId, groupedId);
    }

    public void delete(long userId, long dialogId, int messageId) {
        var msg = getMessage(userId, dialogId, messageId);
        if (msg == null) {
            return;
        }

        deletedMessageDao.delete(userId, dialogId, messageId);

        if (!TextUtils.isEmpty(msg.message.mediaPath)) {
            var p = new File(msg.message.mediaPath);
            if (p.exists()) {
                try {
                    p.delete();
                } catch (Exception e) {
                    Log.e(NAX, "failed to delete file " + msg.message.mediaPath, e);
                }
            }
        }
    }
 
    public void deleteCurrent(long dialogId, long mergeDialogId, Runnable callback) {
        List<DeletedMessageFull> messages = deletedMessageDao.getMessagesByDialog(dialogId);
        ArrayList<Integer> messageIds = new ArrayList<>();
        for (DeletedMessageFull message : messages) {
            messageIds.add(message.message.messageId);
        }
        
        if (mergeDialogId != 0) {
            List<DeletedMessageFull> mergeMessages = deletedMessageDao.getMessagesByDialog(mergeDialogId);
            messages.addAll(mergeMessages);
            for (DeletedMessageFull message : mergeMessages) {
                messageIds.add(message.message.messageId);
            }
        }

        // Delete messages and their edit history from database
        deletedMessageDao.delete(dialogId);
        editedMessageDao.deleteByDialogIdAndMessageIds(dialogId, messageIds);
        
        if (mergeDialogId != 0) {
            deletedMessageDao.delete(mergeDialogId);
            editedMessageDao.deleteByDialogIdAndMessageIds(mergeDialogId, messageIds);
        }

        // Clean up media files
        for (DeletedMessageFull msg : messages) {
            if (msg.message.mediaPath != null && !msg.message.mediaPath.isEmpty()) {
                File mediaFile = new File(msg.message.mediaPath);
                if (mediaFile.exists()) {
                    try {
                        mediaFile.delete();
                    } catch (Exception e) {
                        Log.e(NAX, "Failed to delete media file: " + msg.message.mediaPath, e);
                    }
                }
            }
        }

        if (callback != null) {
            callback.run();
        }
    }

    public void clean() {
        AyuData.clean();
        AyuData.create();

        // force to recreate a database to avoid crash
        instance = null;
    }
}
