package br.edu.uniconnect

import org.junit.Assert.*
import org.junit.Test

class CampusNetworkTest {
    @Test fun `accept observed and documented names without broadening to unrelated networks`() {
        listOf("UFDPar-A", "UFDPar-B", "UFDPar-C", "UFDPar A", "UFDPar B", "UFDPar C").forEach {
            assertTrue(it, CampusNetwork.accepts(it))
        }
        listOf(null, "<unknown ssid>", "UFDPar-A-Guest", "Minha UFDPar-A", "eduroam").forEach {
            assertFalse(CampusNetwork.accepts(it))
        }
    }
}
