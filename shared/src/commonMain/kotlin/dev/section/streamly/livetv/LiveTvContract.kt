package dev.section.streamly.livetv

import dev.section.streamly.core.mvi.UiEffect
import dev.section.streamly.core.mvi.UiIntent
import dev.section.streamly.core.mvi.UiState
import dev.section.streamly.livetv.domain.Channel

data class LiveTvState(
    val isLoading: Boolean = true,
    val channels: List<Channel> = emptyList(),
    /** Null means "All". */
    val selectedCategory: String? = null,
    val error: String? = null,
    val isOffline: Boolean = false,
) : UiState {
    val categories: List<String> = channels.map { it.category }.distinct().sorted()

    val visibleChannels: List<Channel> =
        if (selectedCategory == null) channels else channels.filter { it.category == selectedCategory }
}

sealed interface LiveTvIntent : UiIntent {
    data object Retry : LiveTvIntent
    data class SelectCategory(val category: String?) : LiveTvIntent
    data class ChannelClicked(val channelId: String) : LiveTvIntent
}

sealed interface LiveTvEffect : UiEffect {
    data class OpenPlayer(val channelId: String) : LiveTvEffect
}
