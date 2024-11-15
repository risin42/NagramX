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
import androidx.room.Transaction;
import com.radolyn.ayugram.database.entities.DeletedMessage;
import com.radolyn.ayugram.database.entities.DeletedMessageFull;
import com.radolyn.ayugram.database.entities.DeletedMessageReaction;

import java.util.List;

@Dao
public interface DeletedMessageDao {
    @Transaction
    @Query("SELECT * FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND messageId = :messageId")
    DeletedMessageFull getMessage(long userId, long dialogId, int messageId);

    @Transaction
    @Query("SELECT * FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND topicId = :topicId AND :startId <= messageId AND messageId <= :endId ORDER BY messageId LIMIT :limit")
    List<DeletedMessageFull> getMessages(long userId, long dialogId, long topicId, int startId, int endId, int limit);

    @Transaction
    @Query("SELECT * FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND groupedId = :groupedId ORDER BY messageId")
    List<DeletedMessageFull> getMessagesGrouped(long userId, long dialogId, long groupedId);

    @Insert
    long insert(DeletedMessage msg);

    @Insert
    void insertReaction(DeletedMessageReaction reaction);

    @Query("SELECT EXISTS(SELECT * FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND topicId = :topicId AND messageId = :msgId)")
    boolean exists(long userId, long dialogId, long topicId, int msgId);

    @Query("DELETE FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND messageId = :msgId")
    void delete(long userId, long dialogId, int msgId);
}
