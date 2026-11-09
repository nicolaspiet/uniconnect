package br.edu.uniconnect

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build

object AutomationControl {
    fun prerequisites(context: Context): String? = when {
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED -> "Permita localização precisa para identificar o Wi-Fi."
        !context.getSystemService(LocationManager::class.java).isLocationEnabled -> "Ative a localização do aparelho."
        Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> "Permita notificações para acompanhar o monitoramento."
        !context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() -> "Ative as notificações do UniConnect."
        else -> null
    }
    fun start(context: Context, resume: Boolean = false): String? {
        prerequisites(context)?.let { return it }
        return try {
            AutomationPreferences.enable(context, true)
            context.startForegroundService(Intent(context, AutoConnectService::class.java).setAction(if (resume) "RESUME" else "START"))
            refresh(context)
            null
        } catch (_: Exception) {
            AutomationPreferences.enable(context, false)
            refresh(context)
            "O Android bloqueou o início. Abra o UniConnect para ativar."
        }
    }
    fun stop(context: Context) {
        AutomationPreferences.enable(context, false)
        context.stopService(Intent(context, AutoConnectService::class.java))
        refresh(context)
    }
    fun refresh(context: Context) {
        AutomationWidget.refresh(context)
        android.service.quicksettings.TileService.requestListeningState(context, ComponentName(context, AutomationTile::class.java))
    }
}

/** User-initiated visible trampoline gives Android a foreground activation context. */
class AutomationActionActivity : Activity() {
    override fun onResume() {
        super.onResume()
        val widgetTap = intent.getBooleanExtra("widgetTap", false)
        val options = AutomationPreferences.options(this)
        val error = if (AutomationPreferences.enabled(this)) {
            AutomationControl.stop(this); null
        } else if (!widgetTap || options.widgetConnectOnTap) AutomationControl.start(this) else {
            startActivity(Intent(this, MainActivity::class.java)); null
        }
        if (error != null) startActivity(Intent(this, MainActivity::class.java).putExtra("controlMessage", error))
        finish()
    }
}

class AutomationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AutomationPreferences.options(context).boot || !AutomationPreferences.enabled(context)) return
        val backgroundAllowed = context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        val error = if (backgroundAllowed) AutomationControl.start(context, resume = true) else "Toque para retomar o modo automático."
        if (error != null) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("restart", "Retomar conexão", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 8, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            manager.notify(18, Notification.Builder(context, "restart").setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("UniConnect").setContentText(error).setContentIntent(open).setAutoCancel(true).build())
        }
    }
}
