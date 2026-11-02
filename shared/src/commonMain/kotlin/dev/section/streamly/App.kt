package dev.section.streamly

import androidx.compose.runtime.Composable
import dev.section.streamly.core.network.rememberPlatformModule
import dev.section.streamly.di.appModule
import dev.section.streamly.navigation.AppNavigation
import dev.section.streamly.ui.theme.StreamlyTheme
import org.koin.compose.KoinApplication

@Composable
fun App() {
    val platformModule = rememberPlatformModule()
    KoinApplication(application = { modules(appModule, platformModule) }) {
        StreamlyTheme {
            AppNavigation()
        }
    }
}
