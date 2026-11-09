package br.edu.uniconnect

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class LoginScriptTest {
    @Test fun `credentials cannot terminate JS strings`() {
        val malicious = "');fetch('https://other.test'); //\nç🔒"
        val script = LoginScript.fillAndSubmit(malicious, malicious)
        assertFalse(script.contains(malicious))
        assertFalse(script.contains("other.test"))
        val encoded = Base64.getEncoder().encodeToString(malicious.toByteArray(Charsets.UTF_8))
        assertTrue(script.contains("decode('$encoded')"))
        assertEquals(malicious, Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8))
    }
}
