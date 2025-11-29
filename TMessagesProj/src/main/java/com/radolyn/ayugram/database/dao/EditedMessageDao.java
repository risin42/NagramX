/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.radolyn.ayugram.database.entities.EditedMessage;

import java.util.List;

@Dao
public interface EditedMessageDao {
    @Query("SELECT * FROM editedmessage WHERE userId = :userId AND dialogId = :dialogId AND messageId = :messageId ORDER BY entityCreateDate")
    List<EditedMessage> getAllRevisions(long userId, long dialogId, long messageId);

    @Query("UPDATE editedmessage SET mediaPath = :newPath WHERE userId = :userId AND dialogId = :dialogId AND messageId = :messageId AND mediaPath = :oldPath")
    void updateAttachmentForRevisionsBetweenDates(long userId, long dialogId, long messageId, String oldPath, String newPath);

    @Query("SELECT * FROM editedmessage WHERE userId = :userId AND dialogId = :dialogId AND messageId = :messageId ORDER BY entityCreateDate DESC LIMIT 1")
    EditedMessage getLastRevision(long userId, long dialogId, long messageId);

    @Query("SELECT EXISTS(SELECT * FROM editedmessage WHERE userId = :userId AND dialogId = :dialogId AND messageId = :messageId)")
    boolean hasAnyRevisions(long userId, long dialogId, long messageId);

    @Query("DELETE FROM editedmessage WHERE dialogId = :dialogId")
    void delete(long dialogId);

    @Query("DELETE FROM editedmessage WHERE dialogId = :dialogId AND messageId IN (:messageIds)")
    void deleteByDialogIdAndMessageIds(long dialogId, List<Integer> messageIds);

    @Query("DELETE FROM editedmessage WHERE fakeId = :fakeId")
    int deleteByFakeId(long fakeId);

    @Query("SELECT mediaPath FROM editedmessage WHERE fakeId = :fakeId LIMIT 1")
    String getMediaPathByFakeId(long fakeId);

    @Insert
    void insert(EditedMessage revision);
}
