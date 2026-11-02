package dev.section.streamly.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform video surface for a live HLS stream. It only plays and reports; all state lives in
 * [PlayerViewModel].
 *
 * @param playWhenReady whether playback should run (false = paused by the user).
 * @param retryToken changing it re-prepares the same [streamUrl].
 */
@Composable
expect fun LivePlayerSurface(
    streamUrl: String,
    playWhenReady: Boolean,
    retryToken: Int,
    onStatus: (PlaybackStatus) -> Unit,
    modifier: Modifier = Modifier,
)

/** Hides the system bars while this composable is in the composition (no-op where N/A). */
@Composable
expect fun ImmersiveMode()
