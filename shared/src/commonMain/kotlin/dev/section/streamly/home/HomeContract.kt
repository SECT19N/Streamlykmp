package dev.section.streamly.home

import dev.section.streamly.core.mvi.UiEffect
import dev.section.streamly.core.mvi.UiIntent
import dev.section.streamly.core.mvi.UiState
import dev.section.streamly.home.domain.HomeRow

data class HomeState(
    val isLoading: Boolean = true,
    val rows: List<HomeRow> = emptyList(),
    val error: String? = null,
    val isOffline: Boolean = false,
) : UiState

sealed interface HomeIntent : UiIntent {
    data object Retry : HomeIntent
    data class ItemClicked(val id: Int, val isMovie: Boolean) : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data class OpenDetails(val id: Int, val isMovie: Boolean) : HomeEffect
}
