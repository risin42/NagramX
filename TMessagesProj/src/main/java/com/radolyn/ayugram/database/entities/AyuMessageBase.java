/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram.database.entities;

public abstract class AyuMessageBase {
    public long userId;
    public long dialogId;
    public long groupedId;
    public long peerId;
    public long fromId;
    public long topicId;
    public int messageId;
    public int date;

    public int flags;

    public int editDate;
    public int views;

    public int fwdFlags;
    public long fwdFromId;
    public String fwdName;
    public int fwdDate;
    public String fwdPostAuthor;

    public int replyFlags;
    public int replyMessageId;
    public long replyPeerId;
    public int replyTopId;
    public boolean replyForumTopic;

    public int entityCreateDate;

    public String text; // plain text
    public byte[] textEntities; // TL serialized
    public String mediaPath; // full path
    public String hqThumbPath; // full path
    public int documentType; // see DOCUMENT_TYPE_*
    public byte[] documentSerialized; // for sticker; TL serialized
    public byte[] thumbsSerialized; // for video/etc.; TL serialized
    public byte[] documentAttributesSerialized; // for video/voice/etc.; TL serialized
    public String mimeType;
}
