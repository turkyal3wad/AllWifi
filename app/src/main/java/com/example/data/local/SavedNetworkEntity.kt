package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_networks")
data class SavedNetworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ssid: String,
    val bssid: String = "",
    val securityType: String = "Open",
    val password: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
