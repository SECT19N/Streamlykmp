package dev.section.streamly.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import dev.section.streamly.livetv.domain.Channel

/** Channel logo, or an initials tile when there is no logo or it fails to load. */
@Composable
fun ChannelLogo(channel: Channel, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.clip(shape)) {
        if (channel.logoUrl == null) {
            InitialsTile(channel.name)
        } else {
            SubcomposeAsyncImage(
                model = channel.logoUrl,
                contentDescription = "${channel.name} logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = { InitialsTile(channel.name) },
                error = { InitialsTile(channel.name) },
            )
        }
    }
}

@Composable
private fun InitialsTile(name: String) {
    Box(
        Modifier.fillMaxSize().background(placeholderColor(name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials(name),
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

internal fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val letters = words.take(2).mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
    return letters.joinToString("").uppercase().ifEmpty { "?" }
}

private val placeholderPalette = listOf(
    Color(0xFF3F51B5), Color(0xFF00796B), Color(0xFF8E24AA), Color(0xFFC2185B),
    Color(0xFFEF6C00), Color(0xFF455A64), Color(0xFF2E7D32), Color(0xFF1565C0),
)

private fun placeholderColor(name: String): Color =
    placeholderPalette[(name.hashCode() and Int.MAX_VALUE) % placeholderPalette.size]
