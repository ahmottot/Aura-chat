package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.CompanionClient
import com.example.data.api.SystemInfoResponse
import com.example.data.database.PairedDevice
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TechGreen
import com.example.ui.theme.TechPurple
import com.example.ui.viewmodels.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentDevice by mainViewModel.currentDevice.collectAsState()
    val isOnline by mainViewModel.isDeviceOnline.collectAsState()

    var systemInfo by remember { mutableStateOf<SystemInfoResponse?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var isLiveOpen by remember { mutableStateOf(false) }
    var liveTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(isLiveOpen) {
        if (isLiveOpen) {
            while (isLiveOpen) {
                delay(1500)
                liveTrigger += 1
            }
        }
    }

    val refreshInfo = {
        currentDevice?.let { dev ->
            isRefreshing = true
            scope.launch {
                val url = dev.buildUrl("/info")
                try {
                    val result = withContext(Dispatchers.IO) {
                        CompanionClient.service.getSystemInfo(url, dev.pinCode)
                    }
                    systemInfo = result
                } catch (e: Exception) {
                    systemInfo = null
                } finally {
                    isRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(currentDevice, isOnline) {
        if (isOnline) {
            refreshInfo()
        } else {
            systemInfo = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PC Durum Paneli", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { refreshInfo() }, enabled = isOnline && !isRefreshing) {
                        Icon(Icons.Filled.Refresh, "Yenile")
                    }
                },
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
                        Icons.Filled.Computer,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text("Bağlı Bilgisayar Yok", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Lütfen yan menüden eşleştirme sayfasına giderek bilgisayarınızı bağlayın.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (!isOnline) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.error)
                    Text("Bilgisayar Bekleniyor...", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Eşleşen cihaz: ${currentDevice?.name}\nSunucu aktif olduğunda bu ekran otomatik güncellenecektir.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Connection Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.DesktopWindows,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = NeonCyan
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                systemInfo?.hostname ?: currentDevice?.name ?: "PC Name",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Konum: ${currentDevice?.ipAddress}:${currentDevice?.port}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "İşletim Sistemi: ${systemInfo?.os ?: "Okunuyor..."}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Dials row for CPU and RAM usage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TelemetrySpeedometerCard(
                        title = "İşlemci (CPU)",
                        percentage = systemInfo?.cpu_usage?.toFloat() ?: 0f,
                        color = TechGreen,
                        modifier = Modifier.weight(1f)
                    )

                    TelemetrySpeedometerCard(
                        title = "Bellek (RAM)",
                        percentage = systemInfo?.memory_usage?.toFloat() ?: 0f,
                        color = TechPurple,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Detailed health properties card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Sistem İndikatörleri", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        StatsRowItem(
                            icon = Icons.Filled.Storage,
                            label = "Boş Disk Alanı",
                            value = systemInfo?.disk_free ?: "Hesaplanıyor..."
                        )

                        StatsRowItem(
                            icon = Icons.Filled.OpenInNew,
                            label = "Aktif Uygulama Window",
                            value = systemInfo?.active_app ?: "Boşta / Masaüstü"
                        )

                        StatsRowItem(
                            icon = Icons.Filled.Pin,
                            label = "Güvenlik PIN Modu",
                            value = "Doğrulandı (${currentDevice?.pinCode})"
                        )
                    }
                }

                // Live Screen Card
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("live_screen_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Filled.Tv,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = NeonCyan
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Ekranı Canlı İzle", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "Bilgisayar ekranını canlı izleyebilirsiniz",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isOnline) {
                            Button(
                                onClick = { isLiveOpen = true },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                                modifier = Modifier.testTag("start_live_btn")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CANLI", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        } else {
                            Text("Çevrimdışı", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (isLiveOpen && currentDevice != null) {
                    val dev = currentDevice!!
                    val liveUrl = dev.buildUrl("/screenshot?pin=${dev.pinCode}&t=$liveTrigger")

                    Dialog(onDismissRequest = { isLiveOpen = false }) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(Color.Red)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("CANLI EKRAN İZLEME", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    IconButton(onClick = { isLiveOpen = false }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Close, contentDescription = "Kapat")
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 10f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = liveUrl,
                                        contentDescription = "PC Ekranı Canlı Akış",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }

                                Text(
                                    "Ekran görüntüsü 1.5 saniyede bir otomatik yenilenmektedir.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Quick explanation banner
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.AutoAwesome, "Komut", tint = NeonCyan, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Yapay Zeka ile Kontrol", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Yan sekmedeki Aura Chat ekranına geçerek yapay zekaya 'ekran görüntüsünü at' veya 'dosyayı ara ve indir' şeklinde Türkçe seslenebilirsiniz. Yapay zeka bu ekranın verilerini doğrudan eş zamanlı okuyarak cevaplar.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetrySpeedometerCard(
    title: String,
    percentage: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                val animatedProgress by animateFloatAsState(targetValue = percentage / 100f)

                Canvas(modifier = Modifier.size(80.dp)) {
                    // Background Circle
                    drawCircle(
                        color = color.copy(alpha = 0.15f),
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Foreground Sweep Arc
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${percentage.toInt()}%",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun StatsRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
