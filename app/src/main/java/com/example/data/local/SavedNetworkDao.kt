package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedNetworkDao {
    @Query("SELECT * FROM saved_networks ORDER BY timestamp DESC")
    fun getAllSavedNetworks(): Flow<List<SavedNetworkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedNetwork(network: SavedNetworkEntity)

    @Delete
    suspend fun deleteSavedNetwork(network: SavedNetworkEntity)

    @Query("DELETE FROM saved_networks WHERE ssid = :ssid")
    suspend fun deleteBySsid(ssid: String)

    @Query("SELECT * FROM saved_networks WHERE ssid = :ssid LIMIT 1")
    suspend fun getNetworkBySsid(ssid: String): SavedNetworkEntity?
}
