package dev.section.streamly.search

import dev.section.streamly.core.mvi.UiEffect
import dev.section.streamly.core.mvi.UiIntent
import dev.section.streamly.core.mvi.UiState
import dev.section.streamly.home.domain.MediaItem

data class SearchState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<MediaItem> = emptyList(),
    val error: String? = null,
    val isOffline: Boolean = false,
    /** True once a query has completed, so "no results" isn't shown before the first search. */
    val hasSearched: Boolean = false,
    val page: Int = 1,
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
) : UiState

sealed interface SearchIntent : UiIntent {
    data class QueryChanged(val query: String) : SearchIntent
    data object Retry : SearchIntent
    data object LoadMore : SearchIntent
    data class ItemClicked(val id: Int, val isMovie: Boolean) : SearchIntent
}

sealed interface SearchEffect : UiEffect {
    data class OpenDetails(val id: Int, val isMovie: Boolean) : SearchEffect
}
