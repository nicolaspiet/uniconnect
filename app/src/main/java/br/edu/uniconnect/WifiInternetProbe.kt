package br.edu.uniconnect

import android.net.Network
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object WifiInternetProbe {
    /** No redirects, no fallback to mobile data, and normal TLS verification. */
    fun reachable(network: Network): Boolean = runCatching {
        val connection = network.openConnection(URL("https://connectivitycheck.gstatic.com/generate_204")) as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            connection.responseCode == 204
        } finally { connection.disconnect() }
    }.getOrDefault(false)
}
