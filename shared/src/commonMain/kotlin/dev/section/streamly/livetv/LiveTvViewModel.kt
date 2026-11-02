package dev.section.streamly.livetv

import androidx.lifecycle.viewModelScope
import dev.section.streamly.core.mvi.MviViewModel
import dev.section.streamly.core.network.ConnectivityMonitor
import dev.section.streamly.livetv.data.ChannelRepository
import kotlinx.coroutines.launch

class LiveTvViewModel(
    private val repository: ChannelRepository,
    connectivity: ConnectivityMonitor,
) : MviViewModel<LiveTvState, LiveTvIntent, LiveTvEffect>(LiveTvState()) {

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> setState { copy(isOffline = !online) } }
        }
        load()
    }

    override fun handleIntent(intent: LiveTvIntent) {
        when (intent) {
            LiveTvIntent.Retry -> load()
            is LiveTvIntent.SelectCategory -> setState { copy(selectedCategory = intent.category) }
            is LiveTvIntent.ChannelClicked -> sendEffect(LiveTvEffect.OpenPlayer(intent.channelId))
        }
    }

    private fun load() {
        setState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getChannels()
                .onSuccess { channels ->
                    setState {
                        copy(
                            isLoading = false,
                            channels = channels,
                            // Drop a filter that no longer matches any channel.
                            selectedCategory = selectedCategory?.takeIf { sel ->
                                channels.any { it.category == sel }
                            },
                        )
                    }
                }
                .onFailure {
                    setState { copy(isLoading = false, error = "The channel list couldn't be loaded.") }
                }
        }
    }
}
