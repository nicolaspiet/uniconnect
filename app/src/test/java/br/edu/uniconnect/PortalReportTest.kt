package br.edu.uniconnect

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class PortalReportTest {
    @Test fun `only exact HTTPS origin is trusted`() {
        assertTrue(PortalReport.allowed(URI(PortalReport.PORTAL)))
        listOf("http://login.ufdpar.edu.br:6082/a", "https://login.ufdpar.edu.br/a",
            "https://login.ufdpar.edu.br.evil.test:6082/a", "https://evil.test:6082/a",
            "https://user:secret@login.ufdpar.edu.br:6082/a").forEach {
            assertFalse(it, PortalReport.allowed(URI(it)))
        }
    }

    @Test fun `report excludes values cookies page text and action tokens`() {
        val html = """<p>private-user</p><form method="POST" action="/secret-token?session=private-session">
            <input name="username" value="private-user"><input type="password" name="passwd" value="private-pass">
            <input type="hidden" name="csrf" value="private-token"></form>
            <script>document.cookie='private-cookie';</script>"""
        val report = PortalReport.summarize(html, URI(PortalReport.PORTAL))
        assertTrue(report.contains("nome=passwd"))
        assertTrue(report.contains("nome=csrf"))
        assertTrue(report.contains("autorizado: true"))
        assertFalse(report.contains("private-"))
        assertFalse(report.contains("secret-token"))
    }

    @Test fun `cross origin action is flagged without exposing it`() {
        val report = PortalReport.summarize("<form action='https://other.test/secret'></form>", URI(PortalReport.PORTAL))
        assertTrue(report.contains("autorizado: false"))
        assertFalse(report.contains("other.test"))
    }
}
