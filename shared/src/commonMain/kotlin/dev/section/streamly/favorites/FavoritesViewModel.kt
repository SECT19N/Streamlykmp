package dev.section.streamly.favorites

import androidx.lifecycle.viewModelScope
import dev.section.streamly.core.mvi.MviViewModel
import dev.section.streamly.favorites.data.FavoritesRepository
import kotlinx.coroutines.launch

class FavoritesViewModel(
    repository: FavoritesRepository,
) : MviViewModel<FavoritesState, FavoritesIntent, FavoritesEffect>(
    // Local data is available immediately, so there is no loading state to show.
    FavoritesState(repository.favorites.value),
) {
    init {
        viewModelScope.launch {
            repository.favorites.collect { list -> setState { copy(favorites = list) } }
        }
    }

    override fun handleIntent(intent: FavoritesIntent) {
        when (intent) {
            is FavoritesIntent.ItemClicked ->
                sendEffect(FavoritesEffect.OpenDetails(intent.id, intent.isMovie))

            is FavoritesIntent.SelectTab -> setState { copy(tab = intent.tab) }
        }
    }
}
