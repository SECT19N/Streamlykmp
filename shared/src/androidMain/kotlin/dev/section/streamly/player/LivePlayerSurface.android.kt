package dev.section.streamly.player

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import dev.section.streamly.player.audio.StreamlyRenderersFactory

@OptIn(UnstableApi::class)
@Composable
actual fun LivePlayerSurface(
    streamUrl: String,
    playWhenReady: Boolean,
    retryToken: Int,
    onStatus: (PlaybackStatus) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val latestOnStatus by rememberUpdatedState(onStatus)
    val latestPlayWhenReady by rememberUpdatedState(playWhenReady)
    val player = remember { ExoPlayer.Builder(context, StreamlyRenderersFactory(context)).build() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> latestOnStatus(PlaybackStatus.Buffering)
                    Player.STATE_READY -> latestOnStatus(PlaybackStatus.Ready)
                    // IDLE follows an error (already reported); ENDED doesn't happen on live.
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    // Standard live-HLS recovery: we fell behind the live window, rejoin the edge.
                    player.seekToDefaultPosition()
                    player.prepare()
                } else {
                    latestOnStatus(PlaybackStatus.Error(errorKindFor(error.errorCode)))
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, streamUrl, retryToken) {
        // Some playlists (Rojava HD) declare only the video codec in CODECS. Chunkless preparation
        // trusts that and plays them without audio, so let ExoPlayer read the tracks from the stream.
        val source = HlsMediaSource.Factory(DefaultDataSource.Factory(context))
            .setAllowChunklessPreparation(false)
            .createMediaSource(MediaItem.fromUri(streamUrl))
        player.setMediaSource(source)
        player.playWhenReady = latestPlayWhenReady
        player.prepare()
    }

    SideEffect {
        if (player.playWhenReady != playWhenReady) player.playWhenReady = playWhenReady
    }

    // Don't keep playing (or decoding) in the background; resume if the user hadn't paused.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (latestPlayWhenReady) player.play()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                keepScreenOn = true
                // Key events are handled by the Compose layer above.
                isFocusable = false
                isFocusableInTouchMode = false
                this.player = player
            }
        },
    )
}

/** Maps a Media3 error code to a coarse, user-presentable kind. */
internal fun errorKindFor(errorCode: Int): PlayerErrorKind = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> PlayerErrorKind.Network

    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        -> PlayerErrorKind.Source

    else -> PlayerErrorKind.Unknown
}

@Composable
actual fun ImmersiveMode() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
