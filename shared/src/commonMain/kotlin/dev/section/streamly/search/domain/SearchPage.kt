package dev.section.streamly.search.domain

import dev.section.streamly.home.domain.MediaItem

data class SearchPage(
    val items: List<MediaItem>,
    val page: Int,
    val totalPages: Int,
) {
    val canLoadMore: Boolean get() = page < totalPages
}
