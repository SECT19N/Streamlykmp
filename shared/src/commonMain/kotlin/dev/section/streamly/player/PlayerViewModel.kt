package dev.section.streamly.player

import androidx.lifecycle.viewModelScope
import dev.section.streamly.core.mvi.MviViewModel
import dev.section.streamly.core.network.ConnectivityMonitor
import dev.section.streamly.livetv.data.ChannelRepository
import dev.section.streamly.livetv.domain.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val channelId: String,
    private val repository: ChannelRepository,
    connectivity: ConnectivityMonitor,
) : MviViewModel<PlayerState, PlayerIntent, PlayerEffect>(PlayerState()) {

    private var channels: List<Channel> = emptyList()
    private var hideJob: Job? = null

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online ->
                val wasOffline = state.value.isOffline
                setState { copy(isOffline = !online) }
                // Back online while stuck on a failure: try again without making the user ask.
                if (online && wasOffline && state.value.status is PlaybackStatus.Error) retry()
            }
        }
        loadChannel()
    }

    override fun handleIntent(intent: PlayerIntent) {
        when (intent) {
            PlayerIntent.Retry -> retry()
            PlayerIntent.TogglePlayPause -> togglePlayPause()
            PlayerIntent.ShowControls -> showControls()
            PlayerIntent.HideControls -> hideControls()
            PlayerIntent.NextChannel -> zap(+1)
            PlayerIntent.PreviousChannel -> zap(-1)
            PlayerIntent.Back -> sendEffect(PlayerEffect.NavigateBack)
            PlayerIntent.CycleAudio -> cycleAudio()
            is PlayerIntent.AudioTracksChanged -> setState { copy(audioTracks = intent.tracks) }
            is PlayerIntent.PlayerStatusChanged -> onStatusChanged(intent.status)
        }
    }

    private fun loadChannel() {
        viewModelScope.launch {
            repository.getChannels()
                .onSuccess { list ->
                    channels = list
                    val channel = list.firstOrNull { it.id == channelId }
                    if (channel == null) {
                        setState { copy(status = PlaybackStatus.Error(PlayerErrorKind.NotFound)) }
                    } else {
                        setState { copy(channel = channel, canZap = list.size > 1) }
                        scheduleHide()
                    }
                }
                .onFailure {
                    setState { copy(status = PlaybackStatus.Error(PlayerErrorKind.Unknown)) }
                }
        }
    }

    private fun retry() {
        val channel = state.value.channel
        if (channel == null) {
            // The channel list itself never loaded; start over.
            setState { copy(status = PlaybackStatus.Loading) }
            loadChannel()
            return
        }
        setState {
            copy(status = PlaybackStatus.Loading, retryToken = retryToken + 1, playWhenReady = true)
        }
        showControls()
    }

    /** Switches to the next audio feed, wrapping around. */
    private fun cycleAudio() {
        val tracks = state.value.audioTracks
        if (tracks.size < 2) return
        val current = tracks.indexOfFirst { it.isSelected }
        val next = tracks[(current + 1).mod(tracks.size)]
        setState { copy(requestedAudioId = next.id) }
        showControls()
    }

    private fun togglePlayPause() {
        setState { copy(playWhenReady = !playWhenReady) }
        showControls()
    }

    private fun zap(step: Int) {
        if (channels.size < 2) return
        val current = channels.indexOfFirst { it.id == state.value.channel?.id }
        val next = channels[(current + step).mod(channels.size)]
        setState {
            // Track ids belong to the old stream, so the choice and the list start over.
            copy(
                channel = next,
                status = PlaybackStatus.Loading,
                playWhenReady = true,
                audioTracks = emptyList(),
                requestedAudioId = null,
            )
        }
        showControls()
    }

    private fun onStatusChanged(status: PlaybackStatus) {
        // A stream error while the device is offline is really an offline problem.
        val resolved = if (status is PlaybackStatus.Error &&
            status.kind == PlayerErrorKind.Network && state.value.isOffline
        ) {
            PlaybackStatus.Error(PlayerErrorKind.Offline)
        } else {
            status
        }
        setState { copy(status = resolved) }
        when (resolved) {
            is PlaybackStatus.Error -> showControls()
            PlaybackStatus.Ready -> scheduleHide()
            else -> Unit
        }
    }

    private fun showControls() {
        setState { copy(controlsVisible = true) }
        scheduleHide()
    }

    private fun hideControls() {
        hideJob?.cancel()
        if (canAutoHide()) setState { copy(controlsVisible = false) }
    }

    /** Controls stay up while paused, loading or failed; they only hide during healthy playback. */
    private fun canAutoHide(): Boolean = state.value.let {
        it.status == PlaybackStatus.Ready && it.playWhenReady
    }

    private fun scheduleHide() {
        hideJob?.cancel()
        hideJob = viewModelScope.launch {
            delay(CONTROLS_TIMEOUT_MS)
            if (canAutoHide()) setState { copy(controlsVisible = false) }
        }
    }

    private companion object {
        const val CONTROLS_TIMEOUT_MS = 4_000L
    }
}
