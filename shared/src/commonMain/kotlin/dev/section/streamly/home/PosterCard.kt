package dev.section.streamly.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import dev.section.streamly.home.domain.MediaItem
import dev.section.streamly.ui.components.clickableCard

@Composable
fun PosterCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .width(130.dp)
            .clickableCard(onClick = onClick, shape = shape),
    ) {
        Box(Modifier.aspectRatio(2f / 3f).clip(shape)) {
            val url = item.posterUrl()
            if (url == null) {
                PosterPlaceholder(item.title)
            } else {
                SubcomposeAsyncImage(
                    model = url,
                    contentDescription = null, // the title below names the card,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { PosterPlaceholder(item.title) },
                    error = { PosterPlaceholder(item.title) },
                )
            }
        }
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            // Always reserve two lines. Otherwise a row is as tall as its tallest visible card, so
            // scrolling a long title into view makes the whole screen jump down and back up.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun PosterPlaceholder(title: String) {
    Box(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics {} // decorative: the card already names the title
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
