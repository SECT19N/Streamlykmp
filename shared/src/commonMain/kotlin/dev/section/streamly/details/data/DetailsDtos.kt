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
    val status: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("spoken_languages") val spokenLanguages: List<TmdbLanguageDto> = emptyList(),
    val budget: Long? = null,
    val revenue: Long? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("belongs_to_collection") val belongsToCollection: TmdbCollectionRefDto? = null,
    /** Movies: `append_to_response=release_dates`. */
    @SerialName("release_dates") val releaseDates: TmdbReleaseDatesDto? = null,
    /** Shows: `append_to_response=content_ratings`. */
    @SerialName("content_ratings") val contentRatings: TmdbContentRatingsDto? = null,
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
            certification = if (isMovie) movieCertification() else showCertification(),
            status = status?.takeIf { it.isNotBlank() },
            originalLanguage = languageName(),
            budget = budget?.takeIf { it > 0 },
            revenue = revenue?.takeIf { it > 0 },
            seasons = numberOfSeasons?.takeIf { it > 0 },
            episodes = numberOfEpisodes?.takeIf { it > 0 },
        )
    }
}

private const val CertificationRegion = "US"

private fun TmdbDetailsDto.movieCertification(): String? {
    val countries = releaseDates?.results.orEmpty()
    fun TmdbReleaseCountryDto.cert() =
        releaseDates.firstNotNullOfOrNull { it.certification?.takeIf(String::isNotBlank) }
    return (countries.firstOrNull { it.country == CertificationRegion }?.cert()
        ?: countries.firstNotNullOfOrNull { it.cert() })
}

private fun TmdbDetailsDto.showCertification(): String? {
    val countries = contentRatings?.results.orEmpty()
    return (countries.firstOrNull { it.country == CertificationRegion }?.rating
        ?.takeIf(String::isNotBlank)
        ?: countries.firstNotNullOfOrNull { it.rating?.takeIf(String::isNotBlank) })
}

/** English name of the original language, falling back to the upper-cased code. */
private fun TmdbDetailsDto.languageName(): String? {
    val code = originalLanguage?.takeIf { it.isNotBlank() } ?: return null
    return spokenLanguages.firstOrNull { it.code == code }?.englishName?.takeIf { it.isNotBlank() }
        ?: code.uppercase()
}

@Serializable
data class TmdbLanguageDto(
    @SerialName("iso_639_1") val code: String? = null,
    @SerialName("english_name") val englishName: String? = null,
)

@Serializable
data class TmdbCollectionRefDto(val id: Int? = null, val name: String? = null)

@Serializable
data class TmdbReleaseDatesDto(val results: List<TmdbReleaseCountryDto> = emptyList())

@Serializable
data class TmdbReleaseCountryDto(
    @SerialName("iso_3166_1") val country: String? = null,
    @SerialName("release_dates") val releaseDates: List<TmdbReleaseDto> = emptyList(),
)

@Serializable
data class TmdbReleaseDto(val certification: String? = null)

@Serializable
data class TmdbContentRatingsDto(val results: List<TmdbContentRatingDto> = emptyList())

@Serializable
data class TmdbContentRatingDto(
    @SerialName("iso_3166_1") val country: String? = null,
    val rating: String? = null,
)

@Serializable
data class TmdbCollectionDto(val parts: List<TmdbCollectionPartDto> = emptyList())

@Serializable
data class TmdbCollectionPartDto(
    val id: Int? = null,
    val title: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
)

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
