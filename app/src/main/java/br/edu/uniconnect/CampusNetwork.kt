package br.edu.uniconnect

object CampusNetwork {
    // Hyphenated name observed on-device; spaced names retained from the official guide.
    fun accepts(ssid: String?): Boolean = ssid in setOf(
        "UFDPar-A", "UFDPar-B", "UFDPar-C", "UFDPar A", "UFDPar B", "UFDPar C"
    )
}
