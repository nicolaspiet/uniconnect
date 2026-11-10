package br.edu.uniconnect

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.*
import android.net.http.SslError
import android.os.*
import android.webkit.*
import java.io.ByteArrayInputStream
import java.net.URI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/** Separate process keeps WebView network binding isolated from the visible app. */
class AutoConnectService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var manager: ConnectivityManager
    private var browser: WebView? = null
    private var session: PortalLoginSession? = null
    private var current: Network? = null
    private var job: Job? = null
    private var debounceJob: Job? = null
    private var probeJob: Job? = null
    private val attempted = mutableSetOf<Network>()
    private var failed = false
    private var lastStatus = "Aguardando Wi-Fi da UFDPar"
    private var lastAttempt = 0L
    private var latest: List<WifiState> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { finishAttempt("Sem confirmação. Use o login manual; modo automático pausado.", true) }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        failed = AutomationPreferences.attention(this)
        manager = getSystemService(ConnectivityManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("automatic", "Conexão automática", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { AutomationPreferences.enable(this, false); AutomationControl.refresh(this); stopSelf(); return START_NOT_STICKY }
        if (!AutomationPreferences.enabled(this)) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == "START") { failed = false; attempted.clear() }
        try {
            val type = if (Build.VERSION.SDK_INT >= 34)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                else ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            startForeground(17, notification(if (failed) "Atenção necessária. Abra o login manual." else "Aguardando Wi-Fi da UFDPar"), type)
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                stopSelf(); return START_NOT_STICKY
            }
            if (job == null) job = scope.launch {
                val ticks = flow { while (true) { emit(Unit); delay(5_000) } }
                WifiRepository(this@AutoConnectService).observe().combine(ticks) { networks, _ -> networks }.collect { networks ->
                    // NetworkCallback can keep old capabilities after captive-portal logout.
                    // Refresh each network on the polling tick so logout is noticed without reopening the app.
                    val refreshed = networks.map { state ->
                        val caps = manager.getNetworkCapabilities(state.network)
                        if (caps == null) state else state.copy(
                            captive = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
                            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                        )
                    }
                    latest = refreshed
                    attempted.retainAll(refreshed.map { it.network }.toSet())
                    AutomationPreferences.report(this@AutoConnectService, lastStatus, failed || current in attempted)
                    if (!AutomationPreferences.enabled(this@AutoConnectService)) { stopSelf(); return@collect }
                    val active = current
                    if (active != null && vpnActive()) {
                        finishAttempt("Uma VPN foi ativada durante a autenticação. Desative-a e reative o modo automático.", true)
                    }
                    if (current != null && active != null && refreshed.none { it.network == active }) {
                        finishAttempt("Wi-Fi desconectou. Aguardando nova conexão.", false)
                    }
                    if (current != null && active != null && refreshed.any { it.network == active && it.validated }) {
                        attempted.remove(active)
                        finishAttempt("Você já está conectado. Monitorando Wi-Fi.", false)
                    }
                    val wifi = refreshed.firstOrNull { CampusNetwork.accepts(it.ssid) }
                    if (failed) update(lastStatus)
                    else if (wifi != null && wifi.validated && current == null) update("Você já está conectado. Monitorando Wi-Fi.")
                    else if (wifi == null && current == null) update("Aguardando Wi-Fi da UFDPar.")
                    if (wifi != null && !wifi.validated && !failed && current == null && wifi.network !in attempted) {
                        if (SystemClock.elapsedRealtime() - lastAttempt < 60_000 && lastAttempt != 0L) {
                            update("Aguardando intervalo de proteção entre tentativas.")
                        } else if (vpnActive()) {
                            update("Uma VPN está ativa. Desative-a para autenticar.")
                        } else {
                            current = wifi.network
                            debounceJob?.cancel()
                            debounceJob = scope.launch {
                                delay(3_000)
                                val fresh = latest.firstOrNull { it.network == wifi.network }
                                if (current != wifi.network) return@launch
                                if (fresh == null || fresh.validated || vpnActive()) {
                                    finishAttempt("Aguardando rede sem VPN e com login necessário.", false)
                                } else {
                                    try { begin(wifi.network) }
                                    catch (error: CancellationException) { throw error }
                                    catch (_: Exception) { finishAttempt("Não foi possível autenticar. Automação pausada; use o login manual.", true) }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) { AutomationPreferences.enable(this, false); AutomationControl.refresh(this); stopSelf(); return START_NOT_STICKY }
        return if (AutomationPreferences.options(this).recover) START_STICKY else START_NOT_STICKY
    }

    private fun vpnActive() = manager.activeNetwork?.let(manager::getNetworkCapabilities)
        ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun begin(network: Network) {
        if (current != network) return
        val saved = try { withContext(Dispatchers.IO) { CredentialStore(this@AutoConnectService).read() } }
            catch (_: Exception) { null }
        if (saved == null) { finishAttempt("Salve suas credenciais no login manual e reative o modo automático.", true); return }
        if (vpnActive() || current != network) { finishAttempt("VPN ativa ou Wi-Fi indisponível.", false); return }
        attempted.add(network)
        lastAttempt = SystemClock.elapsedRealtime()
        if (!manager.bindProcessToNetwork(network)) { finishAttempt("Não foi possível selecionar o Wi-Fi.", true); return }
        update("Autenticando no portal da UFDPar…")
        session = PortalLoginSession(saved,
            onSubmitted = { update("Login enviado. Aguardando validação da internet…") },
            onFailure = { message -> handler.post { if (current == network) finishAttempt(message, true) } })
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                fun allowed(url: String) = runCatching { PortalReport.allowed(URI(url)) }.getOrDefault(false)
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !allowed(request.url.toString())
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    if (allowed(request.url.toString())) null else WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                override fun onReceivedSslError(view: WebView, ssl: SslErrorHandler, error: SslError) {
                    ssl.cancel(); session?.close()
                    handler.post { if (browser === view) finishAttempt("Certificado inválido. Automação pausada; use o login manual.", true) }
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) handler.post { if (browser === view) finishAttempt("Erro de conexão (${error.errorCode}). Automação pausada.", true) }
                }
                override fun onPageFinished(view: WebView, url: String) {
                    if (allowed(url) && browser === view) session?.pageFinished(view)
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) handler.post {
                        if (browser === view) finishAttempt("O portal respondeu HTTP ${response.statusCode}. Automação pausada.", true)
                    }
                }
            }
            loadUrl(PortalReport.PORTAL)
        }
        handler.postDelayed(timeout, 30_000)
        // Probe only this Wi-Fi after submission; HTTPS 204 cannot be a captive portal page.
        probeJob?.cancel()
        probeJob = scope.launch {
            repeat(5) {
                delay(4_000)
                if (current != network) return@launch
                val validated = manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                val reachable = if (session?.submitted == true && !validated) withContext(Dispatchers.IO) {
                    WifiInternetProbe.reachable(network)
                } else false
                if (current == network && (validated || reachable)) {
                    attempted.remove(network)
                    finishAttempt("Você já está conectado. Monitorando Wi-Fi.", false)
                    return@launch
                }
            }
        }
    }

    private fun finishAttempt(message: String, pause: Boolean) {
        debounceJob?.cancel(); debounceJob = null
        probeJob?.cancel(); probeJob = null
        handler.removeCallbacks(timeout)
        session?.close(); session = null
        browser?.apply { stopLoading(); destroy() }
        browser = null
        manager.bindProcessToNetwork(null)
        current = null
        if (pause) failed = true
        update(message)
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, AutoConnectService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "automatic").setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("UniConnect automático").setContentText(message).setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "Pausar", stop).build()).build()
    }
    private fun update(message: String) {
        lastStatus = message
        // A process killed during a submission must not blindly resubmit on restart.
        AutomationPreferences.report(this, message, failed || current in attempted)
        AutomationControl.refresh(this)
        getSystemService(NotificationManager::class.java).notify(17, notification(message))
    }
    override fun onDestroy() {
        scope.cancel(); handler.removeCallbacksAndMessages(null)
        session?.close(); session = null
        browser?.apply { stopLoading(); destroy() }; browser = null
        manager.bindProcessToNetwork(null)
        super.onDestroy()
    }
}
