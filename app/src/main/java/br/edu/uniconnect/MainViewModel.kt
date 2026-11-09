package br.edu.uniconnect

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScreenState(
    val networks: List<WifiState> = emptyList(),
    val busy: Boolean = false,
    val report: String = "",
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(ScreenState())
    val state = mutable.asStateFlow()
    private var observation: Job? = null

    fun observe() {
        observation?.cancel()
        observation = viewModelScope.launch {
            WifiRepository(getApplication()).observe().catch {
                mutable.value = mutable.value.copy(error = "Permissão indisponível. Autorize localização precisa e tente novamente.")
            }.collect { mutable.value = mutable.value.copy(networks = it, error = null) }
        }
    }

    fun inspect(wifi: WifiState) {
        if (mutable.value.busy || wifi !in mutable.value.networks) return
        if (!CampusNetwork.accepts(wifi.ssid)) return
        mutable.value = mutable.value.copy(busy = true, report = "")
        viewModelScope.launch {
            val manager = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
            val defaultCaps = manager.activeNetwork?.let(manager::getNetworkCapabilities)
            val vpn = defaultCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            val result = try {
                withContext(Dispatchers.IO) { PortalProbe().inspect(wifi.network) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                DiagnosticFailure.describe(error)
            }
            mutable.value = mutable.value.copy(busy = false, report =
                "UniConnect ${BuildConfig.VERSION_NAME} — diagnóstico\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "Rede: ${wifi.ssid}\nPortal cativo detectado: ${wifi.captive}\nInternet validada pelo Android: ${wifi.validated}\n" +
                "VPN na rede padrão: ${vpn?.toString() ?: "indisponível"}\nTransporte da consulta: Wi-Fi selecionado\n\n$result")
        }
    }
}
