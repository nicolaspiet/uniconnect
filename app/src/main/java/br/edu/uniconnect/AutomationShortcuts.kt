package br.edu.uniconnect

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.os.Build
import android.service.quicksettings.*
import android.widget.RemoteViews

class AutomationWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { refresh(context) }
    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AutomationWidget::class.java))
            val enabled = AutomationPreferences.enabled(context)
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.automation_widget)
                val options = AutomationPreferences.options(context)
                val state = AutomationPreferences.status(context)
                val title = if (enabled) "UniConnect · ativo" else "UniConnect"
                val subtitle = if (options.widgetDetails) state.take(42) else if (enabled) "Toque para pausar" else "Toque para conectar"
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_status, subtitle)
                val action = PendingIntent.getActivity(context, 30,
                    Intent(context, AutomationActionActivity::class.java).putExtra("widgetTap", true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                views.setOnClickPendingIntent(R.id.widget_root, action)
                manager.updateAppWidget(id, views)
            }
        }
    }
}

class AutomationTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (AutomationPreferences.enabled(this@AutomationTile)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "UniConnect"
            subtitle = AutomationPreferences.status(this@AutomationTile).take(30)
            updateTile()
        }
    }
    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        val action = Intent(this, AutomationActionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 31, action, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else PendingIntent.getActivity(this, 31, action, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT).send()
    }
}
