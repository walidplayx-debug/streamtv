package com.streamtv.data

data class EpgProgram(
    val channelId: String, val title: String, val description: String,
    val startEpochMillis: Long, val endEpochMillis: Long
)
