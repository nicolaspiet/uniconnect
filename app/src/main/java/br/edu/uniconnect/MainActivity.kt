package br.edu.uniconnect

import android.Manifest
import android.app.PendingIntent
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private fun appSettings() = startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val model: MainViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            var page by remember { mutableStateOf("home") }
            var darkTheme by remember { mutableStateOf(AppearancePreferences.dark(this)) }
            var options by remember { mutableStateOf(AutomationPreferences.options(this)) }
            var enabled by remember { mutableStateOf(AutomationPreferences.enabled(this)) }
            var status by remember { mutableStateOf(AutomationPreferences.status(this)) }
            var message by remember { mutableStateOf(intent.getStringExtra("controlMessage").orEmpty()) }
            var diagnosis by remember { mutableStateOf(false) }
            var update by remember { mutableStateOf<UpdateInfo?>(null) }
            var updateOpen by remember { mutableStateOf(false) }
            var updateBusy by remember { mutableStateOf(false) }
            var updateProgress by remember { mutableStateOf<DownloadProgress?>(null) }
            var updateError by remember { mutableStateOf<String?>(null) }
            var readyUpdate by remember { mutableStateOf<java.io.File?>(null) }
            BackHandler(enabled = page != "home") { page = "home"; message = "" }
            val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.observe() }
            val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                message = if (granted) "Pronto. Toque em Ativar automático." else "Autorize notificações nas configurações para acompanhar o monitoramento."
            }
            LaunchedEffect(Unit) {
                model.observe()
                while (true) {
                    enabled = AutomationPreferences.enabled(this@MainActivity)
                    status = AutomationPreferences.status(this@MainActivity)
                    delay(2_000)
                }
            }
            LaunchedEffect(Unit) {
                when (val result = UpdateChecker.check(this@MainActivity)) {
                    is UpdateCheckResult.Available -> { update = result.update; updateOpen = true }
                    else -> Unit
                }
            }
            fun save(next: AutomationOptions) {
                try {
                    AutomationPreferences.save(this, next)
                    options = next
                    if (enabled) startForegroundService(Intent(this, AutoConnectService::class.java).setAction("SETTINGS"))
                } catch (_: Exception) { message = "Não foi possível aplicar a opção ao monitoramento. Pause e ative novamente." }
            }
            UniConnectTheme(darkTheme) {
                update?.takeIf { updateOpen }?.let { available ->
                    AlertDialog(
                        onDismissRequest = { if (!updateBusy) updateOpen = false },
                        title = { Text("Atualização disponível") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("UniConnect ${available.versionName}", style = MaterialTheme.typography.titleMedium)
                                if (available.notes.isNotBlank()) Text(available.notes.take(600))
                                updateProgress?.let { progress -> Text(progress.percent?.let { "$it%" } ?: "Baixando…") }
                                updateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                        },
                        confirmButton = {
                            Button(enabled = !updateBusy, onClick = {
                                updateBusy = true; updateError = null
                                lifecycleScope.launch {
                                    try {
                                        val apk = readyUpdate?.takeIf { it.isFile }
                                            ?: UpdateChecker.download(this@MainActivity, available) { updateProgress = it }
                                                .also { readyUpdate = it }
                                        if (!UpdateChecker.canRequestInstall(this@MainActivity)) {
                                            startActivity(UpdateChecker.installPermissionIntent(this@MainActivity))
                                            updateError = "Permita instalações desta fonte e toque em Atualizar novamente."
                                        } else {
                                            startActivity(UpdateChecker.installIntent(this@MainActivity, apk))
                                            updateOpen = false
                                        }
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                    catch (failure: java.io.IOException) { readyUpdate = null; updateError = failure.message ?: "Download interrompido. Tente novamente." }
                                    catch (_: android.content.ActivityNotFoundException) { updateError = "O instalador do Android não está disponível neste aparelho. Baixe o APK pela página de releases." }
                                    catch (_: SecurityException) { updateError = "O Android bloqueou o instalador. Autorize instalações do UniConnect e tente novamente." }
                                    catch (_: Exception) { readyUpdate = null; updateError = "Não foi possível preparar a instalação. Tente novamente." }
                                    finally { updateBusy = false }
                                }
                            }) { Text(if (updateBusy) "Baixando…" else "Atualizar") }
                        },
                        dismissButton = { TextButton(enabled = !updateBusy, onClick = { updateOpen = false }) { Text("Agora não") } }
                    )
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("UniConnect", style = MaterialTheme.typography.headlineMedium); Text("UFDPar", color = MaterialTheme.colorScheme.primary) }
                            TextButton(onClick = { page = if (page == "home") "settings" else "home"; message = "" }) { Text(if (page == "home") "Ajustes" else "Voltar") }
                        }
                        if (page == "home") {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text(if (enabled) "Automático solicitado" else "Conecte e siga seu dia", style = MaterialTheme.typography.headlineSmall)
                                    Text(if (enabled) status else "Ative uma vez e acompanhe a conexão pela notificação, mesmo com a tela apagada.")
                                    Button(onClick = {
                                        if (enabled) {
                                            AutomationControl.stop(this@MainActivity); enabled = false; message = "Monitoramento pausado."
                                        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                            permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                                            message = "Permita localização precisa e toque novamente em Ativar."
                                        } else if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            message = AutomationControl.start(this@MainActivity) ?: "Acompanhe o estado na notificação."
                                            enabled = AutomationPreferences.enabled(this@MainActivity)
                                        }
                                    }, modifier = Modifier.fillMaxWidth()) { Text(if (enabled) "Pausar automático" else "Ativar automático") }
                                }
                            }
                            val wifi = state.networks.firstOrNull { CampusNetwork.accepts(it.ssid) } ?: state.networks.firstOrNull()
                            Card {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(wifi?.ssid ?: "Conecte ao Wi-Fi do campus", style = MaterialTheme.typography.titleLarge)
                                    Text(when { wifi == null -> "Redes UFDPar-A, UFDPar-B ou UFDPar-C"; wifi.validated -> "Você já está conectado à internet"; wifi.captive -> "Autenticação necessária"; else -> "Aguardando autenticação" })
                                    if (wifi != null && CampusNetwork.accepts(wifi.ssid) && !wifi.validated) OutlinedButton(onClick = {
                                        AutomationControl.stop(this@MainActivity)
                                        startActivity(Intent(this@MainActivity, LoginActivity::class.java).putExtra("network", wifi.network))
                                    }) { Text("Login e credenciais") }
                                }
                            }
                            Text("Primeiro acesso? Salve suas credenciais em Login e credenciais. Se uma VPN estiver ativa, desative-a durante a autenticação.", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { diagnosis = !diagnosis }) { Text(if (diagnosis) "Ocultar diagnóstico" else "Precisa de ajuda?") }
                            if (diagnosis) {
                                state.error?.let { Text(it) }
                                OutlinedButton(onClick = { permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }) { Text("Permissões de Wi-Fi") }
                                TextButton(onClick = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Ativar localização do aparelho") }
                                state.networks.filter { CampusNetwork.accepts(it.ssid) }.forEach { network ->
                                    OutlinedButton(onClick = { model.inspect(network) }, enabled = !state.busy) { Text(if (state.busy) "Consultando…" else "Diagnosticar portal") }
                                }
                                if (state.report.isNotBlank()) {
                                    SelectionContainer { Text(state.report, style = MaterialTheme.typography.bodySmall) }
                                    TextButton(onClick = { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, state.report) }, "Compartilhar diagnóstico")) }) { Text("Compartilhar relatório") }
                                }
                            }
                        } else {
                            Text("Do seu jeito", style = MaterialTheme.typography.headlineSmall)
                            Text("Todas as opções abaixo são opcionais. Pausar o automático impede retomadas até você ativar novamente.")
                            SettingCard("Recuperar monitoramento", "Permite ao Android tentar retomar após encerrar o processo. Não supera Forçar parada nem garante que o HyperOS mantenha o serviço.", options.recover) { save(options.copy(recover = it)) }
                            SettingCard("Retomar após reiniciar", "Retoma apenas se o automático estava ativado. Exige localização Permitir o tempo todo e autoinício permitido no HyperOS.", options.boot) { save(options.copy(boot = it)) }
                            SettingCard("Widget conecta ao tocar", "Quando desligado, tocar no widget abre o app. Quando ligado, tocar alterna o modo automático.", options.widgetConnectOnTap) { save(options.copy(widgetConnectOnTap = it)) }
                            SettingCard("Mostrar estado no widget", "Exibe rede, validação e mensagens curtas. Desligado mostra apenas o controle.", options.widgetDetails) { save(options.copy(widgetDetails = it)) }
                            SettingCard("Tema preto e branco", "Alterna entre fundo branco/preto e texto de alto contraste.", darkTheme) { darkTheme = it; AppearancePreferences.setDark(this@MainActivity, it) }
                            if (options.boot && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                Text("Para iniciar sem abrir o app após reiniciar: em Permissões > Localização, escolha Permitir o tempo todo. Essa permissão é opcional; sem ela, tentaremos mostrar um lembrete.")
                                OutlinedButton(onClick = { appSettings() }) { Text("Configurar permissão de reinício") }
                            }
                            Text("Acesso rápido", style = MaterialTheme.typography.titleLarge)
                            Text("Escolha onde ativar ou pausar. Os atalhos podem abrir uma tela breve de ativação; não é preciso navegar pelo app.")
                            OutlinedButton(onClick = {
                                val manager = AppWidgetManager.getInstance(this@MainActivity)
                                message = if (manager.isRequestPinAppWidgetSupported) {
                                    manager.requestPinAppWidget(ComponentName(this@MainActivity, AutomationWidget::class.java), null, null)
                                    "Confirme a inclusão na tela inicial."
                                } else "Pressione a tela inicial, escolha Widgets e procure UniConnect."
                            }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar widget") }
                            OutlinedButton(onClick = {
                                if (Build.VERSION.SDK_INT >= 33) getSystemService(StatusBarManager::class.java).requestAddTileService(
                                    ComponentName(this@MainActivity, AutomationTile::class.java), "UniConnect", Icon.createWithResource(this@MainActivity, R.drawable.ic_wifi), mainExecutor
                                ) { result -> message = if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) "Atalho disponível na central de controles." else "Você pode adicioná-lo ao editar os atalhos da central de controles." }
                                else message = "Abra a central de controles, toque em Editar e adicione UniConnect."
                            }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar atalho rápido") }
                            Text("Bateria e HyperOS", style = MaterialTheme.typography.titleLarge)
                            Text("Para manter o monitoramento, escolha bateria Sem restrições. Se ativou retomada após reiniciar, permita também o autoinício nas configurações do HyperOS.")
                            TextButton(onClick = { appSettings() }) { Text("Abrir configurações do aplicativo") }
                            TextButton(onClick = {
                                message = "Consultando versões…"
                                lifecycleScope.launch {
                                    when (val result = UpdateChecker.check(this@MainActivity)) {
                                        is UpdateCheckResult.Available -> { update = result.update; updateOpen = true; message = "" }
                                        is UpdateCheckResult.Unavailable -> message = result.message
                                        UpdateCheckResult.UpToDate -> message = "Você já está usando a versão mais recente."
                                    }
                                }
                            }) { Text("Verificar atualizações") }
                        }
                        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
                        Text("${BuildConfig.VERSION_NAME} · Feito para seu Wi-Fi no campus", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                Switch(checked = checked, onCheckedChange = onChange)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
