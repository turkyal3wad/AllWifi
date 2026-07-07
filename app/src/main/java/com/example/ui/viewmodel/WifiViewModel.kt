package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SavedNetworkEntity
import com.example.data.model.ConnectedDetails
import com.example.data.model.WifiNetwork
import com.example.data.repository.WifiRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOption {
    SIGNAL_STRENGTH, SSID
}

enum class FilterOption {
    ALL, OPEN, SECURED
}

class WifiViewModel(private val repository: WifiRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.SIGNAL_STRENGTH)
    val sortOption = _sortOption.asStateFlow()

    private val _filterOption = MutableStateFlow(FilterOption.ALL)
    val filterOption = _filterOption.asStateFlow()

    private val _autoRefreshEnabled = MutableStateFlow(true)
    val autoRefreshEnabled = _autoRefreshEnabled.asStateFlow()

    val isScanning: StateFlow<Boolean> = repository.isScanning
    val connectionProgress: StateFlow<String?> = repository.connectionProgress
    val connectedDetails: StateFlow<ConnectedDetails> = repository.connectedDetails

    val savedNetworks: StateFlow<List<SavedNetworkEntity>> = repository.savedNetworksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val wifiNetworks: StateFlow<List<WifiNetwork>> = combine(
        repository.getWifiNetworksFlow(),
        _searchQuery,
        _sortOption,
        _filterOption
    ) { networks, query, sort, filter ->
        var resultList = networks

        if (query.isNotEmpty()) {
            resultList = resultList.filter { it.ssid.contains(query, ignoreCase = true) }
        }

        resultList = when (filter) {
            FilterOption.ALL -> resultList
            FilterOption.OPEN -> resultList.filter { it.securityType == "Open" }
            FilterOption.SECURED -> resultList.filter { it.securityType != "Open" }
        }

        resultList = when (sort) {
            SortOption.SIGNAL_STRENGTH -> resultList.sortedBy { it.rssi } // RSSI is negative, sortedBy (ascending) puts stronger signals (e.g., -50) after weaker ones (e.g., -90). Let's sort so stronger is first.
            SortOption.SSID -> resultList.sortedBy { it.ssid.lowercase() }
        }

        // Since we want stronger signal first, we sort by signal strength descending.
        // Wait, RSSI values range from e.g. -30 (very strong) to -100 (very weak).
        // Sorting by RSSI DESCENDING (using sortedByDescending) puts stronger signals first. Let's fix that below!
        if (sort == SortOption.SIGNAL_STRENGTH) {
            resultList = resultList.sortedByDescending { it.rssi }
        }

        resultList
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var autoRefreshJob: Job? = null

    init {
        startAutoRefresh()
    }

    fun startScan() {
        viewModelScope.launch {
            repository.startScan()
        }
    }

    fun toggleAutoRefresh(enabled: Boolean) {
        _autoRefreshEnabled.value = enabled
        if (enabled) {
            startAutoRefresh()
        } else {
            stopAutoRefresh()
        }
    }

    private fun startAutoRefresh() {
        stopAutoRefresh()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                if (_autoRefreshEnabled.value) {
                    repository.startScan()
                }
                delay(10000)
            }
        }
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun setFilterOption(option: FilterOption) {
        _filterOption.value = option
    }

    fun connectToNetwork(ssid: String, securityType: String, password: String) {
        repository.connectToNetwork(ssid, securityType, password, viewModelScope)
    }

    fun disconnect() {
        repository.disconnectFromCurrentNetwork()
    }

    fun toggleWifi(enabled: Boolean): Boolean {
        return repository.setWifiEnabled(enabled)
    }

    fun saveNetworkManual(ssid: String, securityType: String, password: String) {
        viewModelScope.launch {
            repository.saveNetworkToDb(ssid, securityType, password)
        }
    }

    fun deleteSavedNetwork(ssid: String) {
        viewModelScope.launch {
            repository.deleteNetworkFromDb(ssid)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
        repository.unregisterNetworkCallback()
    }

    class Factory(private val repository: WifiRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WifiViewModel::class.java)) {
                return WifiViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
