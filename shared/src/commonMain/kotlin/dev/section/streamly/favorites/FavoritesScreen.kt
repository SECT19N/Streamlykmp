@file:OptIn(ExperimentalComposeUiApi::class)

package dev.section.streamly.favorites

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.section.streamly.home.PosterCard
import dev.section.streamly.ui.components.EmptyView
import dev.section.streamly.ui.components.focusRing
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FavoritesScreen(
    onOpenDetails: (id: Int, isMovie: Boolean) -> Unit,
    viewModel: FavoritesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FavoritesEffect.OpenDetails -> onOpenDetails(effect.id, effect.isMovie)
            }
        }
    }

    FavoritesContent(state, viewModel::onIntent)
}

@Composable
private fun FavoritesContent(state: FavoritesState, onIntent: (FavoritesIntent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TabRow(
            selected = state.tab,
            onSelect = { onIntent(FavoritesIntent.SelectTab(it)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val visible = state.visible
        if (visible.isEmpty()) {
            EmptyView(
                if (state.tab.isMovie) "No favorite movies yet. Open a movie and tap the heart."
                else "No favorite shows yet. Open a show and tap the heart.",
            )
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(130.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().focusRestorer(),
        ) {
            items(visible, key = { "${it.isMovie}-${it.id}" }) { item ->
                PosterCard(
                    item,
                    onClick = { onIntent(FavoritesIntent.ItemClicked(item.id, item.isMovie)) },
                )
            }
        }
    }
}

/** A connected button group: exactly one of Movies / TV Shows is selected. */
@Composable
private fun TabRow(
    selected: FavoritesTab,
    onSelect: (FavoritesTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = FavoritesTab.entries
    SingleChoiceSegmentedButtonRow(modifier) {
        tabs.forEachIndexed { index, tab ->
            val interaction = remember { MutableInteractionSource() }
            val shape = SegmentedButtonDefaults.itemShape(index, tabs.size)
            SegmentedButton(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                shape = shape,
                interactionSource = interaction,
                // The filled container already marks the selection; skip the default check icon.
                icon = {},
                modifier = Modifier.focusRing(interaction, shape),
            ) { Text(tab.label) }
        }
    }
}
