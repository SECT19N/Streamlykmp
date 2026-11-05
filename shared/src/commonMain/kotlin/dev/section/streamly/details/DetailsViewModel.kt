package dev.section.streamly.details

import androidx.lifecycle.viewModelScope
import dev.section.streamly.core.mvi.MviViewModel
import dev.section.streamly.core.network.ConnectivityMonitor
import dev.section.streamly.details.data.DetailsRepository
import dev.section.streamly.favorites.data.FavoritesRepository
import dev.section.streamly.home.domain.MediaItem
import kotlinx.coroutines.launch

class DetailsViewModel(
    private val id: Int,
    private val isMovie: Boolean,
    private val repository: DetailsRepository,
    private val favorites: FavoritesRepository,
    connectivity: ConnectivityMonitor,
) : MviViewModel<DetailsState, DetailsIntent, DetailsEffect>(DetailsState()) {

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> setState { copy(isOffline = !online) } }
        }
        viewModelScope.launch {
            favorites.isFavorite(id, isMovie).collect { saved -> setState { copy(isFavorite = saved) } }
        }
        load()
    }

    override fun handleIntent(intent: DetailsIntent) {
        when (intent) {
            DetailsIntent.Retry -> load(refresh = true)
            DetailsIntent.BackClicked -> sendEffect(DetailsEffect.NavigateBack)
            DetailsIntent.ToggleFavorite -> state.value.details?.let { d ->
                favorites.toggle(MediaItem(d.id, d.title, d.posterPath, d.isMovie))
            }
        }
    }

    private fun load(refresh: Boolean = false) {
        setState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getDetails(id, isMovie, refresh)
                .onSuccess { details -> setState { copy(isLoading = false, details = details) } }
                .onFailure {
                    setState {
                        copy(isLoading = false, error = "The details couldn't be loaded.")
                    }
                }
        }
    }
}
