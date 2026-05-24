package com.example.ui.screens

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.FileItem
import com.example.data.database.PairedDevice
import com.example.ui.theme.NeonCyan
import com.example.ui.viewmodels.ChatViewModel
import com.example.ui.viewmodels.FileExplorerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    explorerViewModel: FileExplorerViewModel,
    chatViewModel: ChatViewModel,
    currentDevice: PairedDevice?,
    isDeviceOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val currentPath by explorerViewModel.currentPath.collectAsState()
    val items by explorerViewModel.items.collectAsState()
    val isRoot by explorerViewModel.isRoot.collectAsState()
    val isLoading by explorerViewModel.isLoading.collectAsState()
    val errorMsg by explorerViewModel.errorMessage.collectAsState()

    var showDownloadDialog by remember { mutableStateOf<FileItem?>(null) }

    LaunchedEffect(currentDevice, isDeviceOnline) {
        if (isDeviceOnline && currentDevice != null && currentPath == null) {
            explorerViewModel.loadDirectory(currentDevice, null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PC Dosya Gezgini", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        if (currentDevice == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text("Bağlantı Yok", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Lütfen eşleştirme sayfasından bilgisayarınızı bağlayın.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (!isDeviceOnline) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.error)
                    Text("Bilgisayar aranıyor...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Address Bar / Breadcrumbs
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { explorerViewModel.goUp(currentDevice) },
                            enabled = !isRoot && !isLoading
                        ) {
                            Icon(Icons.Filled.ArrowUpward, "Üst Klasör")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = currentPath ?: "Yükleniyor...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { explorerViewModel.loadDirectory(currentDevice, currentPath) }) {
                                Icon(Icons.Filled.Refresh, "Yenile", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // Error message banner
                errorMsg?.let { err ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }

                // Items list
                if (items.isEmpty() && !isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Klasör Boş", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(items) { item ->
                            FileExplorerRowItem(
                                item = item,
                                onClick = { explorerViewModel.openItem(currentDevice, item) },
                                onActionClick = { if (!item.is_dir) showDownloadDialog = item }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }

    // Download Confirmation Dialog
    showDownloadDialog?.let { fileItem ->
        AlertDialog(
            onDismissRequest = { showDownloadDialog = null },
            icon = { Icon(Icons.Filled.Download, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(32.dp)) },
            title = { Text("Telefona Kaydet", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text("Bu dosyayı PC'den telefonun indirilenler klasörüne kaydetmek istiyor musunuz?", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Dosya Adı: ${fileItem.name}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Boyut: ${formatFileSize(fileItem.size)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("PC Yolu: ${fileItem.path}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        currentDevice?.let { dev ->
                            chatViewModel.downloadFileToPhone(dev, fileItem.path, fileItem.name) { _ -> }
                        }
                        showDownloadDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Dosyayı İndir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = null }) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
fun FileExplorerRowItem(
    item: FileItem,
    onClick: () -> Unit,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.is_dir) Icons.Filled.Folder else Icons.Filled.Description,
            contentDescription = null,
            tint = if (item.is_dir) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontWeight = if (item.is_dir) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.is_dir) {
                Text(
                    text = formatFileSize(item.size),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!item.is_dir) {
            IconButton(onClick = { onActionClick() }) {
                Icon(Icons.Filled.Download, "İndir", tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.toDouble())).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.toDouble(), digitGroups.toDouble()), units[digitGroups])
}
