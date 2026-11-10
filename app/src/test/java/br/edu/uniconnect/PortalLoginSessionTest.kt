package br.edu.uniconnect

import org.junit.Assert.*
import org.junit.Test

class PortalLoginSessionTest {
    private class Page(var result: String? = "\"submitted\"") : PortalLoginSession.Page {
        var evaluations = 0
        var callback: ((String?) -> Unit)? = null
        var retry: (() -> Unit)? = null
        var delayed = false
        override fun evaluate(script: String, callback: (String?) -> Unit) {
            evaluations++
            if (delayed) this.callback = callback else callback(result)
        }
        override fun retry(action: () -> Unit) { retry = action }
        fun tick() { val action = retry; retry = null; action?.invoke() }
    }
    private var sends = 0
    private val failures = mutableListOf<String>()
    private fun session() = PortalLoginSession(Credentials("aluno", "senha"), { sends++ }, { failures.add(it) })

    @Test fun `page reload inspects response without another click`() {
        val session = session(); val page = Page()
        session.pageFinished(page)
        page.result = "\"login-form\""
        session.pageFinished(page)
        assertEquals(1, sends); assertTrue(session.submitted); assertTrue(failures.isEmpty())
        page.result = "\"portal-error\""
        session.pageFinished(page)
        session.pageFinished(page)
        assertEquals(1, failures.size); assertEquals(3, page.evaluations)
    }
    @Test fun `preparation retries bounded and never marks submitted`() {
        val session = session(); val page = Page("\"not-ready\"")
        session.pageFinished(page)
        repeat(10) { page.tick() }
        assertEquals(5, page.evaluations); assertEquals(0, sends)
        assertEquals(1, failures.size); assertFalse(session.submitted)
    }
    @Test fun `closed attempt ignores late JS callback and retry`() {
        val session = session(); val page = Page().apply { delayed = true }
        session.pageFinished(page)
        session.close(); page.callback?.invoke("\"submitted\"")
        session.pageFinished(page)
        assertEquals(0, sends); assertTrue(failures.isEmpty()); assertEquals(1, page.evaluations)
    }
    @Test fun `overlapping callbacks cannot submit twice`() {
        val session = session(); val page = Page().apply { delayed = true }
        session.pageFinished(page); session.pageFinished(page)
        assertEquals(1, page.evaluations)
        page.callback?.invoke("\"submitted\"")
        assertEquals(1, sends)
    }
    @Test fun `null and click failures terminate without repeating credentials`() {
        for (result in listOf(null, "\"submit-error\"", "\"script-error\"")) {
            val session = session(); val page = Page(result)
            session.pageFinished(page); session.pageFinished(page)
            assertEquals(1, page.evaluations)
        }
        assertEquals(0, sends); assertEquals(3, failures.size)
    }
}
