package dev.section.streamly.home

import dev.section.streamly.core.mvi.UiEffect
import dev.section.streamly.core.mvi.UiIntent
import dev.section.streamly.core.mvi.UiState
import dev.section.streamly.home.domain.HomeRow
import dev.section.streamly.home.domain.HomeRowType

data class HomeState(
    val isLoading: Boolean = true,
    val rows: List<HomeRow> = emptyList(),
    val error: String? = null,
    val isOffline: Boolean = false,
    /** Rows currently fetching their next page. */
    val loadingMore: Set<HomeRowType> = emptySet(),
) : UiState

sealed interface HomeIntent : UiIntent {
    data object Retry : HomeIntent
    data class LoadMore(val type: HomeRowType) : HomeIntent
    data class ItemClicked(val id: Int, val isMovie: Boolean) : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data class OpenDetails(val id: Int, val isMovie: Boolean) : HomeEffect
}
