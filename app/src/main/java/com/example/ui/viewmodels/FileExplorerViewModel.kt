package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.CompanionClient
import com.example.data.api.FileItem
import com.example.data.database.PairedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileExplorerViewModel(application: Application) : AndroidViewModel(application) {
    private val _currentPath = MutableStateFlow<String?>(null)
    val currentPath: StateFlow<String?> = _currentPath.asStateFlow()

    private val _parentPath = MutableStateFlow<String?>(null)
    val parentPath: StateFlow<String?> = _parentPath.asStateFlow()

    private val _items = MutableStateFlow<List<FileItem>>(emptyList())
    val items: StateFlow<List<FileItem>> = _items.asStateFlow()

    private val _isRoot = MutableStateFlow(true)
    val isRoot: StateFlow<Boolean> = _isRoot.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadDirectory(device: PairedDevice?, targetPath: String? = null) {
        if (device == null) {
            _errorMessage.value = "Hata: Bağlı bilgisayar yok."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val url = device.buildUrl("/files")
            try {
                val response = withContext(Dispatchers.IO) {
                    CompanionClient.service.listFiles(url, targetPath, device.pinCode)
                }

                if (response.status == "success") {
                    _items.value = response.items.sortedWith(
                        compareByDescending<FileItem> { it.is_dir }.thenBy { it.name.lowercase() }
                    )
                    _currentPath.value = response.current_path
                    _parentPath.value = response.parent
                    _isRoot.value = response.is_root
                } else {
                    _errorMessage.value = "Dizin listelenemedi."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Sunucuya bağlanılamadı: " + (e.localizedMessage ?: "Bağlantı kesildi.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun openItem(device: PairedDevice?, item: FileItem) {
        if (item.is_dir) {
            loadDirectory(device, item.path)
        }
    }

    fun goUp(device: PairedDevice?) {
        val parent = _parentPath.value
        if (parent != null && !_isRoot.value) {
            loadDirectory(device, parent)
        }
    }
}
