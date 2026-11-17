package com.streamtv.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    // In the playlist's own order: rows are written in that order, so their row id keeps it.
    @Query("SELECT * FROM channels ORDER BY rowid")
    fun observeAll(): Flow<List<ChannelEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChannelEntity>)
    @Query("DELETE FROM channels")
    suspend fun clear()
}
