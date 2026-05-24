package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.PairedDevice
import com.example.ui.theme.NeonCyan
import com.example.ui.viewmodels.MainViewModel
import com.example.ui.viewmodels.PairingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: MainViewModel,
    onPairingSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pairingState by viewModel.pairingState.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val currentDevice by viewModel.currentDevice.collectAsState()
    val isOnline by viewModel.isDeviceOnline.collectAsState()

    var ipAddress by remember { mutableStateOf("192.168.1.") }
    var port by remember { mutableStateOf("5555") }
    var pinCode by remember { mutableStateOf("") }

    val clipboardManager = LocalClipboardManager.current
    var showInstructions by remember { mutableStateOf(true) }

    LaunchedEffect(pairingState) {
        if (pairingState is PairingState.Success) {
            onPairingSuccess()
            viewModel.resetPairingState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.SettingsInputHdmi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            "Aura Link Eşleştirme",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active Connection Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOnline) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (isOnline) Color(0xFF20C997) else Color(0xFFDF3B3B))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isOnline) "Bilgisayar Bağlantısı Aktif" else "Bağlantı Kesildi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            currentDevice?.let { dev ->
                                Text(
                                    "Cihaz: ${dev.name} (${dev.ipAddress}:${dev.port})",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } ?: run {
                                Text(
                                    "Lütfen eşleştirmek istediğiniz bilgisayarın IP ve PIN kodunu girin.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (currentDevice != null) {
                            IconButton(onClick = { viewModel.disconnectActiveDevice() }) {
                                Icon(Icons.Filled.LinkOff, "Bağlantıyı Kes", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Instructions toggler
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    onClick = { showInstructions = !showInstructions }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PC Sunucusu Nasıl Kurulur ve Başlatılır?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Icon(
                            imageVector = if (showInstructions) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            }

            // Quick Setup Guide
            item {
                AnimatedVisibility(visible = showInstructions) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("1️⃣ PC Companion Komutunu İndirin", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Proje dosyalarını indirdiğinizde 'desktop-companion' klasörünün içindeki 'pair_server.py' betiğini bilgisayarınıza atın. Python ve Pillow kütüphanesi kurulu olmalıdır.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text("2️⃣ Gerekli Kütüphaneleri Yükleyin", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth().clickable {
                                clipboardManager.setText(AnnotatedString("pip install pillow"))
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("pip install pillow", fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Kopyala", modifier = Modifier.size(16.dp))
                            }
                        }

                        Text("3️⃣ PC Sunucusunu Çalıştırın (Pardus / Windows)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth().clickable {
                                clipboardManager.setText(AnnotatedString("python pair_server.py"))
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("python pair_server.py", fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Kopyala", modifier = Modifier.size(16.dp))
                            }
                        }

                        Text("4️⃣ Bilgileri Telefona Yazın", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Sunucu açıldığında size konsolda ve local web sitesinde (http://localhost:5555) IP adresi ve 6 basamaklı PIN kodunu gösterecektir. Bu bilgileri aşağıya yazarak eşleştirin.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Verification Inputs form
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Yeni Cihaz Bağla",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { ipAddress = it },
                            label = { Text("PC Yerel IP Adresi (Örn: 192.168.1.10)") },
                            placeholder = { Text("192.168.1.X") },
                            leadingIcon = { Icon(Icons.Filled.Language, "IP") },
                            modifier = Modifier.fillMaxWidth().testTag("ip_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = port,
                                onValueChange = { port = it },
                                label = { Text("Port") },
                                leadingIcon = { Icon(Icons.Filled.NetworkCheck, "Port") },
                                modifier = Modifier.weight(1f).testTag("port_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            OutlinedTextField(
                                value = pinCode,
                                onValueChange = { pinCode = it },
                                label = { Text("PIN Kodu") },
                                trailingIcon = {
                                    IconButton(onClick = { pinCode = "" }) {
                                        Icon(Icons.Filled.Clear, "Temizle")
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Key, "Pass") },
                                modifier = Modifier.weight(1.3f).testTag("pin_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        // Display state progress/error
                        when (pairingState) {
                            PairingState.Loading -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Bağlantı kuruluyor, lütfen bekleyin...", fontSize = 13.sp)
                                }
                            }
                            is PairingState.Error -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                        .padding(12.dp)
                                ) {
                                    Icon(Icons.Filled.Error, "Hata", tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = (pairingState as PairingState.Error).message,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            else -> {}
                        }

                        Button(
                            onClick = { viewModel.pairNewDevice(ipAddress, port, pinCode) },
                            enabled = ipAddress.isNotBlank() && pinCode.isNotBlank() && pairingState !is PairingState.Loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("pair_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Eşleştir ve Bağlan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            // History Paired Devices List header
            if (pairedDevices.isNotEmpty()) {
                item {
                    Text(
                        "Kayıtlı Cihazlarınız",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(pairedDevices) { device ->
                    val isSelected = currentDevice?.id == device.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectCurrentDevice(device) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Computer,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(device.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${device.ipAddress}:${device.port}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isOnline) Color(0xFF20C997).copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = if (isOnline) "Aktif" else "Çevrimdışı",
                                            color = if (isOnline) Color(0xFF20C997) else MaterialTheme.colorScheme.error,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.deleteDevice(device.id) }) {
                                    Icon(Icons.Filled.DeleteOutline, "Kayıt Sil", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Spacer
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
