package br.edu.uniconnect

import java.net.URI
import org.jsoup.Jsoup

/** No form values, HTML text, cookies or query values leave this parser. */
object PortalReport {
    const val PORTAL = "https://login.ufdpar.edu.br:6082/php/uid.php?vsys=1&rule=4"

    fun allowed(uri: URI): Boolean = uri.scheme.equals("https", true) &&
        uri.host.equals("login.ufdpar.edu.br", true) && uri.port == 6082 &&
        uri.rawUserInfo == null && uri.fragment == null

    private fun identifier(value: String): String =
        value.take(80).replace(Regex("[^a-zA-Z0-9_:.\\-]"), "_").ifBlank { "(ausente)" }

    fun summarize(html: String, base: URI): String {
        require(allowed(base))
        val doc = Jsoup.parse(html, base.toString())
        return buildString {
            appendLine("Formulários: ${doc.select("form").size}")
            doc.select("form").take(5).forEachIndexed { index, form ->
                val action = runCatching { base.resolve(form.attr("action")) }.getOrNull()
                appendLine("Formulário ${index + 1}: método=${identifier(form.attr("method"))}")
                // Paths can also contain tokens: report only whether the origin is trusted.
                appendLine("Destino no servidor autorizado: ${action?.let(::allowed) == true}")
                appendLine("Possui onsubmit: ${form.hasAttr("onsubmit")}")
                form.select("input, button, select, textarea").take(30).forEach {
                    appendLine("  ${it.tagName()}: tipo=${identifier(it.attr("type"))}; nome=${identifier(it.attr("name"))}")
                }
            }
            appendLine("Scripts na página: ${doc.select("script").size}")
            appendLine("Relatório sem valores, cookies, tokens ou conteúdo da página.")
        }
    }
}
