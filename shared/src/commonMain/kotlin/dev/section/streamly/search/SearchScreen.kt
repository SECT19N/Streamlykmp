package dev.section.streamly.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.section.streamly.home.PosterCard
import dev.section.streamly.ui.components.EmptyView
import dev.section.streamly.ui.components.ErrorView
import dev.section.streamly.ui.components.LoadingView
import dev.section.streamly.ui.components.OfflineBanner
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import streamlykmp.shared.generated.resources.Res
import streamlykmp.shared.generated.resources.ic_search

@Composable
fun SearchScreen(
    onOpenDetails: (id: Int, isMovie: Boolean) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchEffect.OpenDetails -> onOpenDetails(effect.id, effect.isMovie)
            }
        }
    }

    SearchContent(state, viewModel::onIntent)
}

@Composable
private fun SearchContent(state: SearchState, onIntent: (SearchIntent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (state.isOffline) {
            OfflineBanner(message = "You're offline. Search needs a connection.")
        }
        SearchField(
            query = state.query,
            onQueryChange = { onIntent(SearchIntent.QueryChanged(it)) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        when {
            state.error != null -> ErrorView(
                state.error,
                onRetry = { onIntent(SearchIntent.Retry) },
            )

            state.isLoading -> LoadingView()
            state.query.isBlank() -> EmptyView("Search for movies and shows.")
            state.hasSearched && state.results.isEmpty() ->
                EmptyView("No results for \"${state.query.trim()}\".")

            state.results.isEmpty() -> Unit // typing; the debounce hasn't fired yet
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(130.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.results, key = { "${it.isMovie}-${it.id}" }) { item ->
                    PosterCard(item, onClick = {
                        onIntent(SearchIntent.ItemClicked(item.id, item.isMovie))
                    })
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        label = { Text("Search movies and shows") },
        leadingIcon = {
            Icon(painterResource(Res.drawable.ic_search), contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                TextButton(onClick = { onQueryChange("") }) { Text("Clear") }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
    )
}
