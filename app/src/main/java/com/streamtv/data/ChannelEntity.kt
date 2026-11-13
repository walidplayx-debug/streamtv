package com.streamtv.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val cacheKey: String,
    val id: String,
    val name: String,
    val url: String,
    val logo: String?,
    val groupName: String,
    val country: String,
    val language: String
) {
    fun toChannel() = Channel(id, name, url, logo, groupName, country, language)
    companion object {
        fun from(channel: Channel) = ChannelEntity(
            cacheKey = "${channel.id}|${channel.url}",
            id = channel.id,
            name = channel.name,
            url = channel.url,
            logo = channel.logo,
            groupName = channel.group,
            country = channel.country,
            language = channel.language
        )
    }
}
