package dev.section.streamly.details.domain

data class CastMember(
    val name: String,
    val character: String?,
    val profilePath: String?,
) {
    fun profileUrl(size: String = "w185"): String? =
        profilePath?.let { "https://image.tmdb.org/t/p/$size$it" }
}

data class MediaDetails(
    val id: Int,
    val isMovie: Boolean,
    val title: String,
    val tagline: String?,
    val overview: String?,
    val year: String?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    /** TMDB average on a 0..10 scale, or null when nobody has voted. */
    val rating: Double?,
    val posterPath: String?,
    val backdropPath: String?,
    val cast: List<CastMember>,
) {
    fun posterUrl(size: String = "w500"): String? =
        posterPath?.let { "https://image.tmdb.org/t/p/$size$it" }

    fun backdropUrl(size: String = "w780"): String? =
        backdropPath?.let { "https://image.tmdb.org/t/p/$size$it" }
}
