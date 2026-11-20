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
    /** Replace the cached playlist atomically, so observers never see a half-written list. */
    @Transaction
    suspend fun replaceAll(items: List<ChannelEntity>) {
        clear()
        insertAll(items)
    }

    /**
     * Replaces a large playlist without first allocating a second full-size list of entities.
     * Room inserts bounded batches; the source Channel list remains the only full in-memory copy.
     */
    @Transaction
    suspend fun replaceAllChannels(channels: List<Channel>, batchSize: Int = 400) {
        clear()
        var start = 0
        while (start < channels.size) {
            val end = minOf(start + batchSize, channels.size)
            val batch = ArrayList<ChannelEntity>(end - start)
            for (index in start until end) batch.add(ChannelEntity.from(channels[index]))
            insertAll(batch)
            start = end
        }
    }
}
