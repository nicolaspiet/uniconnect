package br.edu.uniconnect

import android.webkit.WebView

/** One click per attempt. Only preparation may be retried while the page is loading. */
class PortalLoginSession(
    credentials: Credentials,
    private val onSubmitted: () -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private var credentials: Credentials? = credentials
    private var active = true
    private var evaluating = false
    private var preparations = 0
    var submitted = false
        private set

    interface Page {
        fun evaluate(script: String, callback: (String?) -> Unit)
        fun retry(action: () -> Unit)
    }

    fun pageFinished(view: WebView) = pageFinished(object : Page {
        override fun evaluate(script: String, callback: (String?) -> Unit) {
            view.evaluateJavascript(script) { callback(it) }
        }
        override fun retry(action: () -> Unit) { view.postDelayed({ action() }, 500) }
    })

    fun pageFinished(page: Page) {
        if (!active || evaluating) return
        if (submitted) {
            page.evaluate(LoginScript.inspectResponse()) { result ->
                if (!active) return@evaluate
                when (result) {
                    "\"portal-error\"" -> fail("O portal exibiu um erro após o envio. Confira suas credenciais no portal oficial antes de tentar novamente.")
                    "\"challenge\"" -> fail("O portal exige uma etapa adicional. Conclua o login no portal oficial.")
                }
            }
            return
        }
        val entered = credentials ?: return
        evaluating = true
        preparations++
        page.evaluate(LoginScript.fillAndSubmit(entered.user, entered.password)) { result ->
            if (!active) return@evaluate
            evaluating = false
            when (result) {
                "\"submitted\"" -> {
                    submitted = true
                    credentials = null
                    onSubmitted()
                }
                "\"not-ready\"" -> if (preparations < 5) {
                    page.retry { if (active) pageFinished(page) }
                } else fail("O formulário não ficou pronto para envio. Tente novamente ou use o portal oficial.")
                else -> fail(LoginResult.message(result))
            }
        }
    }

    private fun fail(message: String) {
        close()
        onFailure(message)
    }

    fun close() { active = false; credentials = null }
}
