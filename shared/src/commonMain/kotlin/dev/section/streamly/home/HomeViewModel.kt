package dev.section.streamly.home

import androidx.lifecycle.viewModelScope
import dev.section.streamly.core.mvi.MviViewModel
import dev.section.streamly.core.network.ConnectivityMonitor
import dev.section.streamly.home.data.HomeRepository
import dev.section.streamly.home.domain.HomeRowType
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
            HomeIntent.Retry -> load(refresh = true)
            is HomeIntent.LoadMore -> loadMore(intent.type)
            is HomeIntent.ItemClicked -> sendEffect(
                HomeEffect.OpenDetails(
                    intent.id,
                    intent.isMovie
                )
            )
        }
    }

    private fun load(refresh: Boolean = false) {
        setState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getRows(refresh)
                .onSuccess { rows -> setState { copy(isLoading = false, rows = rows) } }
                .onFailure {
                    setState {
                        copy(isLoading = false, error = "The movies and shows couldn't be loaded.")
                    }
                }
        }
    }

    private fun loadMore(type: HomeRowType) {
        val row = state.value.rows.firstOrNull { it.type == type } ?: return
        if (!row.canLoadMore || type in state.value.loadingMore) return
        setState { copy(loadingMore = loadingMore + type) }
        viewModelScope.launch {
            repository.loadMore(row)
                .onSuccess { updated ->
                    setState {
                        copy(
                            rows = rows.map { if (it.type == type) updated else it },
                            loadingMore = loadingMore - type,
                        )
                    }
                }
                // A failed page keeps what is on screen; scrolling to the end again retries.
                .onFailure { setState { copy(loadingMore = loadingMore - type) } }
        }
    }
}
