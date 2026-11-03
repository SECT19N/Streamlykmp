package dev.section.streamly.details.data

import dev.section.streamly.details.domain.CastMember
import dev.section.streamly.details.domain.MediaDetails
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val MaxCast = 12

/** Movie and show details share one DTO; every field is optional so a sparse record still decodes. */
@Serializable
data class TmdbDetailsDto(
    val id: Int? = null,
    val title: String? = null,
    val name: String? = null,
    val tagline: String? = null,
    val overview: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val genres: List<TmdbGenreDto> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val credits: TmdbCreditsDto? = null,
) {
    /** Null when the record has no id or title, which the repository treats as a failure. */
    fun toDomain(isMovie: Boolean): MediaDetails? {
        val detailsId = id ?: return null
        val detailsTitle = (if (isMovie) title else name)?.takeIf { it.isNotBlank() } ?: return null
        val date = if (isMovie) releaseDate else firstAirDate
        return MediaDetails(
            id = detailsId,
            isMovie = isMovie,
            title = detailsTitle,
            tagline = tagline?.takeIf { it.isNotBlank() },
            overview = overview?.takeIf { it.isNotBlank() },
            year = date?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) },
            runtimeMinutes = (if (isMovie) runtime else episodeRunTime.firstOrNull())
                ?.takeIf { it > 0 },
            genres = genres.mapNotNull { it.name?.takeIf(String::isNotBlank) },
            rating = voteAverage?.takeIf { (voteCount ?: 0) > 0 && it > 0.0 },
            posterPath = posterPath?.takeIf { it.isNotBlank() },
            backdropPath = backdropPath?.takeIf { it.isNotBlank() },
            cast = credits?.cast.orEmpty()
                .mapNotNull { it.toDomain() }
                .take(MaxCast),
        )
    }
}

@Serializable
data class TmdbGenreDto(val name: String? = null)

@Serializable
data class TmdbCreditsDto(val cast: List<TmdbCastDto> = emptyList())

@Serializable
data class TmdbCastDto(
    val name: String? = null,
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
) {
    fun toDomain(): CastMember? {
        val castName = name?.takeIf { it.isNotBlank() } ?: return null
        return CastMember(
            name = castName,
            character = character?.takeIf { it.isNotBlank() },
            profilePath = profilePath?.takeIf { it.isNotBlank() },
        )
    }
}
