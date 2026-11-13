package com.streamtv.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String, val name: String, val url: String, val logo: String?,
    val groupName: String, val country: String, val language: String
) {
    fun toChannel() = Channel(id, name, url, logo, groupName, country, language)
    companion object { fun from(channel: Channel) = ChannelEntity(channel.id, channel.name, channel.url, channel.logo, channel.group, channel.country, channel.language) }
}
