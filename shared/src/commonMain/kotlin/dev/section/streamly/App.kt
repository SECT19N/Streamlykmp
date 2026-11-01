package dev.section.streamly

import androidx.compose.runtime.Composable
import dev.section.streamly.di.appModule
import dev.section.streamly.navigation.AppNavigation
import dev.section.streamly.ui.theme.StreamlyTheme
import org.koin.compose.KoinApplication

@Composable
fun App() {
    KoinApplication(application = { modules(appModule) }) {
        StreamlyTheme {
            AppNavigation()
        }
    }
}
