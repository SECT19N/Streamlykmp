package dev.section.streamly.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.core.module.Module
import org.koin.dsl.module

class AndroidConnectivityMonitor(context: Context) : ConnectivityMonitor {

    private val manager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val isOnline: Flow<Boolean> = callbackFlow {
        val networks = mutableSetOf<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(networks) { networks += network }
                trySend(true)
            }

            override fun onLost(network: Network) {
                val online = synchronized(networks) {
                    networks -= network
                    networks.isNotEmpty()
                }
                trySend(online)
            }
        }
        trySend(hasInternet())
        // registerNetworkCallback (not registerDefaultNetworkCallback) is available on API 23.
        manager.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun hasInternet(): Boolean {
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

@Composable
actual fun rememberPlatformModule(): Module {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        module { single<ConnectivityMonitor> { AndroidConnectivityMonitor(context) } }
    }
}
