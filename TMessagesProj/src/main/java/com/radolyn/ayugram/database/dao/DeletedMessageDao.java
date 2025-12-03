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
    @Query("SELECT * FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND :startId <= messageId AND messageId <= :endId ORDER BY messageId LIMIT :limit")
    List<DeletedMessageFull> getMessages(long userId, long dialogId, long startId, long endId, int limit);

    @Transaction
    @Query("SELECT * FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND topicId = :topicId AND :startId <= messageId AND messageId <= :endId ORDER BY messageId LIMIT :limit")
    List<DeletedMessageFull> getTopicMessages(long userId, long dialogId, long topicId, long startId, long endId, int limit);

    @Transaction
    @Query("SELECT * FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND replyMessageId = :threadMessageId AND :startId <= messageId AND messageId <= :endId ORDER BY messageId LIMIT :limit")
    List<DeletedMessageFull> getThreadMessages(long userId, long dialogId, long threadMessageId, long startId, long endId, int limit);

    @Transaction
    @Query("SELECT * FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND groupedId = :groupedId ORDER BY messageId")
    List<DeletedMessageFull> getMessagesGrouped(long userId, long dialogId, long groupedId);

    @Transaction
    @Query("SELECT * FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND groupedId IN (:groupedIds) ORDER BY messageId")
    List<DeletedMessageFull> getMessagesGroupedIn(long userId, long dialogId, List<Long> groupedIds);

    @Transaction
    @Query("SELECT * FROM deletedmessage WHERE dialogId = :dialogId")
    List<DeletedMessageFull> getMessagesByDialog(long dialogId);

    @Transaction
    @Query("SELECT * FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId ORDER BY messageId DESC LIMIT :limit")
    List<DeletedMessageFull> getLatestMessages(long userId, long dialogId, int limit);

    @Transaction
    @Query("SELECT * FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND messageId < :before ORDER BY messageId DESC LIMIT :limit")
    List<DeletedMessageFull> getOlderMessagesBefore(long userId, long dialogId, int before, int limit);

    @Transaction
    @Query("SELECT messageId FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND messageId IN (:messageIds)")
    List<Integer> getExistingMessageIds(long userId, long dialogId, List<Integer> messageIds);

    @Transaction
    @Query("SELECT * FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND messageId IN (:messageIds)")
    List<DeletedMessageFull> getMessagesByIds(long userId, long dialogId, List<Integer> messageIds);

    @Insert
    long insert(DeletedMessage msg);

    @Insert
    void insertReaction(DeletedMessageReaction reaction);

    @Query("SELECT COUNT(*) FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId")
    int countByDialog(long userId, long dialogId);

    @Query("SELECT EXISTS(SELECT * FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND topicId = :topicId AND messageId = :msgId)")
    boolean exists(long userId, long dialogId, long topicId, int msgId);

    @Query("DELETE FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND messageId = :msgId")
    void delete(long userId, long dialogId, int msgId);

    @Query("DELETE FROM deletedmessage WHERE dialogId = :dialogId")
    void delete(long dialogId);

    @Query("DELETE FROM deletedmessage WHERE userId = :userId AND dialogId = :dialogId AND messageId IN (:messageIds)")
    void deleteMessages(long userId, long dialogId, List<Integer> messageIds);

    @Query("UPDATE deletedmessage SET mediaPath = :newPath WHERE userId = :userId AND dialogId = :dialogId AND messageId = :messageId AND (mediaPath IS NULL OR mediaPath = '')")
    void updateMediaPathIfEmpty(long userId, long dialogId, int messageId, String newPath);
}
