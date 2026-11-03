@file:OptIn(ExperimentalComposeUiApi::class)

package dev.section.streamly.livetv

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.section.streamly.livetv.domain.Channel
import dev.section.streamly.ui.components.EmptyView
import dev.section.streamly.ui.components.ErrorView
import dev.section.streamly.ui.components.LoadingView
import dev.section.streamly.ui.components.OfflineBanner
import dev.section.streamly.ui.components.clickableCard
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LiveTvScreen(
    onOpenPlayer: (channelId: String) -> Unit,
    viewModel: LiveTvViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LiveTvEffect.OpenPlayer -> onOpenPlayer(effect.channelId)
            }
        }
    }

    LiveTvContent(state, viewModel::onIntent)
}

@Composable
private fun LiveTvContent(state: LiveTvState, onIntent: (LiveTvIntent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (state.isOffline) OfflineBanner()
        when {
            state.isLoading -> LoadingView()
            state.error != null -> ErrorView(
                state.error,
                onRetry = { onIntent(LiveTvIntent.Retry) })

            state.channels.isEmpty() -> EmptyView("No channels available.")
            else -> {
                CategoryChips(state.categories, state.selectedCategory) {
                    onIntent(LiveTvIntent.SelectCategory(it))
                }
                ChannelGrid(state.visibleChannels, state.selectedCategory) {
                    onIntent(LiveTvIntent.ChannelClicked(it.id))
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
            )
        }
        items(categories) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category) },
            )
        }
    }
}

@Composable
private fun ChannelGrid(
    channels: List<Channel>,
    selectedCategory: String?,
    onClick: (Channel) -> Unit,
) {
    val gridState = rememberLazyGridState()
    // A new filter shows a different list, so start it from the top.
    LaunchedEffect(selectedCategory) { gridState.scrollToItem(0) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().focusRestorer(),
    ) {
        items(channels, key = { it.id }) { channel ->
            ChannelCard(channel, onClick = { onClick(channel) })
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    val shape = CardDefaults.shape
    Card(
        Modifier
            .fillMaxWidth()
            .clickableCard(onClick = onClick, shape = shape, onClickLabel = "Watch ${channel.name}"),
    ) {
        Column(Modifier.padding(12.dp)) {
            ChannelLogo(channel, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Text(
                channel.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                channel.category,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
