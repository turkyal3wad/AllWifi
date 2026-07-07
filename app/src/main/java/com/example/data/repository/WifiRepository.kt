package com.example.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import com.example.data.local.SavedNetworkDao
import com.example.data.local.SavedNetworkEntity
import com.example.data.model.ConnectedDetails
import com.example.data.model.WifiNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

class WifiRepository(
    private val context: Context,
    private val savedNetworkDao: SavedNetworkDao
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    private val _connectedDetails = MutableStateFlow(ConnectedDetails())
    val connectedDetails: StateFlow<ConnectedDetails> = _connectedDetails.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionProgress = MutableStateFlow<String?>(null)
    val connectionProgress: StateFlow<String?> = _connectionProgress.asStateFlow()

    val savedNetworksFlow: Flow<List<SavedNetworkEntity>> = savedNetworkDao.getAllSavedNetworks()

    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        registerNetworkCallback()
        updateConnectedDetails()
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        _isScanning.value = true
        try {
            @Suppress("Deprecation")
            val success = wifiManager.startScan()
            if (success) {
                val results = wifiManager.scanResults ?: emptyList()
                _scanResults.value = results
            } else {
                val results = wifiManager.scanResults ?: emptyList()
                _scanResults.value = results
            }
        } catch (e: Exception) {
            Log.e("WifiRepository", "Scan failed", e)
        } finally {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun getCachedScanResults(): List<ScanResult> {
        return try {
            wifiManager.scanResults ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun getWifiNetworksFlow(): Flow<List<WifiNetwork>> {
        return combine(
            _scanResults,
            savedNetworksFlow,
            _connectedDetails
        ) { scanned, saved, connected ->
            scanned.map { result ->
                val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.wifiSsid?.toString()?.removePrefix("\"")?.removeSuffix("\"") ?: result.SSID
                } else {
                    result.SSID
                }.replace("\"", "")

                val isConnected = connected.isConnected && connected.bssid == result.BSSID
                val isSaved = saved.any { it.ssid == ssid }
                val capabilities = result.capabilities
                val security = WifiNetwork.getSecurityType(capabilities)

                WifiNetwork(
                    ssid = ssid.ifEmpty { "[Hidden Network]" },
                    bssid = result.BSSID,
                    rssi = result.level,
                    frequency = result.frequency,
                    securityType = security,
                    isHidden = result.capabilities.contains("ESS") && ssid.isEmpty(),
                    isSaved = isSaved,
                    isConnected = isConnected
                )
            }.distinctBy { it.ssid + "_" + it.bssid }
        }
    }

    @SuppressLint("MissingPermission")
    fun updateConnectedDetails() {
        val info: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val activeNet = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(activeNet)
            caps?.transportInfo as? WifiInfo
        } else {
            @Suppress("Deprecation")
            wifiManager.connectionInfo
        }

        if (info != null && info.networkId != -1) {
            val ssid = info.ssid?.replace("\"", "") ?: "Connected"
            val bssid = info.bssid ?: ""
            val matchingScanResult = _scanResults.value.find { it.BSSID == bssid }
            
            val ipVal = info.ipAddress
            val ipStr = formatIpAddress(ipVal)

            val linkProperties = connectivityManager.getLinkProperties(connectivityManager.activeNetwork)
            val gatewayStr = linkProperties?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress ?: "0.0.0.0"
            val dnsServers = linkProperties?.dnsServers?.map { it.hostAddress } ?: emptyList()
            val dns1Str = dnsServers.getOrNull(0) ?: "0.0.0.0"
            val dns2Str = dnsServers.getOrNull(1) ?: "0.0.0.0"

            val prefix = linkProperties?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.prefixLength ?: 24
            val maskInt = (0xffffffff shl (32 - prefix)).toInt()
            val subnetMaskStr = "${(maskInt ushr 24) and 0xff}.${(maskInt ushr 16) and 0xff}.${(maskInt ushr 8) and 0xff}.${maskInt and 0xff}"

            val macStr = if (info.macAddress != "02:00:00:00:00:00") {
                info.macAddress
            } else {
                getDeviceMacAddress() ?: "02:00:00:00:00:00 (Restricted)"
            }

            val linkSpeedVal = info.linkSpeed
            val rxSpeedVal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.rxLinkSpeedMbps else 0
            val txSpeedVal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.txLinkSpeedMbps else 0

            val standardStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                when (info.wifiStandard) {
                    ScanResult.WIFI_STANDARD_LEGACY -> "802.11a/b/g"
                    ScanResult.WIFI_STANDARD_11N -> "802.11n (Wi-Fi 4)"
                    ScanResult.WIFI_STANDARD_11AC -> "802.11ac (Wi-Fi 5)"
                    ScanResult.WIFI_STANDARD_11AX -> "802.11ax (Wi-Fi 6)"
                    ScanResult.WIFI_STANDARD_11BE -> "802.11be (Wi-Fi 7)"
                    else -> "802.11 (Wi-Fi)"
                }
            } else {
                "802.11ac (Wi-Fi 5)"
            }

            val widthStr = if (matchingScanResult != null) {
                when (matchingScanResult.channelWidth) {
                    ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
                    ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
                    ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
                    ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
                    ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
                    ScanResult.CHANNEL_WIDTH_320MHZ -> "320 MHz"
                    else -> "Unknown Width"
                }
            } else {
                "20 MHz"
            }

            _connectedDetails.value = ConnectedDetails(
                ssid = ssid,
                bssid = bssid,
                ipAddress = ipStr,
                gateway = gatewayStr,
                dns1 = dns1Str,
                dns2 = dns2Str,
                subnetMask = subnetMaskStr,
                macAddress = macStr,
                linkSpeed = linkSpeedVal,
                rxLinkSpeed = rxSpeedVal,
                txLinkSpeed = txSpeedVal,
                wifiStandard = standardStr,
                frequency = info.frequency,
                channelWidth = widthStr,
                rssi = info.rssi,
                isConnected = true
            )
        } else {
            _connectedDetails.value = ConnectedDetails()
        }
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        activeNetworkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateConnectedDetails()
            }

            override fun onLost(network: Network) {
                updateConnectedDetails()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                updateConnectedDetails()
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, activeNetworkCallback!!)
        } catch (e: Exception) {
            Log.e("WifiRepository", "Failed to register network callback", e)
        }
    }

    fun unregisterNetworkCallback() {
        activeNetworkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e("WifiRepository", "Failed to unregister network callback", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToNetwork(ssid: String, securityType: String, password: String, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            _connectionProgress.value = "Saving network profile..."
            val existing = savedNetworkDao.getNetworkBySsid(ssid)
            if (existing == null) {
                savedNetworkDao.insertSavedNetwork(
                    SavedNetworkEntity(
                        ssid = ssid,
                        securityType = securityType,
                        password = password
                    )
                )
            }

            _connectionProgress.value = "Connecting to $ssid..."
            try {
                val suggestionBuilder = WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)

                if (securityType != "Open" && password.isNotEmpty()) {
                    when (securityType) {
                        "WPA2", "WPA" -> suggestionBuilder.setWpa2Passphrase(password)
                        "WPA3" -> suggestionBuilder.setWpa3Passphrase(password)
                        "WEP" -> {
                            connectUsingSpecifier(ssid, securityType, password)
                            return@launch
                        }
                    }
                }

                val status = wifiManager.addNetworkSuggestions(listOf(suggestionBuilder.build()))
                if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                    _connectionProgress.value = "Suggested connection successfully. Android connecting automatically..."
                    delay(3000)
                    _connectionProgress.value = null
                } else {
                    connectUsingSpecifier(ssid, securityType, password)
                }
            } catch (e: Exception) {
                Log.e("WifiRepository", "Suggestion failed, trying specifier", e)
                connectUsingSpecifier(ssid, securityType, password)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectUsingSpecifier(ssid: String, securityType: String, password: String) {
        _connectionProgress.value = "Opening connection request screen..."
        try {
            val specifierBuilder = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)

            if (securityType != "Open" && password.isNotEmpty()) {
                when (securityType) {
                    "WPA2", "WPA" -> specifierBuilder.setWpa2Passphrase(password)
                    "WPA3" -> specifierBuilder.setWpa3Passphrase(password)
                }
            }

            val specifier = specifierBuilder.build()
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    _connectionProgress.value = "Connected to $ssid!"
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(2000)
                        _connectionProgress.value = null
                    }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    _connectionProgress.value = "Connection declined or timed out."
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(3000)
                        _connectionProgress.value = null
                    }
                }
            })
        } catch (e: Exception) {
            _connectionProgress.value = "Connection failed: ${e.message}"
            CoroutineScope(Dispatchers.IO).launch {
                delay(3000)
                _connectionProgress.value = null
            }
        }
    }

    fun disconnectFromCurrentNetwork() {
        try {
            wifiManager.removeNetworkSuggestions(emptyList())
            _connectionProgress.value = "Network suggestions cleared. Open settings to fully disconnect."
            CoroutineScope(Dispatchers.IO).launch {
                delay(2500)
                _connectionProgress.value = null
            }
        } catch (e: Exception) {
            Log.e("WifiRepository", "Disconnect error", e)
        }
    }

    fun setWifiEnabled(enabled: Boolean): Boolean {
        return try {
            @Suppress("Deprecation")
            wifiManager.setWifiEnabled(enabled)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun saveNetworkToDb(ssid: String, securityType: String, password: String) {
        savedNetworkDao.insertSavedNetwork(
            SavedNetworkEntity(
                ssid = ssid,
                securityType = securityType,
                password = password
            )
        )
    }

    suspend fun deleteNetworkFromDb(ssid: String) {
        savedNetworkDao.deleteBySsid(ssid)
    }

    private fun formatIpAddress(ip: Int): String {
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            (ip shr 8) and 0xff,
            (ip shr 16) and 0xff,
            (ip shr 24) and 0xff
        )
    }

    private fun getDeviceMacAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.name.equals("wlan0", ignoreCase = true)) {
                    val mac = intf.hardwareAddress ?: return null
                    val buf = StringBuilder()
                    for (b in mac) {
                        buf.append(String.format("%02X:", b))
                    }
                    if (buf.isNotEmpty()) {
                        buf.deleteCharAt(buf.length - 1)
                    }
                    return buf.toString()
                }
            }
        } catch (ex: Exception) {
            Log.e("WifiRepository", "MAC access failed", ex)
        }
        return null
    }
}
