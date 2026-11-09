package br.edu.uniconnect

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

// Independent files: settings are written by the UI, status only by the service.
// AtomicFile avoids stale SharedPreferences caches across the two processes.
data class AutomationOptions(
    val recover: Boolean = false,
    val boot: Boolean = false,
    val widgetConnectOnTap: Boolean = true,
    val widgetDetails: Boolean = true,
)
object AutomationPreferences {
    private fun file(context: Context, name: String) = AtomicFile(File(context.filesDir, name))
    private fun read(context: Context, name: String) = runCatching {
        DiskLock.run(context) { JSONObject(file(context, name).readFully().toString(Charsets.UTF_8)) }
    }.getOrDefault(JSONObject())
    private fun write(context: Context, name: String, data: JSONObject) {
        DiskLock.run(context) {
        val target = file(context, name)
        val stream = target.startWrite()
        try { stream.write(data.toString().toByteArray()); target.finishWrite(stream) }
        catch (error: Exception) { target.failWrite(stream); throw error }
        }
    }
    fun options(context: Context): AutomationOptions = read(context, "automation-options.json").let {
        AutomationOptions(it.optBoolean("recover"), it.optBoolean("boot"),
            it.optBoolean("widgetConnectOnTap", true), it.optBoolean("widgetDetails", true))
    }
    fun save(context: Context, options: AutomationOptions) = write(context, "automation-options.json",
        JSONObject().put("recover", options.recover).put("boot", options.boot)
            .put("widgetConnectOnTap", options.widgetConnectOnTap).put("widgetDetails", options.widgetDetails))
    fun enabled(context: Context) = read(context, "automation-enabled.json").optBoolean("enabled")
    fun attention(context: Context) = read(context, "automation-status.json").optBoolean("attention")
    fun enable(context: Context, enabled: Boolean) = write(context, "automation-enabled.json", JSONObject().put("enabled", enabled))
    fun status(context: Context): String {
        val data = read(context, "automation-status.json")
        val age = System.currentTimeMillis() - data.optLong("time")
        return if (age in 0..20_000) data.optString("message", "Monitorando")
            else if (enabled(context)) "Monitoramento solicitado. Confira a notificação."
            else "Modo automático pausado"
    }
    fun report(context: Context, message: String, attention: Boolean = false) = write(context, "automation-status.json",
        JSONObject().put("message", message).put("time", System.currentTimeMillis()).put("attention", attention))
}
