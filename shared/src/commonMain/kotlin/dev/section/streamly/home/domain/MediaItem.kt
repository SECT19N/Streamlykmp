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

enum class HomeRowType(val title: String, val isMovie: Boolean) {
    TrendingMovies("Trending movies", isMovie = true),
    TrendingShows("Trending shows", isMovie = false),
    PopularMovies("Popular movies", isMovie = true),
    PopularShows("Popular shows", isMovie = false),
}

data class HomeRow(
    val type: HomeRowType,
    val items: List<MediaItem>,
    /** Last TMDB page loaded into [items]. */
    val page: Int = 1,
    val totalPages: Int = 1,
) {
    val canLoadMore: Boolean get() = page < totalPages
}
