package dev.section.streamly.livetv.domain

data class Channel(
    val id: String,
    val name: String,
    val category: String,
    val logoUrl: String?,
    val streamUrl: String,
)
