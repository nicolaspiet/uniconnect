package br.edu.uniconnect

import android.net.Network
import android.system.ErrnoException
import android.system.OsConstants
import java.io.ByteArrayOutputStream
import java.net.URI
import javax.net.ssl.HttpsURLConnection

class PortalProbe {
    fun inspect(network: Network): String {
        var stage = "abrir conexão vinculada ao Wi-Fi"
        return try {
            inspectConnection(network) { stage = it }
        } catch (error: Exception) {
            val errno = generateSequence<Throwable>(error) { it.cause }.take(6)
                .filterIsInstance<ErrnoException>().firstOrNull()?.errno
            val systemCode = errno?.let { "\nCódigo do sistema: ${OsConstants.errnoName(it)} ($it)" }.orEmpty()
            "Etapa: $stage\n${DiagnosticFailure.describe(error)}$systemCode"
        }
    }

    private fun inspectConnection(network: Network, stage: (String) -> Unit): String {
        var uri = URI(PortalReport.PORTAL)
        repeat(4) {
            check(PortalReport.allowed(uri))
            // DNS and traffic use this Wi-Fi network, never the cellular default.
            stage("abrir conexão vinculada ao Wi-Fi")
            val connection = network.openConnection(uri.toURL()) as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.useCaches = false
                connection.setRequestProperty("Accept", "text/html")
                stage("conectar ao portal HTTPS na porta 6082 e receber cabeçalhos")
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val next = connection.getHeaderField("Location")?.let { uri.resolve(it) }
                    if (next == null || !PortalReport.allowed(next)) {
                        return "HTTP $code. Redirecionamento externo bloqueado. Nenhuma credencial enviada."
                    }
                    uri = next
                } else {
                    if (code != 200) return "HTTP $code. O portal não entregou o formulário."
                    if (!connection.contentType.orEmpty().contains("text/html", ignoreCase = true)) {
                        return "HTTP 200. Resposta não é HTML; integração específica necessária."
                    }
                    stage("ler HTML do portal (HTTP $code)")
                    val bytes = connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= 262_144) {
                            val count = input.read(buffer, 0, minOf(buffer.size, 262_145 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    if (bytes.size > 262_144) return "Página excede o limite de diagnóstico (256 KiB)."
                    stage("interpretar estrutura do formulário")
                    return "HTTP 200; certificado HTTPS validado.\n" +
                        PortalReport.summarize(bytes.toString(Charsets.UTF_8), uri)
                }
            } finally {
                connection.disconnect()
            }
        }
        return "Limite de redirecionamentos atingido."
    }
}
