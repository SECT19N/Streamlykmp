package dev.section.streamly.home

import androidx.lifecycle.viewModelScope
import dev.section.streamly.core.mvi.MviViewModel
import dev.section.streamly.core.network.ConnectivityMonitor
import dev.section.streamly.home.data.HomeRepository
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: HomeRepository,
    connectivity: ConnectivityMonitor,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> setState { copy(isOffline = !online) } }
        }
        load()
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Retry -> load()
            is HomeIntent.ItemClicked -> sendEffect(HomeEffect.OpenDetails(intent.id, intent.isMovie))
        }
    }

    private fun load() {
        setState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getRows()
                .onSuccess { rows -> setState { copy(isLoading = false, rows = rows) } }
                .onFailure {
                    setState {
                        copy(isLoading = false, error = "The movies and shows couldn't be loaded.")
                    }
                }
        }
    }
}
