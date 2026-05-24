package com.example.data.repository

import com.example.data.database.ChatMessage
import com.example.data.database.DeviceDao
import com.example.data.database.PairedDevice
import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val deviceDao: DeviceDao) {
    val allDevices: Flow<List<PairedDevice>> = deviceDao.getAllDevices()
    val currentDeviceFlow: Flow<PairedDevice?> = deviceDao.getCurrentDeviceFlow()
    val chatHistory: Flow<List<ChatMessage>> = deviceDao.getChatHistory()

    suspend fun getCurrentDevice(): PairedDevice? = deviceDao.getCurrentDevice()

    suspend fun connectDevice(device: PairedDevice) {
        deviceDao.setCurrentDevice(device)
    }

    suspend fun disconnectDevice() {
        deviceDao.clearCurrentDevices()
    }

    suspend fun deleteDevice(deviceId: Int) {
        deviceDao.deleteDeviceById(deviceId)
    }

    suspend fun addMessage(message: ChatMessage): Long {
        return deviceDao.insertMessage(message)
    }

    suspend fun clearHistory() {
        deviceDao.clearChatHistory()
    }
}
