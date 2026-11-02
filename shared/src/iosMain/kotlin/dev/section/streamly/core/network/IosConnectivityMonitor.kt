package dev.section.streamly.core.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.dsl.module

/** iOS is optional for this project; this always reports online (NWPathMonitor can replace it). */
class IosConnectivityMonitor : ConnectivityMonitor {
    override val isOnline: Flow<Boolean> = flowOf(true)
}

@Composable
actual fun rememberPlatformModule(): Module = remember {
    module { single<ConnectivityMonitor> { IosConnectivityMonitor() } }
}
