package dev.section.streamly.core.network

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import org.koin.core.module.Module

/** Emits the current connectivity immediately, then every change. */
interface ConnectivityMonitor {
    val isOnline: Flow<Boolean>
}

/** Platform-specific Koin bindings (needs an Android context, hence composable). */
@Composable
expect fun rememberPlatformModule(): Module
