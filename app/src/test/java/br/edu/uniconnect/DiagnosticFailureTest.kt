package br.edu.uniconnect

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.*
import org.junit.Test

class DiagnosticFailureTest {
    @Test fun `nested failure is classified without exposing exception messages`() {
        val text = DiagnosticFailure.describe(IOException("token=secret", UnknownHostException("private-host")))
        assertTrue(text.contains("DNS"))
        assertTrue(text.contains("IOException / UnknownHostException"))
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("private-host"))
    }

    @Test fun `connect error does not invent session rejection or certificate error`() {
        val text = DiagnosticFailure.describe(ConnectException("secret"))
        assertTrue(text.contains("porta do portal"))
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("certificado"))
    }

    @Test fun `TLS handshake failure is not always a certificate failure`() {
        val text = DiagnosticFailure.describe(SSLHandshakeException("secret"))
        assertTrue(text.contains("certificado, protocolo ou servidor"))
        assertFalse(text.contains("secret"))
    }
}
