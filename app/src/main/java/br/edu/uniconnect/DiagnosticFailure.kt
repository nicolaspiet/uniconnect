package br.edu.uniconnect

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** Never include exception messages: they can contain URLs, tokens or response content. */
object DiagnosticFailure {
    fun describe(error: Exception): String {
        val chain = generateSequence<Throwable>(error) { it.cause }.take(6).toList()
        val reason = when {
            chain.any { it is UnknownHostException } -> "DNS do portal indisponível nesta rede."
            chain.any { it is SSLPeerUnverifiedException } -> "Não foi possível validar a identidade HTTPS do servidor."
            chain.any { it is SSLHandshakeException } -> "Falha na negociação TLS; certificado, protocolo ou servidor precisam ser verificados."
            chain.any { it is SSLException } -> "Falha na conexão TLS."
            chain.any { it is SocketTimeoutException } -> "Tempo de espera esgotado."
            chain.any { it is NoRouteToHostException } -> "Sem rota para o portal pela rede selecionada."
            chain.any { it is ConnectException } -> "Não foi possível estabelecer conexão com a porta do portal."
            chain.any { it is SecurityException } -> "O sistema negou acesso à conexão."
            chain.any { it is SocketException } -> "Falha no socket da conexão."
            else -> "Falha durante o diagnóstico."
        }
        val types = chain.joinToString(" / ") { it.javaClass.simpleName }
        return "$reason\nTipos técnicos: $types\nA causa não pode ser atribuída à sessão ou à VPN só por este resultado."
    }
}
