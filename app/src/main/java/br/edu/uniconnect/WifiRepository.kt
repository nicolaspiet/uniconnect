package br.edu.uniconnect

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

data class WifiState(
    val network: Network,
    val ssid: String?,
    val captive: Boolean,
    val validated: Boolean,
)

class WifiRepository(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    @SuppressLint("MissingPermission")
    fun observe() = callbackFlow {
        val networks = linkedMapOf<Network, WifiState>()
        val callback = object : ConnectivityManager.NetworkCallback(
            ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
        ) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val raw = (caps.transportInfo as? WifiInfo)?.ssid
                val name = raw?.takeUnless { it == "<unknown ssid>" }?.removeSurrounding("\"")
                networks[network] = WifiState(network, name,
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                trySend(networks.values.toList())
            }
            override fun onLost(network: Network) {
                networks.remove(network)
                trySend(networks.values.toList())
            }
        }
        try {
            // Observe Wi-Fi explicitly: mobile data may remain the default network before login.
            manager.registerNetworkCallback(NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback)
        } catch (error: SecurityException) {
            close(error)
            return@callbackFlow
        }
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
}
