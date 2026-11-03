package dev.section.streamly.search

import androidx.lifecycle.viewModelScope
import dev.section.streamly.core.mvi.MviViewModel
import dev.section.streamly.core.network.ConnectivityMonitor
import dev.section.streamly.search.data.SearchRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

internal const val SearchDebounceMillis = 400L

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: SearchRepository,
    connectivity: ConnectivityMonitor,
) : MviViewModel<SearchState, SearchIntent, SearchEffect>(SearchState()) {

    /** Trimmed query that drives the search; Retry re-emits it via [retryTick]. */
    private val activeQuery = MutableStateFlow("")
    private val retryTick = MutableStateFlow(0)
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> setState { copy(isOffline = !online) } }
        }
        viewModelScope.launch {
            activeQuery
                .debounce { if (it.isBlank()) 0L else SearchDebounceMillis }
                .distinctUntilChanged()
                .collectLatest { query -> runSearch(query) }
        }
        viewModelScope.launch {
            retryTick.drop(1).collectLatest { runSearch(activeQuery.value, refresh = true) }
        }
    }

    override fun handleIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> {
                setState { copy(query = intent.query) }
                activeQuery.value = intent.query.trim()
            }

            SearchIntent.Retry -> retryTick.value++
            SearchIntent.LoadMore -> loadMore()
            is SearchIntent.ItemClicked ->
                sendEffect(SearchEffect.OpenDetails(intent.id, intent.isMovie))
        }
    }

    private fun loadMore() {
        val current = state.value
        if (!current.canLoadMore || current.isLoadingMore || current.isLoading) return
        val query = activeQuery.value
        setState { copy(isLoadingMore = true) }
        loadMoreJob = viewModelScope.launch {
            repository.search(query, current.page + 1)
                .onSuccess { next ->
                    setState {
                        copy(
                            results = (results + next.items).distinctBy { it.isMovie to it.id },
                            page = next.page,
                            canLoadMore = next.canLoadMore,
                            isLoadingMore = false,
                        )
                    }
                }
                // A failed page keeps the results on screen; scrolling to the end again retries.
                .onFailure { setState { copy(isLoadingMore = false) } }
        }
    }

    private suspend fun runSearch(query: String, refresh: Boolean = false) {
        loadMoreJob?.cancel() // a page for the previous query must not land in these results
        if (query.isBlank()) {
            setState {
                copy(
                    isLoading = false,
                    results = emptyList(),
                    error = null,
                    hasSearched = false,
                    page = 1,
                    canLoadMore = false,
                    isLoadingMore = false,
                )
            }
            return
        }
        setState { copy(isLoading = true, error = null, isLoadingMore = false) }
        repository.search(query, page = 1, refresh = refresh)
            .onSuccess { first ->
                setState {
                    copy(
                        isLoading = false,
                        results = first.items,
                        hasSearched = true,
                        page = first.page,
                        canLoadMore = first.canLoadMore,
                    )
                }
            }
            .onFailure {
                setState {
                    copy(
                        isLoading = false,
                        results = emptyList(),
                        canLoadMore = false,
                        error = "The search couldn't be completed.",
                    )
                }
            }
    }
}
