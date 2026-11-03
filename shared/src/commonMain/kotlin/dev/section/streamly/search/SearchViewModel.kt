package dev.section.streamly.search

import androidx.lifecycle.viewModelScope
import dev.section.streamly.core.mvi.MviViewModel
import dev.section.streamly.core.network.ConnectivityMonitor
import dev.section.streamly.search.data.SearchRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
            retryTick.drop(1).collectLatest { runSearch(activeQuery.value) }
        }
    }

    override fun handleIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> {
                setState { copy(query = intent.query) }
                activeQuery.value = intent.query.trim()
            }

            SearchIntent.Retry -> retryTick.value++
            is SearchIntent.ItemClicked ->
                sendEffect(SearchEffect.OpenDetails(intent.id, intent.isMovie))
        }
    }

    private suspend fun runSearch(query: String) {
        if (query.isBlank()) {
            setState { copy(isLoading = false, results = emptyList(), error = null, hasSearched = false) }
            return
        }
        setState { copy(isLoading = true, error = null) }
        repository.search(query)
            .onSuccess { items ->
                setState { copy(isLoading = false, results = items, hasSearched = true) }
            }
            .onFailure {
                setState {
                    copy(
                        isLoading = false,
                        results = emptyList(),
                        error = "The search couldn't be completed.",
                    )
                }
            }
    }
}
