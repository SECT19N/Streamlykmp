package dev.section.streamly.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.section.streamly.ui.components.EmptyView
import dev.section.streamly.ui.components.ErrorView
import dev.section.streamly.ui.components.LoadingView
import dev.section.streamly.ui.components.OfflineBanner
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onOpenDetails: (id: Int, isMovie: Boolean) -> Unit,
    onOpenAbout: () -> Unit,
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

    HomeContent(state, viewModel::onIntent, onOpenAbout)
}

@Composable
private fun HomeContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onOpenAbout) { Text("About") }
        }
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
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(row.items, key = { it.id }) { item ->
                            PosterCard(item, onClick = {
                                onIntent(HomeIntent.ItemClicked(item.id, item.isMovie))
                            })
                        }
                    }
                }
            }
        }
    }
}
