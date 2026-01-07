package com.radolyn.ayugram.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.radolyn.ayugram.database.entities.LastSeenEntity;

import java.util.List;

@Dao
public interface LastSeenDao {
    @Query("SELECT * FROM LastSeenEntity")
    List<LastSeenEntity> getAll();

    @Query("DELETE FROM LastSeenEntity WHERE lastSeen < :cutoff")
    void deleteOlderThan(int cutoff);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<LastSeenEntity> entities);
}
