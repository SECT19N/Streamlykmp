package dev.section.streamly.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform video surface for a live HLS stream. It only plays and reports; all state lives in
 * [PlayerViewModel].
 *
 * @param playWhenReady whether playback should run (false = paused by the user).
 * @param retryToken changing it re-prepares the same [streamUrl].
 * @param requestedAudioId the [AudioTrack.id] to play, or null for the stream's default.
 * @param onAudioTracks the stream's audio feeds, reported whenever they or the selection change.
 */
@Composable
expect fun LivePlayerSurface(
    streamUrl: String,
    playWhenReady: Boolean,
    retryToken: Int,
    requestedAudioId: String?,
    onAudioTracks: (List<AudioTrack>) -> Unit,
    onStatus: (PlaybackStatus) -> Unit,
    modifier: Modifier = Modifier,
)

/** Hides the system bars while this composable is in the composition (no-op where N/A). */
@Composable
expect fun ImmersiveMode()
