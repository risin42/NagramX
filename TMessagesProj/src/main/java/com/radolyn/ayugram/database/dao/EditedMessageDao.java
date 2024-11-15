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

    @Query("SELECT COUNT(*) FROM editedmessage WHERE userId = :userId AND entityCreateDate > :fromDate")
    int getSyncCount(long userId, long fromDate);

    @Query("SELECT * FROM editedmessage WHERE userId = :userId AND entityCreateDate > :fromDate ORDER BY entityCreateDate LIMIT 50 OFFSET :offset")
    List<EditedMessage> getForSync(long userId, long fromDate, int offset);

    @Insert
    void insert(EditedMessage revision);
}
