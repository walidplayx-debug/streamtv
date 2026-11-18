package com.streamtv.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
    /**
     * Replace the cached playlist atomically, so a reader never sees a half-written list. Rows are
     * made and written a thousand at a time: a whole big playlist turned into rows at once, beside
     * the channels themselves, ran the TV out of memory.
     */
    @Transaction
    suspend fun replaceAll(channels: List<Channel>) {
        clear()
        channels.asSequence().chunked(1000).forEach { chunk -> insertAll(chunk.map { ChannelEntity.from(it) }) }
    }
}
