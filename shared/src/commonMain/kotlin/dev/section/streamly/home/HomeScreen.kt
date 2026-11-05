@file:OptIn(ExperimentalComposeUiApi::class)

package dev.section.streamly.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.section.streamly.ui.components.EmptyView
import dev.section.streamly.ui.components.ErrorView
import dev.section.streamly.ui.components.LoadingView
import dev.section.streamly.ui.components.OfflineBanner
import org.koin.compose.viewmodel.koinViewModel

/** Start fetching the next page when this many items from the end of a row are visible. */
private const val LoadMoreThreshold = 4

@Composable
fun HomeScreen(
    onOpenDetails: (id: Int, isMovie: Boolean) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.OpenDetails -> onOpenDetails(effect.id, effect.isMovie)
            }
        }
    }

    HomeContent(state, viewModel::onIntent)
}

@Composable
private fun HomeContent(state: HomeState, onIntent: (HomeIntent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (state.isOffline) {
            OfflineBanner(message = "You're offline. Movies and shows may be out of date.")
        }
        when {
            state.isLoading -> LoadingView()
            state.error != null -> ErrorView(state.error, onRetry = { onIntent(HomeIntent.Retry) })
            state.rows.isEmpty() -> EmptyView("No movies or shows available.")
            else -> LazyColumn(
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(state.rows, key = { it.type }) { row ->
                    Text(
                        row.type.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .semantics { heading() },
                    )
                    val firstCard = remember { FocusRequester() }
                    val isFirstRow = row == state.rows.first()
                    if (isFirstRow) {
                        // Without this a D-pad user lands on nothing after the rows load.
                        LaunchedEffect(Unit) { runCatching { firstCard.requestFocus() } }
                    }
                    val listState = rememberLazyListState()
                    val nearEnd by remember(listState) {
                        derivedStateOf {
                            val info = listState.layoutInfo
                            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            info.totalItemsCount > 0 && last >= info.totalItemsCount - LoadMoreThreshold
                        }
                    }
                    LaunchedEffect(nearEnd, row.items.size) {
                        if (nearEnd) onIntent(HomeIntent.LoadMore(row.type))
                    }
                    LazyRow(
                        state = listState,
                        // Headroom so the focus ring isn't clipped at the row's edges.
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.focusRestorer(firstCard),
                    ) {
                        itemsIndexed(row.items, key = { _, item -> item.id }) { index, item ->
                            PosterCard(
                                item,
                                onClick = {
                                    onIntent(
                                        HomeIntent.ItemClicked(
                                            item.id,
                                            item.isMovie
                                        )
                                    )
                                },
                                modifier = if (index == 0) Modifier.focusRequester(firstCard) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}
