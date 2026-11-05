@file:OptIn(ExperimentalComposeUiApi::class)

package dev.section.streamly.search

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.section.streamly.home.PosterCard
import dev.section.streamly.ui.components.EmptyView
import dev.section.streamly.ui.components.ErrorView
import dev.section.streamly.ui.components.LoadingView
import dev.section.streamly.ui.components.OfflineBanner
import dev.section.streamly.ui.components.focusRing
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import streamlykmp.shared.generated.resources.Res
import streamlykmp.shared.generated.resources.ic_search

/** Start fetching the next page when this many items from the end are visible. */
private const val LoadMoreThreshold = 8

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
        if (state.hasSearched && state.error == null) {
            FilterRow(state.filter) { onIntent(SearchIntent.SetFilter(it)) }
        }
        val visible = state.visibleResults
        when {
            state.error != null -> ErrorView(
                state.error,
                onRetry = { onIntent(SearchIntent.Retry) },
                // The user is typing; moving focus to Retry would cut the on-screen keyboard off.
                takeFocus = false,
            )

            state.isLoading -> LoadingView()
            state.query.isBlank() -> EmptyView("Search for movies and shows.")
            state.hasSearched && state.results.isEmpty() ->
                EmptyView("No results for \"${state.query.trim()}\".")

            // The filter hides everything loaded so far; the next page is already on its way.
            visible.isEmpty() && state.canLoadMore -> LoadingView()
            visible.isEmpty() && state.results.isNotEmpty() ->
                EmptyView("No ${state.filter.label.lowercase()} for \"${state.query.trim()}\".")

            visible.isEmpty() -> Unit // typing; the debounce hasn't fired yet
            else -> {
                val gridState = rememberLazyGridState()
                val nearEnd by remember(gridState) {
                    derivedStateOf {
                        val info = gridState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        info.totalItemsCount > 0 && last >= info.totalItemsCount - LoadMoreThreshold
                    }
                }
                LaunchedEffect(nearEnd, visible.size) {
                    if (nearEnd) onIntent(SearchIntent.LoadMore)
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(130.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                ) {
                    items(visible, key = { "${it.isMovie}-${it.id}" }) { item ->
                        PosterCard(item, onClick = {
                            onIntent(SearchIntent.ItemClicked(item.id, item.isMovie))
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchFilter.entries.forEach { filter ->
            val interaction = remember { MutableInteractionSource() }
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
                interactionSource = interaction,
                modifier = Modifier.focusRing(interaction, FilterChipDefaults.shape),
            )
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
    val focusManager = LocalFocusManager.current
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
                val interaction = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { onQueryChange("") },
                    interactionSource = interaction,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .focusRing(interaction, ButtonDefaults.textShape),
                ) { Text("Clear") }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboard?.hide()
                // Hand the D-pad to the results instead of leaving it stuck in the field.
                focusManager.moveFocus(FocusDirection.Down)
            },
        ),
    )
}
