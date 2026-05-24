package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM paired_devices ORDER BY pairedAt DESC")
    fun getAllDevices(): Flow<List<PairedDevice>>

    @Query("SELECT * FROM paired_devices WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrentDevice(): PairedDevice?

    @Query("SELECT * FROM paired_devices WHERE isCurrent = 1 LIMIT 1")
    fun getCurrentDeviceFlow(): Flow<PairedDevice?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: PairedDevice)

    @Update
    suspend fun updateDevice(device: PairedDevice)

    @Query("UPDATE paired_devices SET isCurrent = 0")
    suspend fun clearCurrentDevices()

    @Transaction
    suspend fun setCurrentDevice(device: PairedDevice) {
        clearCurrentDevices()
        insertDevice(device.copy(isCurrent = true))
    }

    @Query("DELETE FROM paired_devices WHERE id = :deviceId")
    suspend fun deleteDeviceById(deviceId: Int)

    // --- Chat Messages Query Block ---
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getChatHistory(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatHistory()
}
