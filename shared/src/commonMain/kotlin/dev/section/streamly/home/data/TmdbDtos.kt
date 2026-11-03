package dev.section.streamly.home.data

import dev.section.streamly.home.domain.MediaItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbPageDto(
    val results: List<TmdbMediaDto> = emptyList(),
    val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
)

/** Movies use `title`, shows use `name`; every field is optional so one bad entry can't fail the page. */
@Serializable
data class TmdbMediaDto(
    val id: Int? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
) {
    fun toDomain(isMovie: Boolean): MediaItem? {
        val itemId = id ?: return null
        val itemTitle = (if (isMovie) title else name)?.takeIf { it.isNotBlank() } ?: return null
        return MediaItem(itemId, itemTitle, posterPath?.takeIf { it.isNotBlank() }, isMovie)
    }
}
