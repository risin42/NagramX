/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram.database.entities;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

public class DeletedMessageFull {
    @Embedded
    public DeletedMessage message;

    @Relation(
            parentColumn = "fakeId",
            entityColumn = "deletedMessageId"
    )
    public List<DeletedMessageReaction> reactions;
}
