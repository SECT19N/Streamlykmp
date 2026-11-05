@file:OptIn(ExperimentalComposeUiApi::class)

package dev.section.streamly.details

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch
import dev.section.streamly.details.domain.CastMember
import dev.section.streamly.details.domain.MediaDetails
import dev.section.streamly.details.domain.facts
import dev.section.streamly.home.PosterCard
import dev.section.streamly.ui.components.ErrorView
import dev.section.streamly.ui.components.LoadingView
import dev.section.streamly.ui.components.OfflineBanner
import dev.section.streamly.ui.components.focusRing
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import streamlykmp.shared.generated.resources.Res
import streamlykmp.shared.generated.resources.ic_arrow_back
import streamlykmp.shared.generated.resources.ic_favorite
import streamlykmp.shared.generated.resources.ic_favorite_filled

@Composable
fun DetailsScreen(
    id: Int,
    isMovie: Boolean,
    onBack: () -> Unit,
    onOpenDetails: (id: Int, isMovie: Boolean) -> Unit,
    viewModel: DetailsViewModel = koinViewModel(key = "details-$id-$isMovie") {
        parametersOf(id, isMovie)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                DetailsEffect.NavigateBack -> onBack()
                is DetailsEffect.OpenDetails -> onOpenDetails(effect.id, effect.isMovie)
            }
        }
    }

    DetailsContent(state, viewModel::onIntent)
}

@Composable
private fun DetailsContent(state: DetailsState, onIntent: (DetailsIntent) -> Unit) {
    val backFocus = remember { FocusRequester() }
    // Land D-pad users on Back as soon as the page is usable.
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) runCatching { backFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize()) {
        if (state.isOffline) {
            OfflineBanner(message = "You're offline. Details may be out of date.")
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> LoadingView()
                state.error != null -> ErrorView(
                    state.error,
                    onRetry = { onIntent(DetailsIntent.Retry) },
                )

                state.details != null -> DetailsBody(state.details, onIntent)
            }
            BackButton(
                onClick = { onIntent(DetailsIntent.BackClicked) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .focusRequester(backFocus),
            )
            // Saving needs the title and poster, so the heart waits for the details.
            if (state.details != null) {
                FavoriteButton(
                    isFavorite = state.isFavorite,
                    onToggle = { onIntent(DetailsIntent.ToggleFavorite) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            .focusRing(interaction, CircleShape),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_arrow_back),
            contentDescription = "Back",
            tint = Color.White,
        )
    }
}

@Composable
private fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    IconToggleButton(
        checked = isFavorite,
        onCheckedChange = { onToggle() },
        interactionSource = interaction,
        modifier = modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            .focusRing(interaction, CircleShape),
    ) {
        Icon(
            painter = painterResource(
                if (isFavorite) Res.drawable.ic_favorite_filled else Res.drawable.ic_favorite,
            ),
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            tint = Color.White,
        )
    }
}

@Composable
private fun DetailsBody(details: MediaDetails, onIntent: (DetailsIntent) -> Unit) {
    val scrollState = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
        Backdrop(details)
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                details.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            details.tagline?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            metaLine(details)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        // "★" is read as "black star"; say what it means instead.
                        details.rating?.let { r ->
                            contentDescription =
                                it.replace("★ ${oneDecimal(r)}", "Rated ${oneDecimal(r)} out of 10")
                        }
                    },
                )
            }
            if (details.genres.isNotEmpty()) {
                Text(
                    details.genres.joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Overview(details.overview, scrollState)
            FactsSection(details)
            if (details.cast.isNotEmpty()) {
                Text(
                    "Cast",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
            }
        }
        if (details.cast.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.focusRestorer(),
            ) {
                itemsIndexed(details.cast) { _, member -> CastCard(member) }
            }
        }
        details.collection?.let { collection ->
            Text(
                "Part of ${collection.name}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp).semantics { heading() },
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.focusRestorer(),
            ) {
                items(collection.parts, key = { it.id }) { part ->
                    PosterCard(part, onClick = { onIntent(DetailsIntent.OpenItem(part.id, part.isMovie)) })
                }
            }
        }
        Box(Modifier.size(24.dp))
    }
}

@Composable
private fun FactsSection(details: MediaDetails) {
    val facts = details.facts()
    if (facts.isEmpty()) return
    Text(
        "Details",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
    facts.forEach { fact ->
        // One announcement per row ("Budget, $63M") instead of two unrelated texts.
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
            Text(
                fact.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(fact.value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Focusable so a D-pad can land on it and scroll the page. */
@Composable
private fun Overview(overview: String?, scrollState: ScrollState) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(8.dp)
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { DpadScrollStep.toPx() }
    Text(
        overview ?: "No overview available.",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(interaction, shape)
            // A D-pad can't drag the page, so while there is more page in that direction, Up and
            // Down scroll it; at the end they fall through and move focus as usual.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.key == Key.DirectionDown && scrollState.canScrollForward -> {
                        scope.launch { scrollState.animateScrollBy(stepPx) }
                        true
                    }

                    event.key == Key.DirectionUp && scrollState.canScrollBackward -> {
                        scope.launch { scrollState.animateScrollBy(-stepPx) }
                        true
                    }

                    else -> false
                }
            }
            .focusable(interactionSource = interaction)
            .padding(4.dp),
    )
}

/** How far one D-pad press scrolls the Details page. */
private val DpadScrollStep = 200.dp

@Composable
private fun Backdrop(details: MediaDetails) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val url = details.backdropUrl() ?: details.posterUrl()
        if (url != null) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null, // decorative; the title is the heading above
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {},
                error = {},
            )
        }
    }
}

@Composable
private fun CastCard(member: CastMember) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(96.dp)
            .focusRing(interaction, shape)
            .focusable(interactionSource = interaction)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val url = member.profileUrl()
            if (url == null) {
                CastInitial(member.name)
            } else {
                SubcomposeAsyncImage(
                    model = url,
                    contentDescription = null, // the name below is part of the merged card
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { CastInitial(member.name) },
                    error = { CastInitial(member.name) },
                )
            }
        }
        Text(
            member.name,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            minLines = 2, // fixed height, so the cast row doesn't resize while scrolling
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        // Always present, even when empty, so every cast card is the same height.
        Text(
            member.character.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CastInitial(name: String) {
    Text(
        name.firstOrNull()?.uppercase().orEmpty(),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun metaLine(details: MediaDetails): String? {
    val parts = buildList {
        details.certification?.let(::add)
        details.year?.let(::add)
        details.runtimeMinutes?.let { add(formatRuntime(it)) }
        details.rating?.let { add("★ ${oneDecimal(it)}") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}

internal fun formatRuntime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

internal fun oneDecimal(value: Double): String {
    val tenths = (value * 10).toInt()
    return "${tenths / 10}.${tenths % 10}"
}
