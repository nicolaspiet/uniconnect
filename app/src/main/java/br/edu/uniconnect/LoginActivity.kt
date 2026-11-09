package br.edu.uniconnect

import android.annotation.SuppressLint
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.http.SslError
import android.os.Bundle
import android.view.WindowManager
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import java.io.ByteArrayInputStream
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : ComponentActivity() {
    private lateinit var manager: ConnectivityManager
    private var network: Network? = null
    private var webView: WebView? = null
    private var bound = false
    private var submitStarted = false
    private var verificationJob: Job? = null
    private var credentials: Credentials? = null
    private var status by mutableStateOf("Suas credenciais serão enviadas apenas ao portal HTTPS da UFDPar.")
    private var showingProgress by mutableStateOf(false)

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        manager = getSystemService(ConnectivityManager::class.java)
        network = intent.getParcelableExtra("network")
        setContent {
            var user by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var rememberCredentials by remember { mutableStateOf(false) }
            var busy by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                try {
                    val saved = withContext(Dispatchers.IO) { CredentialStore(this@LoginActivity).read() }
                    if (saved != null) {
                        user = saved.user
                        password = saved.password
                        rememberCredentials = true
                    }
                } catch (_: Exception) {
                    status = "Não foi possível ler os dados salvos. Digite novamente ou apague os dados salvos."
                } finally { busy = false }
            }
            UniConnectTheme(AppearancePreferences.dark(this)) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Conectar à UFDPar", style = MaterialTheme.typography.headlineSmall)
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                        if (showingProgress) {
                            Box(Modifier.fillMaxWidth().weight(1f)) {
                                AndroidView(factory = { createBrowser() }, modifier = Modifier.size(1.dp).alpha(0f))
                                Card(Modifier.fillMaxWidth().align(Alignment.Center)) {
                                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        CircularProgressIndicator()
                                        Text("Autenticando com o portal…", style = MaterialTheme.typography.titleLarge)
                                        Text(status)
                                        Text("O navegador não é exibido. Aguarde até 30 segundos.", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            OutlinedButton(onClick = { cancelLogin() }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                            if (status.startsWith("O portal não confirmou") || status.startsWith("Certificado") || status.startsWith("O portal não aceitou")) {
                                Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Voltar e tentar novamente") }
                            }
                        } else {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Use o usuário e a senha do SIGAA. Esta versão preenche e envia o portal com o app aberto; ainda não funciona em segundo plano.")
                                OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Usuário SIGAA") }, singleLine = true, enabled = !busy)
                                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Senha") }, singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(), enabled = !busy)
                                Row {
                                    Checkbox(checked = rememberCredentials, onCheckedChange = { rememberCredentials = it }, enabled = !busy)
                                    Text("Lembrar neste aparelho (criptografado)", Modifier.padding(top = 12.dp))
                                }
                                Button(enabled = !busy && user.isNotBlank() && password.isNotEmpty(), onClick = {
                                    if (prepareNetwork()) {
                                        busy = true
                                        val entered = Credentials(user.trim(), password)
                                        lifecycleScope.launch {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    val store = CredentialStore(this@LoginActivity)
                                                    if (rememberCredentials) store.save(entered) else store.clear()
                                                }
                                                credentials = entered
                                                password = ""
                                                submitStarted = false
                                                showingProgress = true
                                                status = "Abrindo o portal pelo Wi-Fi…"
                                            } catch (_: Exception) {
                                                releaseNetwork()
                                                status = "Não foi possível preparar o armazenamento. Nenhuma credencial enviada."
                                            } finally { busy = false }
                                        }
                                    }
                                }) { Text("Conectar") }
                                OutlinedButton(enabled = !busy, onClick = {
                                    busy = true
                                    lifecycleScope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { CredentialStore(this@LoginActivity).clear() }
                                            user = ""; password = ""; rememberCredentials = false
                                            status = "Credenciais salvas apagadas."
                                        } catch (_: Exception) { status = "Não foi possível apagar os dados salvos." }
                                        finally { busy = false }
                                    }
                                }) { Text("Apagar credenciais salvas") }
                                if (isVpnActive()) Text("Uma VPN está ativa. Desative-a para autenticar nesta rede.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { finish() }) { Text("Voltar") }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun prepareNetwork(): Boolean {
        val selected = network
        val caps = selected?.let(manager::getNetworkCapabilities)
        if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            status = "O Wi-Fi desconectou. Volte e selecione novamente a rede da universidade."
            return false
        }
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            status = "Você já está conectado à internet."
            return false
        }
        if (isVpnActive()) {
            status = "Uma VPN está ativa. Desative-a para autenticar nesta rede."
            return false
        }
        return try {
            bound = manager.bindProcessToNetwork(selected)
            if (!bound) status = "Não foi possível selecionar o Wi-Fi. Tente reconectar."
            bound
        } catch (_: Exception) {
            status = "O Android não permitiu selecionar o Wi-Fi."
            false
        }
    }

    private fun isVpnActive(): Boolean = manager.activeNetwork?.let(manager::getNetworkCapabilities)
        ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

    private fun allowed(url: String): Boolean = runCatching { PortalReport.allowed(URI(url)) }.getOrDefault(false)

    @SuppressLint("SetJavaScriptEnabled")
    private fun createBrowser(): WebView = WebView(this).apply {
        webView = this
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (allowed(uri.toString())) return false
                if (submitStarted && (uri.scheme == "https" || uri.scheme == "http")) return false
                status = "O portal tentou abrir um endereço não autorizado."
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val uri = request.url
                if (allowed(uri.toString()) || (submitStarted && request.isForMainFrame && (uri.scheme == "https" || uri.scheme == "http"))) return null
                return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                credentials = null
                status = "Certificado HTTPS não validado. Conexão interrompida."
                submitStarted = true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    status = "Falha ao carregar o portal (código ${error.errorCode}). Encerre e tente novamente."
                    credentials = null
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) {
                    status = "O portal respondeu HTTP ${response.statusCode}. Nenhum novo envio automático será feito."
                    credentials = null
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (submitStarted || !allowed(url)) return
                val saved = credentials ?: return
                submitStarted = true
                view.evaluateJavascript(LoginScript.fillAndSubmit(saved.user, saved.password)) { result ->
                    credentials = null
                    status = when (result) {
                        "\"submitted\"" -> "Login enviado. Confirmando a conexão…"
                        "\"needs-interaction\"" -> "O portal exige uma interação adicional."
                        "\"unsupported\"" -> "O formulário do portal mudou e precisa de atualização."
                        else -> "O portal não aceitou o envio automático."
                    }
                    if (result == "\"submitted\"") verifyConnection()
                }
            }
        }
        loadUrl(PortalReport.PORTAL)
    }

    private fun verifyConnection() {
        verificationJob?.cancel()
        verificationJob = lifecycleScope.launch {
            repeat(15) {
                delay(2_000)
                val caps = network?.let(manager::getNetworkCapabilities)
                if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    status = "O Wi-Fi caiu durante a autenticação."
                    return@launch
                }
                if (isVpnActive()) {
                    status = "Uma VPN foi ativada durante a autenticação. Desative-a e tente novamente."
                    return@launch
                }
                if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) {
                    status = "Você já está conectado à internet."
                    delay(700)
                    finish()
                    return@launch
                }
            }
            status = "O portal não confirmou a conexão em 30 segundos. Verifique a senha, a VPN e o Wi-Fi."
        }
    }

    private fun cancelLogin() {
        verificationJob?.cancel()
        credentials = null
        finish()
    }

    private fun releaseNetwork() {
        if (bound) {
            manager.bindProcessToNetwork(null)
            bound = false
        }
    }

    override fun onDestroy() {
        verificationJob?.cancel()
        credentials = null
        webView?.apply { stopLoading(); removeAllViews(); destroy() }
        webView = null
        releaseNetwork()
        super.onDestroy()
    }
}
