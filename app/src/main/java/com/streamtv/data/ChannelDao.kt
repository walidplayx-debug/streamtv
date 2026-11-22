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
    /** The cached playlist, read once (observing it kept a second copy of every channel alive). */
    @Query("SELECT * FROM channels ORDER BY rowid")
    suspend fun getAll(): List<ChannelEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChannelEntity>)
    @Query("DELETE FROM channels")
    suspend fun clear()
    /** Replace the cached playlist atomically, so observers never see a half-written list. */
    @Transaction
    suspend fun replaceAll(items: List<Channel>) {
        clear()
        // In slices: one entity copy of a 50 000-entry playlist at once ran small TVs out of memory.
        for (slice in items.chunked(1000)) insertAll(slice.map { ChannelEntity.from(it) })
    }
}
