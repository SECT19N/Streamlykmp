package dev.section.streamly.livetv.data

import dev.section.streamly.livetv.domain.Channel
import kotlinx.serialization.Serializable

@Serializable
data class ChannelDto(
    val id: String? = null,
    val name: String? = null,
    val logo: String? = null,
    val category: String? = null,
    val streamUrl: String? = null,
)

/** Returns null for entries that cannot be played (missing id, name or stream). */
fun ChannelDto.toDomain(): Channel? {
    val id = id?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    val name = name?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    val streamUrl = streamUrl?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    return Channel(
        id = id,
        name = name,
        category = category?.trim().takeUnless { it.isNullOrEmpty() } ?: DEFAULT_CATEGORY,
        logoUrl = logo?.trim().takeUnless { it.isNullOrEmpty() },
        streamUrl = streamUrl,
    )
}

private const val DEFAULT_CATEGORY = "Other"
