package dev.section.streamly.home.domain

data class MediaItem(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val isMovie: Boolean,
) {
    /** TMDB image CDN URL, or null when the item has no poster. */
    fun posterUrl(size: String = "w342"): String? =
        posterPath?.let { "https://image.tmdb.org/t/p/$size$it" }
}

enum class HomeRowType(val title: String) {
    TrendingMovies("Trending movies"),
    TrendingShows("Trending shows"),
    PopularMovies("Popular movies"),
    PopularShows("Popular shows"),
}

data class HomeRow(val type: HomeRowType, val items: List<MediaItem>)
