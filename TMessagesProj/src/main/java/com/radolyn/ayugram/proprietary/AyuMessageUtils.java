package com.radolyn.ayugram.proprietary;

import android.text.TextUtils;
import androidx.core.util.Pair;
import com.google.android.exoplayer2.util.Log;
// import com.radolyn.ayugram.AyuConfig;
import com.radolyn.ayugram.AyuUtils;
import com.radolyn.ayugram.database.entities.AyuMessageBase;
import com.radolyn.ayugram.messages.AyuMessagesController;
import com.radolyn.ayugram.messages.AyuSavePreferences;
import com.radolyn.ayugram.utils.AyuFileLocation;
import java.util.Collection;
import java.util.function.Function;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.secretmedia.EncryptedFileInputStream;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.MessageReplyHeader;
import org.telegram.tgnet.TLRPC.TL_messageReplyHeader;
import xyz.nextalone.nagram.NaConfig;

public abstract class AyuMessageUtils {
    private static final String NAX = "AyuMessageUtils";

    public static ArrayList deserializeMultiple(byte[] bArr, Function<NativeByteBuffer, TLObject> function) {
        if (bArr == null || bArr.length == 0) {
            return new ArrayList();
        }
        try {
            NativeByteBuffer nativeByteBuffer = new NativeByteBuffer(bArr.length);
            nativeByteBuffer.buffer.put(bArr);
            nativeByteBuffer.rewind();
            ArrayList arrayList = new ArrayList();
            while (nativeByteBuffer.buffer.position() < nativeByteBuffer.buffer.limit()) {
                TLObject tLObject = (TLObject) function.apply(nativeByteBuffer);
                if (tLObject != null) {
                    arrayList.add(tLObject);
                }
            }
            return arrayList;
        } catch (Exception unused) {
            Log.e(NAX, "Failed to allocate buffer");
            return new ArrayList();
        }
    }

    public static void map(AyuMessageBase ayuMessageBase, TLRPC.Message tLRPC$Message, int i) {
        MessagesController messagesController = MessagesController.getInstance(i);
        int i2 = ayuMessageBase.flags;
        tLRPC$Message.dialog_id = ayuMessageBase.dialogId;
        tLRPC$Message.grouped_id = ayuMessageBase.groupedId;
        tLRPC$Message.peer_id = messagesController.getPeer(ayuMessageBase.peerId);
        tLRPC$Message.from_id = messagesController.getPeer(ayuMessageBase.fromId);
        int i3 = ayuMessageBase.messageId;
        tLRPC$Message.id = i3;
        tLRPC$Message.realId = i3;
        tLRPC$Message.date = ayuMessageBase.date;
        int i4 = ayuMessageBase.flags;
        tLRPC$Message.flags = i4;
        tLRPC$Message.unread = (i2 & 1) != 0;
        tLRPC$Message.out = (i2 & 2) != 0;
        tLRPC$Message.mentioned = (i2 & 16) != 0;
        tLRPC$Message.media_unread = (i2 & 32) != 0;
        tLRPC$Message.silent = (i2 & LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS_NOT_PREMIUM) != 0;
        tLRPC$Message.post = (i2 & 16384) != 0;
        tLRPC$Message.from_scheduled = (262144 & i2) != 0;
        tLRPC$Message.legacy = (524288 & i2) != 0;
        tLRPC$Message.edit_hide = (2097152 & i2) != 0;
        tLRPC$Message.pinned = (16777216 & i2) != 0;
        tLRPC$Message.noforwards = false;
        // tLRPC$Message.ayuNoforwards = (67108864 & i2) != 0;
        // tLRPC$Message.topic_start = (i2 & 134217728) != 0;
        tLRPC$Message.edit_date = ayuMessageBase.editDate;
        tLRPC$Message.views = ayuMessageBase.views;
        if ((i4 & 4) != 0) {
            TLRPC.TL_messageFwdHeader tLRPC$TL_messageFwdHeader = new TLRPC.TL_messageFwdHeader();
            tLRPC$Message.fwd_from = tLRPC$TL_messageFwdHeader;
            tLRPC$TL_messageFwdHeader.flags = ayuMessageBase.fwdFlags;
            tLRPC$TL_messageFwdHeader.from_id = messagesController.getPeer(ayuMessageBase.fwdFromId);
            TLRPC.MessageFwdHeader tLRPC$MessageFwdHeader = tLRPC$Message.fwd_from;
            tLRPC$MessageFwdHeader.from_name = ayuMessageBase.fwdName;
            tLRPC$MessageFwdHeader.date = ayuMessageBase.fwdDate;
            tLRPC$MessageFwdHeader.post_author = ayuMessageBase.fwdPostAuthor;
        }
        if ((tLRPC$Message.flags & 8) != 0) {
            // TLRPC.TL_messageReplyHeader tLRPC$TL_messageReplyHeader = new TLRPC.TL_messageReplyHeader();
            // tLRPC$Message.reply_to = tLRPC$TL_messageReplyHeader;
            // tLRPC$TL_messageReplyHeader.flags = ayuMessageBase.replyFlags;
            // tLRPC$TL_messageReplyHeader.reply_to_msg_id = ayuMessageBase.replyMessageId;
            // tLRPC$TL_messageReplyHeader.reply_to_peer_id = messagesController.getPeer(ayuMessageBase.replyPeerId);
            // TLRPC.TL_messageReplyHeader tLRPC$TL_messageReplyHeader2 = tLRPC$Message.reply_to;
            // tLRPC$TL_messageReplyHeader2.reply_to_top_id = ayuMessageBase.replyTopId;
            // tLRPC$TL_messageReplyHeader2.forum_topic = ayuMessageBase.replyForumTopic;
            MessageReplyHeader replyHeader = tLRPC$Message.reply_to;
            if (replyHeader instanceof TL_messageReplyHeader) { // Check the instance
                TL_messageReplyHeader tLRPC$TL_messageReplyHeader2 = (TL_messageReplyHeader) replyHeader;
                // ... access fields of tLRPC$TL_messageReplyHeader2
                tLRPC$TL_messageReplyHeader2.flags = ayuMessageBase.replyFlags;
                tLRPC$TL_messageReplyHeader2.reply_to_msg_id = ayuMessageBase.replyMessageId;
                tLRPC$TL_messageReplyHeader2.reply_to_peer_id = messagesController.getPeer(ayuMessageBase.replyPeerId);
                tLRPC$TL_messageReplyHeader2.reply_to_top_id = ayuMessageBase.replyTopId;
                tLRPC$TL_messageReplyHeader2.forum_topic = ayuMessageBase.replyForumTopic;
    
            } else if (replyHeader instanceof TLRPC.TL_messageReplyStoryHeader) {
                // Handle TL_messageReplyStoryHeader
            }
        }
        tLRPC$Message.message = ayuMessageBase.text;
        tLRPC$Message.entities = deserializeMultiple(
            ayuMessageBase.textEntities,
            (NativeByteBuffer nativeByteBuffer) ->
                TLRPC.MessageEntity.TLdeserialize(
                    nativeByteBuffer,
                    nativeByteBuffer.readInt32(false),
                    false
                )
        );
    }

    public static void map(AyuSavePreferences ayuSavePreferences, AyuMessageBase ayuMessageBase) {
        TLRPC.Message message = ayuSavePreferences.getMessage();
        ayuMessageBase.userId = ayuSavePreferences.getUserId();
        ayuMessageBase.dialogId = ayuSavePreferences.getDialogId();
        ayuMessageBase.groupedId = message.grouped_id;
        ayuMessageBase.peerId = MessageObject.getPeerId(message.peer_id);
        ayuMessageBase.fromId = MessageObject.getPeerId(message.from_id);
        ayuMessageBase.topicId = ayuSavePreferences.getTopicId();
        ayuMessageBase.messageId = message.id;
        ayuMessageBase.date = message.date;
        ayuMessageBase.flags = message.flags;
        ayuMessageBase.editDate = message.edit_date;
        ayuMessageBase.views = message.views;
        TLRPC.MessageFwdHeader tLRPC$MessageFwdHeader = message.fwd_from;
        if (tLRPC$MessageFwdHeader != null) {
            ayuMessageBase.fwdFlags = tLRPC$MessageFwdHeader.flags;
            ayuMessageBase.fwdFromId = MessageObject.getPeerId(tLRPC$MessageFwdHeader.from_id);
            TLRPC.MessageFwdHeader tLRPC$MessageFwdHeader2 = message.fwd_from;
            ayuMessageBase.fwdName = tLRPC$MessageFwdHeader2.from_name;
            ayuMessageBase.fwdDate = tLRPC$MessageFwdHeader2.date;
            ayuMessageBase.fwdPostAuthor = tLRPC$MessageFwdHeader2.post_author;
        }
        // TLRPC.TL_messageReplyHeader tLRPC$TL_messageReplyHeader = message.reply_to;
        // if (tLRPC$TL_messageReplyHeader != null) {
        //     ayuMessageBase.replyFlags = tLRPC$TL_messageReplyHeader.flags;
        //     ayuMessageBase.replyMessageId = tLRPC$TL_messageReplyHeader.reply_to_msg_id;
        //     ayuMessageBase.replyPeerId = MessageObject.getPeerId(tLRPC$TL_messageReplyHeader.reply_to_peer_id);
        //     TLRPC.TL_messageReplyHeader tLRPC$TL_messageReplyHeader2 = message.reply_to;
        //     ayuMessageBase.replyTopId = tLRPC$TL_messageReplyHeader2.reply_to_top_id;
        //     ayuMessageBase.replyForumTopic = tLRPC$TL_messageReplyHeader2.forum_topic;
        // }
        MessageReplyHeader tLRPC$TL_messageReplyHeader = message.reply_to;
        if (tLRPC$TL_messageReplyHeader != null) {
            ayuMessageBase.replyFlags = tLRPC$TL_messageReplyHeader.flags;
            ayuMessageBase.replyMessageId = tLRPC$TL_messageReplyHeader.reply_to_msg_id;
            if (tLRPC$TL_messageReplyHeader.reply_to_peer_id != null) {
                ayuMessageBase.replyPeerId = MessageObject.getPeerId(tLRPC$TL_messageReplyHeader.reply_to_peer_id);
            }
            ayuMessageBase.replyTopId = tLRPC$TL_messageReplyHeader.reply_to_top_id;
            ayuMessageBase.replyForumTopic = tLRPC$TL_messageReplyHeader.forum_topic;

        }
        ayuMessageBase.entityCreateDate = ayuSavePreferences.getRequestCatchTime();
        ayuMessageBase.text = message.message;
        ayuMessageBase.textEntities = serializeMultiple(message.entities);
    }

    public static void mapMedia(AyuMessageBase ayuMessageBase, TLRPC.Message tLRPC$Message) {
        byte[] bArr;
        int i = ayuMessageBase.documentType;
        byte[] bArr2 = ayuMessageBase.documentSerialized;
        String str = ayuMessageBase.mediaPath;
        int i2 = ayuMessageBase.date;
        if (i != 0) {
            if (i == 2 || !TextUtils.isEmpty(str)) {
                if (i == 2) {
                    try {
                        NativeByteBuffer nativeByteBuffer = new NativeByteBuffer(bArr2.length);
                        nativeByteBuffer.put(ByteBuffer.wrap(bArr2));
                        nativeByteBuffer.reuse();
                        nativeByteBuffer.rewind();
                        tLRPC$Message.media = TLRPC.MessageMedia.TLdeserialize(nativeByteBuffer, nativeByteBuffer.readInt32(false), false);
                    } catch (Exception unused) {
                        Log.e(NAX, "fake news sticker..");
                    }
                    tLRPC$Message.stickerVerified = 1;
                    return;
                }
                tLRPC$Message.attachPath = str;
                File file = new File(str);
                if (i == 1) {
                    Pair extractImageSizeFromName = AyuUtils.extractImageSizeFromName(file.getName());
                    if (extractImageSizeFromName == null) {
                        extractImageSizeFromName = AyuUtils.extractImageSizeFromFile(file.getAbsolutePath());
                    }
                    if (extractImageSizeFromName == null) {
                        extractImageSizeFromName = new Pair(500, 302);
                    }
                    TLRPC.TL_messageMediaPhoto tLRPC$TL_messageMediaPhoto = new TLRPC.TL_messageMediaPhoto();
                    tLRPC$Message.media = tLRPC$TL_messageMediaPhoto;
                    tLRPC$TL_messageMediaPhoto.flags = 1;
                    tLRPC$TL_messageMediaPhoto.photo = new TLRPC.TL_photo();
                    TLRPC.Photo tLRPC$Photo = tLRPC$Message.media.photo;
                    tLRPC$Photo.has_stickers = false;
                    tLRPC$Photo.date = i2;
                    TLRPC.TL_photoSize tLRPC$TL_photoSize = new TLRPC.TL_photoSize();
                    tLRPC$TL_photoSize.size = (int) file.length();
                    tLRPC$TL_photoSize.w = ((Integer) extractImageSizeFromName.first).intValue();
                    tLRPC$TL_photoSize.h = ((Integer) extractImageSizeFromName.second).intValue();
                    tLRPC$TL_photoSize.type = "y";
                    tLRPC$TL_photoSize.location = new AyuFileLocation(str);
                    tLRPC$Message.media.photo.sizes.add(tLRPC$TL_photoSize);
                } else if (i == 3) {
                    TLRPC.TL_messageMediaDocument tLRPC$TL_messageMediaDocument = new TLRPC.TL_messageMediaDocument();
                    tLRPC$Message.media = tLRPC$TL_messageMediaDocument;
                    tLRPC$TL_messageMediaDocument.flags = 1;
                    tLRPC$TL_messageMediaDocument.document = new TLRPC.TL_document();
                    TLRPC.Document tLRPC$Document = tLRPC$Message.media.document;
                    tLRPC$Document.date = i2;
                    tLRPC$Document.localPath = str;
                    tLRPC$Document.file_name = AyuUtils.getReadableFilename(file.getName());
                    tLRPC$Message.media.document.file_name_fixed = AyuUtils.getReadableFilename(file.getName());
                    tLRPC$Message.media.document.size = file.length();
                    TLRPC.Document tLRPC$Document2 = tLRPC$Message.media.document;
                    tLRPC$Document2.mime_type = ayuMessageBase.mimeType;
                    tLRPC$Document2.attributes = deserializeMultiple(
                        ayuMessageBase.documentAttributesSerialized,
                        (NativeByteBuffer nativeByteBuffer) ->
                            TLRPC.DocumentAttribute.TLdeserialize(
                                nativeByteBuffer,
                                nativeByteBuffer.readInt32(false),
                                false
                            )
                    );
                    Iterator it = deserializeMultiple(
                        ayuMessageBase.thumbsSerialized,
                        (NativeByteBuffer nativeByteBuffer) ->
                            TLRPC.PhotoSize.TLdeserialize(
                                0L,
                                0L,
                                0L,
                                nativeByteBuffer,
                                nativeByteBuffer.readInt32(false),
                                false
                            )
                    ).iterator();
                    while (it.hasNext()) {
                        TLRPC.PhotoSize tLRPC$PhotoSize = (TLRPC.PhotoSize) it.next();
                        if (tLRPC$PhotoSize != null) {
                            if ((tLRPC$PhotoSize instanceof TLRPC.TL_photoSize) && !TextUtils.isEmpty(ayuMessageBase.hqThumbPath) && ((bArr = tLRPC$PhotoSize.bytes) == null || bArr.length == 0)) {
                                tLRPC$PhotoSize.location = new AyuFileLocation(ayuMessageBase.hqThumbPath);
                            }
                            byte[] bArr3 = tLRPC$PhotoSize.bytes;
                            if ((bArr3 != null && bArr3.length != 0) || tLRPC$PhotoSize.location != null) {
                                tLRPC$Message.media.document.thumbs.add(tLRPC$PhotoSize);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void mapMedia(AyuSavePreferences ayuSavePreferences, AyuMessageBase ayuMessageBase, boolean z) {
        File processAttachment;
        TLRPC.Message message = ayuSavePreferences.getMessage();
        if (shouldSaveMedia(ayuSavePreferences)) {
            TLRPC.MessageMedia tLRPC$MessageMedia = message.media;
            if (tLRPC$MessageMedia == null) {
                ayuMessageBase.documentType = 0;
            } else if ((tLRPC$MessageMedia instanceof TLRPC.TL_messageMediaPhoto) && tLRPC$MessageMedia.photo != null) {
                ayuMessageBase.documentType = 1;
            } else if ((tLRPC$MessageMedia instanceof TLRPC.TL_messageMediaDocument) && tLRPC$MessageMedia.document != null && (MessageObject.isStickerMessage(message) || message.media.document.mime_type.equals("application/x-tgsticker"))) {
                ayuMessageBase.documentType = 2;
                ayuMessageBase.mimeType = message.media.document.mime_type;
                try {
                    NativeByteBuffer nativeByteBuffer = new NativeByteBuffer(message.media.getObjectSize());
                    message.media.serializeToStream(nativeByteBuffer);
                    nativeByteBuffer.reuse();
                    nativeByteBuffer.buffer.rewind();
                    byte[] bArr = new byte[nativeByteBuffer.buffer.remaining()];
                    nativeByteBuffer.buffer.get(bArr);
                    ayuMessageBase.documentSerialized = bArr;
                } catch (Exception e) {
                    Log.e(NAX, "fake news sticker", e);
                }
            } else {
                ayuMessageBase.documentType = 3;
            }
            int i = ayuMessageBase.documentType;
            if (i == 1 || i == 3) {
                File file = new File("/");
                try {
                    if (z) {
                        file = processAttachment(ayuSavePreferences);
                        TLRPC.MessageMedia media = MessageObject.getMedia(ayuSavePreferences.getMessage());
                        if (media != null && MessageObject.isVideoDocument(media.document)) {
                            Iterator<TLRPC.PhotoSize> it = media.document.thumbs.iterator();
                            while (true) {
                                if (!it.hasNext()) {
                                    break;
                                }
                                TLRPC.PhotoSize next = it.next();
                                if ((next instanceof TLRPC.TL_photoSize) && (processAttachment = processAttachment(ayuSavePreferences.getAccountId(), next)) != null && !processAttachment.getAbsolutePath().equals("/")) {
                                    ayuMessageBase.hqThumbPath = processAttachment.getAbsolutePath();
                                    break;
                                }
                            }
                        }
                    } else {
                        file = FileLoader.getInstance(ayuSavePreferences.getAccountId()).getPathToMessage(ayuSavePreferences.getMessage());
                    }
                    TLRPC.Document tLRPC$Document = message.media.document;
                    if (tLRPC$Document != null) {
                        ayuMessageBase.documentAttributesSerialized = serializeMultiple(tLRPC$Document.attributes);
                        ayuMessageBase.thumbsSerialized = serializeMultiple(message.media.document.thumbs);
                        ayuMessageBase.mimeType = message.media.document.mime_type;
                    }
                } catch (Exception e2) {
                    Log.e(NAX, "failed to save media", e2);
                }
                String absolutePath = file.getAbsolutePath();
                if (absolutePath.equals("/")) {
                    absolutePath = null;
                }
                ayuMessageBase.mediaPath = absolutePath;
            }
        }
    }

    private static File processAttachment(int i, TLObject tLObject) {
        File pathToAttach = FileLoader.getInstance(i).getPathToAttach(tLObject);
        if (!pathToAttach.exists()) {
            File pathToAttach2 = FileLoader.getInstance(i).getPathToAttach(tLObject, true);
            if (!pathToAttach2.getAbsolutePath().endsWith("/cache")) {
                pathToAttach = pathToAttach2;
            }
        }
        return processAttachment(pathToAttach, new File(AyuMessagesController.attachmentsPath, AyuUtils.getFilename(tLObject, pathToAttach)));
    }

    private static File processAttachment(AyuSavePreferences ayuSavePreferences) {
        TLRPC.Message message = ayuSavePreferences.getMessage();
        File pathToMessage = FileLoader.getInstance(ayuSavePreferences.getAccountId()).getPathToMessage(message);
        if (!pathToMessage.exists() && !pathToMessage.getAbsolutePath().endsWith("/cache")) {
            pathToMessage = FileLoader.getInstance(ayuSavePreferences.getAccountId()).getPathToMessage(message, false);
        }
        if (pathToMessage.exists() || message.media.document == null) {
            if (pathToMessage.exists() || message.media.photo == null) {
                return processAttachment(pathToMessage, new File(AyuMessagesController.attachmentsPath, AyuUtils.getFilename(message, pathToMessage)));
            }
            return processAttachment(ayuSavePreferences.getAccountId(), message.media.photo);
        }
        return processAttachment(ayuSavePreferences.getAccountId(), message.media.document);
    }

    private static File processAttachment(File file, File file2) {
        if (file.exists()) {
            return AyuUtils.moveOrCopyFile(file, file2) ? new File(file2.getAbsolutePath()) : new File("/");
        }
        File directory = FileLoader.getDirectory(4);
        File file3 = new File(directory, file.getName() + ".enc");
        if (file3.exists()) {
            File internalCacheDir = FileLoader.getInternalCacheDir();
            File file4 = new File(internalCacheDir, file3.getName() + ".key");
            if (BuildVars.LOGS_ENABLED) Log.d(NAX, "key file " + file4.getAbsolutePath() + " exists " + file4.exists());
            if (file4.exists()) {
                try {
                    EncryptedFileInputStream encryptedFileInputStream = new EncryptedFileInputStream(file3, file4);
                    FileOutputStream fileOutputStream = new FileOutputStream(file2);
                    try {
                        byte[] bArr = new byte[1024];
                        while (true) {
                            int read = encryptedFileInputStream.read(bArr);
                            if (read == -1) {
                                if (BuildVars.LOGS_ENABLED) Log.d(NAX, "encrypted media copy success");
                                fileOutputStream.close();
                                encryptedFileInputStream.close();
                                return file2;
                            }
                            fileOutputStream.write(bArr, 0, read);
                        }
                    } catch (Throwable th) {
                        try {
                            fileOutputStream.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                        throw th;
                    }
                } catch (Exception e) {
                    Log.e(NAX, "encrypted media copy failed", e);
                    return new File("/");
                }
            }
        }
        return new File("/");
    }

    public static byte[] serializeMultiple(ArrayList arrayList) {
        // if (arrayList == null || arrayList.size() == 0 || !AyuConfig.saveFormatting) {
        if (arrayList == null || arrayList.size() == 0) {
            return "".getBytes();
        }
        try {
            NativeByteBuffer nativeByteBuffer = new NativeByteBuffer(arrayList.stream().mapToInt(new ToIntFunction() { // from class: com.radolyn.ayugram.proprietary.AyuMessageUtils$$ExternalSyntheticLambda0
                @Override // java.util.function.ToIntFunction
                public final int applyAsInt(Object obj) {
                    return ((TLObject) obj).getObjectSize();
                }
            }).sum());
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                ((TLObject) it.next()).serializeToStream(nativeByteBuffer);
            }
            nativeByteBuffer.reuse();
            nativeByteBuffer.rewind();
            byte[] bArr = new byte[nativeByteBuffer.remaining()];
            nativeByteBuffer.buffer.get(bArr);
            return bArr;
        } catch (Exception unused) {
            Log.e(NAX, "Failed to allocate buffer for message entities");
            return "".getBytes();
        }
    }

    private static boolean shouldSaveMedia(AyuSavePreferences ayuSavePreferences) {
        if (NaConfig.INSTANCE.getMessageSavingSaveMedia().Bool() && ayuSavePreferences.getMessage().media != null) {
            if (DialogObject.isUserDialog(ayuSavePreferences.getDialogId())) {
                return NaConfig.INSTANCE.getSaveMediaInPrivateChats().Bool();
            }
            TLRPC.Chat chat = MessagesController.getInstance(ayuSavePreferences.getAccountId()).getChat(Long.valueOf(Math.abs(ayuSavePreferences.getDialogId())));
            if (chat == null) {
                Log.d(NAX, "chat is null so saving media just in case");
                return true;
            }
            boolean isPublic = ChatObject.isPublic(chat);
            if (ChatObject.isChannel(chat)) {
                if (isPublic && NaConfig.INSTANCE.getSaveMediaInPublicChannels().Bool()) {
                    return true;
                }
                return !isPublic && NaConfig.INSTANCE.getSaveMediaInPrivateChannels().Bool();
            } else if (isPublic && NaConfig.INSTANCE.getSaveMediaInPublicGroups().Bool()) {
                return true;
            } else {
                return !isPublic && NaConfig.INSTANCE.getSaveMediaInPrivateGroups().Bool();
            }
        }
        return false;
    }
}
