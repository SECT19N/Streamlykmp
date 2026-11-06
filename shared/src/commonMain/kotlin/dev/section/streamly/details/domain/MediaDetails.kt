package dev.section.streamly.details.domain

import dev.section.streamly.home.domain.MediaItem

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
    /** Age rating such as "PG-13" or "TV-MA", or null when none is listed. */
    val certification: String? = null,
    val status: String? = null,
    val originalLanguage: String? = null,
    /** Movies only; null when TMDB has no figure (it stores 0). */
    val budget: Long? = null,
    val revenue: Long? = null,
    /** Shows only. */
    val seasons: Int? = null,
    val episodes: Int? = null,
    /** The franchise this movie belongs to, with the other films in it. Null when none. */
    val collection: MediaCollection? = null,
) {
    fun posterUrl(size: String = "w500"): String? =
        posterPath?.let { "https://image.tmdb.org/t/p/$size$it" }

    fun backdropUrl(size: String = "w780"): String? =
        backdropPath?.let { "https://image.tmdb.org/t/p/$size$it" }
}

data class MediaCollection(val name: String, val parts: List<MediaItem>)

data class Fact(val label: String, val value: String)

/** The labelled facts for the "Details" section, in display order; unknown values are left out. */
fun MediaDetails.facts(): List<Fact> = buildList {
    status?.let { add(Fact("Status", it)) }
    originalLanguage?.let { add(Fact("Original language", it)) }
    seasons?.let { add(Fact("Seasons", it.toString())) }
    episodes?.let { add(Fact("Episodes", it.toString())) }
    budget?.let { add(Fact("Budget", formatMoney(it))) }
    revenue?.let { add(Fact("Revenue", formatMoney(it))) }
}

/** "$63M", "$1.2B", "$850K"; one decimal only when it isn't zero. */
fun formatMoney(amount: Long): String {
    fun scaled(unit: Long, suffix: String): String {
        val tenths = amount * 10 / unit
        val whole = tenths / 10
        val frac = tenths % 10
        return if (frac == 0L) "$$whole$suffix" else "$$whole.$frac$suffix"
    }
    return when {
        amount >= 1_000_000_000 -> scaled(1_000_000_000, "B")
        amount >= 1_000_000 -> scaled(1_000_000, "M")
        amount >= 1_000 -> scaled(1_000, "K")
        else -> "$$amount"
    }
}
