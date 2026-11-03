package dev.section.streamly.details

import androidx.lifecycle.viewModelScope
import dev.section.streamly.core.mvi.MviViewModel
import dev.section.streamly.core.network.ConnectivityMonitor
import dev.section.streamly.details.data.DetailsRepository
import kotlinx.coroutines.launch

class DetailsViewModel(
    private val id: Int,
    private val isMovie: Boolean,
    private val repository: DetailsRepository,
    connectivity: ConnectivityMonitor,
) : MviViewModel<DetailsState, DetailsIntent, DetailsEffect>(DetailsState()) {

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> setState { copy(isOffline = !online) } }
        }
        load()
    }

    override fun handleIntent(intent: DetailsIntent) {
        when (intent) {
            DetailsIntent.Retry -> load()
            DetailsIntent.BackClicked -> sendEffect(DetailsEffect.NavigateBack)
        }
    }

    private fun load() {
        setState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getDetails(id, isMovie)
                .onSuccess { details -> setState { copy(isLoading = false, details = details) } }
                .onFailure {
                    setState {
                        copy(isLoading = false, error = "The details couldn't be loaded.")
                    }
                }
        }
    }
}
