package com.example.data.model

data class ConnectedDetails(
    val ssid: String = "Not Connected",
    val bssid: String = "",
    val ipAddress: String = "0.0.0.0",
    val gateway: String = "0.0.0.0",
    val dns1: String = "0.0.0.0",
    val dns2: String = "0.0.0.0",
    val subnetMask: String = "255.255.255.0",
    val macAddress: String = "02:00:00:00:00:00 (Restricted)",
    val linkSpeed: Int = 0,
    val rxLinkSpeed: Int = 0,
    val txLinkSpeed: Int = 0,
    val wifiStandard: String = "802.11ac",
    val frequency: Int = 0,
    val channelWidth: String = "Unknown",
    val rssi: Int = -100,
    val isConnected: Boolean = false
) {
    val signalStrengthPercent: Int
        get() = when {
            rssi <= -100 -> 0
            rssi >= -50 -> 100
            else -> ((rssi - (-100)).toFloat() / 50f * 100f).toInt().coerceIn(0, 100)
        }
}
