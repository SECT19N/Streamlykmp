package dev.section.streamly.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.section.streamly.ui.components.focusRing
import org.jetbrains.compose.resources.painterResource
import streamlykmp.shared.generated.resources.Res
import streamlykmp.shared.generated.resources.tmdb_logo

const val TMDB_ATTRIBUTION =
    "This product uses the TMDB API but is not endorsed or certified by TMDB."

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Focusable so a D-pad can scroll the page when it doesn't fit.
            .focusRing(interaction, RoundedCornerShape(12.dp))
            .focusable(interactionSource = interaction)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Streamly",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Live TV channels, plus movie and show browsing powered by TMDB.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
        Image(
            painter = painterResource(Res.drawable.tmdb_logo),
            contentDescription = "The Movie Database (TMDB) logo",
            modifier = Modifier.width(120.dp),
        )
        Text(
            TMDB_ATTRIBUTION,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
    }
}
