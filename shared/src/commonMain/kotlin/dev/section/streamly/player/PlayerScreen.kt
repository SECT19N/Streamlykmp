package dev.section.streamly.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.section.streamly.ui.components.OfflineBanner
import dev.section.streamly.ui.components.focusRing
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import streamlykmp.shared.generated.resources.Res
import streamlykmp.shared.generated.resources.ic_arrow_back
import streamlykmp.shared.generated.resources.ic_pause
import streamlykmp.shared.generated.resources.ic_play_arrow
import streamlykmp.shared.generated.resources.ic_skip_next
import streamlykmp.shared.generated.resources.ic_skip_previous

@Composable
fun PlayerScreen(
    channelId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = koinViewModel(key = channelId) { parametersOf(channelId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                PlayerEffect.NavigateBack -> onBack()
            }
        }
    }

    ImmersiveMode()
    PlayerContent(state, viewModel::onIntent)
}

@Composable
private fun PlayerContent(state: PlayerState, onIntent: (PlayerIntent) -> Unit) {
    val rootFocus = remember { FocusRequester() }
    val primaryFocus = remember { FocusRequester() }
    val isError = state.status is PlaybackStatus.Error
    val controlsShown = state.controlsVisible || isError

    // Controls on screen: focus their primary button. Hidden: the root catches the remote's keys.
    LaunchedEffect(controlsShown) {
        // primaryFocus isn't attached when there is nothing to focus (e.g. channel not found).
        val focused =
            controlsShown && runCatching { primaryFocus.requestFocus() }.getOrDefault(false)
        if (!focused) rootFocus.requestFocus()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                handleKey(event.key, controlsShown, onIntent)
            }
            .pointerInput(controlsShown) {
                detectTapGestures {
                    onIntent(if (controlsShown) PlayerIntent.HideControls else PlayerIntent.ShowControls)
                }
            },
    ) {
        state.channel?.let { channel ->
            LivePlayerSurface(
                streamUrl = channel.streamUrl,
                playWhenReady = state.playWhenReady,
                retryToken = state.retryToken,
                requestedAudioId = state.requestedAudioId,
                onAudioTracks = { onIntent(PlayerIntent.AudioTracksChanged(it)) },
                onStatus = { onIntent(PlayerIntent.PlayerStatusChanged(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        when (val status = state.status) {
            PlaybackStatus.Loading, PlaybackStatus.Buffering -> BufferingIndicator(
                Modifier.align(Alignment.Center),
            )

            is PlaybackStatus.Error -> ErrorPanel(
                kind = status.kind,
                onRetry = { onIntent(PlayerIntent.Retry) },
                retryFocus = primaryFocus,
                modifier = Modifier.align(Alignment.Center),
            )

            PlaybackStatus.Ready -> Unit
        }

        AnimatedVisibility(
            visible = controlsShown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            ControlsOverlay(state, primaryFocus, onIntent, showCenterControls = !isError)
        }

        if (state.isOffline) {
            OfflineBanner(Modifier.align(Alignment.TopCenter))
        }
    }
}

/** Returns true if the key was consumed. */
private fun handleKey(key: Key, controlsShown: Boolean, onIntent: (PlayerIntent) -> Unit): Boolean =
    when (key) {
        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
            onIntent(PlayerIntent.TogglePlayPause); true
        }

        Key.MediaNext, Key.ChannelUp -> {
            onIntent(PlayerIntent.NextChannel); true
        }

        Key.MediaPrevious, Key.ChannelDown -> {
            onIntent(PlayerIntent.PreviousChannel); true
        }

        // With the controls hidden the whole remote acts on playback directly.
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (controlsShown) {
            false // the focused control handles it
        } else {
            onIntent(PlayerIntent.TogglePlayPause); true
        }

        Key.DirectionRight -> if (controlsShown) {
            onIntent(PlayerIntent.ShowControls); false // let focus move between buttons
        } else {
            onIntent(PlayerIntent.NextChannel); true
        }

        Key.DirectionLeft -> if (controlsShown) {
            onIntent(PlayerIntent.ShowControls); false
        } else {
            onIntent(PlayerIntent.PreviousChannel); true
        }

        Key.DirectionUp, Key.DirectionDown -> {
            onIntent(PlayerIntent.ShowControls); !controlsShown
        }

        // Back must keep working through the system's back handling.
        Key.Back, Key.Escape -> false
        else -> {
            onIntent(PlayerIntent.ShowControls); false
        }
    }

@Composable
private fun BufferingIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.semantics {
            contentDescription = "Buffering"
            liveRegion = LiveRegionMode.Polite
        },
        color = Color.White,
    )
}

@Composable
private fun ControlsOverlay(
    state: PlayerState,
    primaryFocus: FocusRequester,
    onIntent: (PlayerIntent) -> Unit,
    showCenterControls: Boolean,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ControlButton(
                icon = Res.drawable.ic_arrow_back,
                description = "Back",
                onClick = { onIntent(PlayerIntent.Back) },
            )
            Text(
                state.channel?.name.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (showCenterControls && state.canSwitchAudio) {
                AudioButton(state.currentAudio, onClick = { onIntent(PlayerIntent.CycleAudio) })
            }
            if (showCenterControls) LiveBadge()
        }

        if (showCenterControls) {
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (state.canZap) {
                    ControlButton(
                        icon = Res.drawable.ic_skip_previous,
                        description = "Previous channel",
                        onClick = { onIntent(PlayerIntent.PreviousChannel) },
                    )
                }
                ControlButton(
                    icon = if (state.playWhenReady) Res.drawable.ic_pause else Res.drawable.ic_play_arrow,
                    description = if (state.playWhenReady) "Pause" else "Play",
                    onClick = { onIntent(PlayerIntent.TogglePlayPause) },
                    size = 72.dp,
                    modifier = Modifier.focusRequester(primaryFocus),
                )
                if (state.canZap) {
                    ControlButton(
                        icon = Res.drawable.ic_skip_next,
                        description = "Next channel",
                        onClick = { onIntent(PlayerIntent.NextChannel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: DrawableResource,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 56.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .size(size)
            .focusRing(interaction, CircleShape),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

/** Cycles through the stream's audio feeds; with two feeds that is a plain toggle. */
@Composable
private fun AudioButton(current: AudioTrack?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val label = current?.label ?: "Audio"
    Button(
        onClick = onClick,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
        ),
        modifier = Modifier
            .focusRing(interaction, ButtonDefaults.shape)
            .semantics { contentDescription = "Audio track: $label. Double tap to switch." },
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LiveBadge() {
    Surface(
        color = Color(0xFFD32F2F),
        shape = CircleShape,
        modifier = Modifier.semantics { contentDescription = "Live broadcast" },
    ) {
        Text(
            "LIVE",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ErrorPanel(
    kind: PlayerErrorKind,
    onRetry: () -> Unit,
    retryFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Playback error",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            errorMessage(kind),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (kind != PlayerErrorKind.NotFound) {
            val interaction = remember { MutableInteractionSource() }
            Button(
                onClick = onRetry,
                interactionSource = interaction,
                modifier = Modifier
                    .focusRequester(retryFocus)
                    .focusRing(interaction, ButtonDefaults.shape),
            ) {
                Text("Retry")
            }
        }
    }
}

internal fun errorMessage(kind: PlayerErrorKind): String = when (kind) {
    PlayerErrorKind.Network -> "Can't reach the stream. Check your connection and try again."
    PlayerErrorKind.Offline -> "You're offline. Playback will resume when you reconnect."
    PlayerErrorKind.Source -> "This channel isn't available right now."
    PlayerErrorKind.NotFound -> "This channel couldn't be found."
    PlayerErrorKind.Unknown -> "Something went wrong while playing this channel."
}
