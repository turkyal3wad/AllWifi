package com.example.data.model

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val securityType: String,
    val isHidden: Boolean = false,
    val isSaved: Boolean = false,
    val isConnected: Boolean = false
) {
    val level: Int = calculateSignalLevel(rssi, 5) // 0 to 4

    val band: String = when (frequency) {
        in 2400..2484 -> "2.4 GHz"
        in 4900..5900 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        else -> "Unknown"
    }

    val channel: Int = when {
        frequency in 2401..2473 -> (frequency - 2407) / 5
        frequency == 2484 -> 14
        frequency in 5170..5825 -> (frequency - 5000) / 5
        frequency in 5945..7105 -> (frequency - 5940) / 5
        else -> 0
    }

    companion object {
        fun calculateSignalLevel(rssi: Int, numLevels: Int): Int {
            return when {
                rssi <= -100 -> 0
                rssi >= -55 -> numLevels - 1
                else -> {
                    val inputRange = -55 - (-100)
                    val outputRange = numLevels - 1
                    val progress = (rssi - (-100)).toFloat() / inputRange
                    (progress * outputRange).toInt().coerceIn(0, numLevels - 1)
                }
            }
        }
        
        fun getSecurityType(capabilities: String): String {
            return when {
                capabilities.contains("WPA3", ignoreCase = true) -> "WPA3"
                capabilities.contains("WPA2", ignoreCase = true) -> "WPA2"
                capabilities.contains("WPA", ignoreCase = true) -> "WPA"
                capabilities.contains("WEP", ignoreCase = true) -> "WEP"
                capabilities.contains("EAP", ignoreCase = true) -> "WPA-EAP"
                else -> "Open"
            }
        }
    }
}
