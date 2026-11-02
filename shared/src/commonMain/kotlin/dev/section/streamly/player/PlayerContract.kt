package dev.section.streamly.player

import dev.section.streamly.core.mvi.UiEffect
import dev.section.streamly.core.mvi.UiIntent
import dev.section.streamly.core.mvi.UiState
import dev.section.streamly.livetv.domain.Channel

enum class PlayerErrorKind {
    /** The stream host can't be reached or the connection dropped. */
    Network,

    /** The device has no connection at all. */
    Offline,

    /** The stream answered but can't be played (bad status, unparseable, unsupported). */
    Source,

    /** The requested channel id isn't in the channel list. */
    NotFound,

    Unknown,
}

/** What the platform player reports. Play/pause is not a status: it is [PlayerState.playWhenReady]. */
sealed interface PlaybackStatus {
    data object Loading : PlaybackStatus
    data object Buffering : PlaybackStatus
    data object Ready : PlaybackStatus
    data class Error(val kind: PlayerErrorKind) : PlaybackStatus
}

data class PlayerState(
    val channel: Channel? = null,
    val status: PlaybackStatus = PlaybackStatus.Loading,
    val playWhenReady: Boolean = true,
    val controlsVisible: Boolean = true,
    val isOffline: Boolean = false,
    /** Bumped on Retry so the platform player re-prepares the same stream. */
    val retryToken: Int = 0,
    val canZap: Boolean = false,
) : UiState

sealed interface PlayerIntent : UiIntent {
    data object Retry : PlayerIntent
    data object TogglePlayPause : PlayerIntent
    data object ShowControls : PlayerIntent
    data object HideControls : PlayerIntent
    data object NextChannel : PlayerIntent
    data object PreviousChannel : PlayerIntent
    data object Back : PlayerIntent
    data class PlayerStatusChanged(val status: PlaybackStatus) : PlayerIntent
}

sealed interface PlayerEffect : UiEffect {
    data object NavigateBack : PlayerEffect
}
