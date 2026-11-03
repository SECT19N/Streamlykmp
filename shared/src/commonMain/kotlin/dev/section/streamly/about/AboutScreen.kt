package dev.section.streamly.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import streamlykmp.shared.generated.resources.Res
import streamlykmp.shared.generated.resources.tmdb_logo

const val TMDB_ATTRIBUTION =
    "This product uses the TMDB API but is not endorsed or certified by TMDB."

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Text(
            "About",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Streamly", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Live TV channels, plus movie and show browsing powered by TMDB.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Image(
                painter = painterResource(Res.drawable.tmdb_logo),
                contentDescription = "The Movie Database (TMDB) logo",
                modifier = Modifier.width(120.dp),
            )
            Text(TMDB_ATTRIBUTION, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
