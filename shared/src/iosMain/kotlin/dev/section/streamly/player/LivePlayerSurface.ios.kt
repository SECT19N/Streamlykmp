package dev.section.streamly.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** iOS is optional for this project; an AVPlayer-backed surface can replace this stub. */
@Composable
actual fun LivePlayerSurface(
    streamUrl: String,
    playWhenReady: Boolean,
    retryToken: Int,
    onStatus: (PlaybackStatus) -> Unit,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("Playback isn't available on iOS yet.", color = Color.White)
    }
}

@Composable
actual fun ImmersiveMode() = Unit
