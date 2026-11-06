package dev.section.streamly.favorites

import dev.section.streamly.core.mvi.UiEffect
import dev.section.streamly.core.mvi.UiIntent
import dev.section.streamly.core.mvi.UiState
import dev.section.streamly.home.domain.MediaItem

enum class FavoritesTab(val label: String, val isMovie: Boolean) {
    Movies("Movies", true),
    Shows("TV Shows", false),
}

data class FavoritesState(
    val favorites: List<MediaItem> = emptyList(),
    val tab: FavoritesTab = FavoritesTab.Movies,
) : UiState {
    val visible: List<MediaItem> get() = favorites.filter { it.isMovie == tab.isMovie }
}

sealed interface FavoritesIntent : UiIntent {
    data class ItemClicked(val id: Int, val isMovie: Boolean) : FavoritesIntent
    data class SelectTab(val tab: FavoritesTab) : FavoritesIntent
}

sealed interface FavoritesEffect : UiEffect {
    data class OpenDetails(val id: Int, val isMovie: Boolean) : FavoritesEffect
}
