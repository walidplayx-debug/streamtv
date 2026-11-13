package com.streamtv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ChannelEntity::class], version = 1, exportSchema = false)
abstract class StreamDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    companion object {
        @Volatile private var INSTANCE: StreamDatabase? = null
        fun get(context: Context): StreamDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, StreamDatabase::class.java, "streamtv.db").build().also { INSTANCE = it }
        }
    }
}
