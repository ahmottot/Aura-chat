package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.PairedDevice
import com.example.data.api.CompanionClient
import com.example.data.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DeviceRepository

    val pairedDevices: StateFlow<List<PairedDevice>>
    val currentDevice: StateFlow<PairedDevice?>

    private val _isDeviceOnline = MutableStateFlow(false)
    val isDeviceOnline: StateFlow<Boolean> = _isDeviceOnline.asStateFlow()

    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

    private val _autoConnectEvent = MutableStateFlow<String?>(null)
    val autoConnectEvent: StateFlow<String?> = _autoConnectEvent.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        val dao = database.deviceDao()
        repository = DeviceRepository(dao)

        pairedDevices = repository.allDevices.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        currentDevice = repository.currentDeviceFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        // Heartbeat monitor for local connection status
        viewModelScope.launch {
            while (true) {
                checkCurrentDeviceHealth()
                delay(10000) // check every 10 seconds
            }
        }

        // Start background TCP socket listener for PC boot events (Auto-Connect)
        startAutoConnectListener()
    }

    private fun startAutoConnectListener() {
        viewModelScope.launch(Dispatchers.IO) {
            var serverSocket: java.net.ServerSocket? = null
            try {
                serverSocket = java.net.ServerSocket(5556)
                while (true) {
                    val socket = serverSocket.accept()
                    val reader = socket.getInputStream().bufferedReader()
                    val line = reader.readLine()
                    if (line != null && line.startsWith("AURALINK_ONLINE")) {
                        val parts = line.split("|")
                        if (parts.size >= 4) {
                            val ip = parts[1]
                            val port = parts[2].toIntOrNull() ?: 5555
                            val pin = parts[3]
                            withContext(Dispatchers.Main) {
                                performAutoConnect(ip, port, pin)
                            }
                        }
                    }
                    socket.close()
                }
            } catch (e: Exception) {
                // background listener error/restarting after a short delay
                delay(5000)
                startAutoConnectListener()
            } finally {
                try { serverSocket?.close() } catch (ex: Exception) {}
            }
        }
    }

    private fun performAutoConnect(ip: String, port: Int, pin: String) {
        viewModelScope.launch {
            val devices = pairedDevices.value
            val matchedDevice = devices.firstOrNull { it.pinCode == pin }
                ?: devices.firstOrNull { it.ipAddress == ip && it.pinCode == pin }

            if (matchedDevice != null) {
                val updatedDevice = matchedDevice.copy(ipAddress = ip, port = port)
                repository.connectDevice(updatedDevice)
                _isDeviceOnline.value = true

                android.widget.Toast.makeText(
                    getApplication(),
                    "AuraLink: Bilgisayarınız (${updatedDevice.name}) otomatik olarak bağlandı!",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                _autoConnectEvent.value = "AuraLink: ${updatedDevice.name} bilgisayarınız açıldı ve otomatik bağlandı!"
            } else {
                val existingDevice = devices.firstOrNull()
                if (existingDevice != null && existingDevice.pinCode == pin) {
                    val updatedDevice = existingDevice.copy(ipAddress = ip, port = port)
                    repository.connectDevice(updatedDevice)
                    _isDeviceOnline.value = true

                    android.widget.Toast.makeText(
                        getApplication(),
                        "AuraLink: Bilgisayarınız otomatik bağlandı!",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    _autoConnectEvent.value = "AuraLink: Bilgisayarınız açıldı ve otomatik bağlandı!"
                }
            }
        }
    }

    fun dismissAutoConnectEvent() {
        _autoConnectEvent.value = null
    }

    suspend fun checkCurrentDeviceHealth() {
        val device = repository.getCurrentDevice()
        if (device == null) {
            _isDeviceOnline.value = false
            return
        }
        val url = "http://${device.ipAddress}:${device.port}/info"
        try {
            withContext(Dispatchers.IO) {
                val response = CompanionClient.service.getSystemInfo(url, device.pinCode)
                _isDeviceOnline.value = response.status == "success"
            }
        } catch (e: Exception) {
            _isDeviceOnline.value = false
        }
    }

    fun pairNewDevice(ip: String, portString: String, pin: String) {
        val port = portString.toIntOrNull() ?: 5555
        _pairingState.value = PairingState.Loading

        viewModelScope.launch {
            val url = "http://$ip:$port/pair"
            try {
                val response = withContext(Dispatchers.IO) {
                    CompanionClient.service.pairHost(url, pin)
                }
                if (response.status == "success") {
                    val device = PairedDevice(
                        name = response.computer_name,
                        ipAddress = ip,
                        port = port,
                        pinCode = pin
                    )
                    repository.connectDevice(device)
                    _pairingState.value = PairingState.Success(response.computer_name)
                    checkCurrentDeviceHealth()
                } else {
                    _pairingState.value = PairingState.Error("Eşleşme sunucu tarafından reddedildi.")
                }
            } catch (e: Exception) {
                _pairingState.value = PairingState.Error("Bağlantı başarısız: " + (e.localizedMessage ?: "Cihaza ulaşılamadı. IP ve PIN kodunu kontrol edin."))
            }
        }
    }

    fun disconnectActiveDevice() {
        viewModelScope.launch {
            repository.disconnectDevice()
            _isDeviceOnline.value = false
        }
    }

    fun deleteDevice(deviceId: Int) {
        viewModelScope.launch {
            val current = repository.getCurrentDevice()
            if (current?.id == deviceId) {
                repository.disconnectDevice()
                _isDeviceOnline.value = false
            }
            repository.deleteDevice(deviceId)
        }
    }

    fun selectCurrentDevice(device: PairedDevice) {
        viewModelScope.launch {
            repository.connectDevice(device)
            checkCurrentDeviceHealth()
        }
    }

    fun resetPairingState() {
        _pairingState.value = PairingState.Idle
    }
}

sealed class PairingState {
    object Idle : PairingState()
    object Loading : PairingState()
    data class Success(val computerName: String) : PairingState()
    data class Error(val message: String) : PairingState()
}
