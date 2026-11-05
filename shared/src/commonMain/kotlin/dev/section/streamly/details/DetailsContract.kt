package dev.section.streamly.details

import dev.section.streamly.core.mvi.UiEffect
import dev.section.streamly.core.mvi.UiIntent
import dev.section.streamly.core.mvi.UiState
import dev.section.streamly.details.domain.MediaDetails

data class DetailsState(
    val isLoading: Boolean = true,
    val details: MediaDetails? = null,
    val error: String? = null,
    val isOffline: Boolean = false,
    val isFavorite: Boolean = false,
) : UiState

sealed interface DetailsIntent : UiIntent {
    data object Retry : DetailsIntent
    data object BackClicked : DetailsIntent
    data object ToggleFavorite : DetailsIntent
    data class OpenItem(val id: Int, val isMovie: Boolean) : DetailsIntent
}

sealed interface DetailsEffect : UiEffect {
    data object NavigateBack : DetailsEffect
    data class OpenDetails(val id: Int, val isMovie: Boolean) : DetailsEffect
}
