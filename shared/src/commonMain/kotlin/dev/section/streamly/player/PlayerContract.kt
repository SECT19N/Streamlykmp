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

/** One selectable audio feed of the current stream. [id] is meaningful only to the platform player. */
data class AudioTrack(val id: String, val label: String, val isSelected: Boolean)

/**
 * Makes labels distinct: two feeds both tagged "English" become "English 1" and "English 2", and
 * a feed with no usable label becomes "Audio N".
 */
fun uniqueAudioLabels(raw: List<String?>): List<String> {
    val base = raw.mapIndexed { i, label -> label?.takeIf { it.isNotBlank() } ?: "Audio ${i + 1}" }
    return base.mapIndexed { i, label ->
        if (base.count { it == label } > 1) "$label ${base.take(i + 1).count { it == label }}" else label
    }
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
    /** Audio feeds the stream offers; empty until the player has read the stream's tracks. */
    val audioTracks: List<AudioTrack> = emptyList(),
    /** The track the user asked for, or null to keep the stream's default. */
    val requestedAudioId: String? = null,
) : UiState {
    val canSwitchAudio: Boolean get() = audioTracks.size > 1
    val currentAudio: AudioTrack? get() = audioTracks.firstOrNull { it.isSelected }
}

sealed interface PlayerIntent : UiIntent {
    data object Retry : PlayerIntent
    data object TogglePlayPause : PlayerIntent
    data object ShowControls : PlayerIntent
    data object HideControls : PlayerIntent
    data object NextChannel : PlayerIntent
    data object PreviousChannel : PlayerIntent
    data object Back : PlayerIntent
    data object CycleAudio : PlayerIntent
    data class AudioTracksChanged(val tracks: List<AudioTrack>) : PlayerIntent
    data class PlayerStatusChanged(val status: PlaybackStatus) : PlayerIntent
}

sealed interface PlayerEffect : UiEffect {
    data object NavigateBack : PlayerEffect
}
