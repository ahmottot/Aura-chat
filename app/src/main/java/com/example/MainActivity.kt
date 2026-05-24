package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FileBrowserScreen
import com.example.ui.screens.PairingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodels.ChatViewModel
import com.example.ui.viewmodels.FileExplorerViewModel
import com.example.ui.viewmodels.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    val navController = rememberNavController()

    // Initialize ViewModels
    val mainViewModel: MainViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()
    val explorerViewModel: FileExplorerViewModel = viewModel()

    val currentDevice by mainViewModel.currentDevice.collectAsState()
    val isDeviceOnline by mainViewModel.isDeviceOnline.collectAsState()
    val autoConnectEvent by mainViewModel.autoConnectEvent.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"

    if (autoConnectEvent != null) {
        AlertDialog(
            onDismissRequest = { mainViewModel.dismissAutoConnectEvent() },
            title = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = com.example.ui.theme.NeonCyan,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("AuraLink Bağlantısı", color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            text = { Text(autoConnectEvent!!, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { mainViewModel.dismissAutoConnectEvent() }) {
                    Text("Harika!")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("bottom_nav")
            ) {
                // Dashboard Tab
                NavigationBarItem(
                    selected = currentRoute == "dashboard",
                    label = { Text("Durum", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Filled.Dashboard, contentDescription = "Durum") },
                    onClick = {
                        navController.navigate("dashboard") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.testTag("nav_dashboard")
                )

                // Assistant Chat Tab
                NavigationBarItem(
                    selected = currentRoute == "chat",
                    label = { Text("Aura Chat", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "Aura Chat") },
                    onClick = {
                        navController.navigate("chat") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.testTag("nav_chat")
                )

                // Remote Files Tab
                NavigationBarItem(
                    selected = currentRoute == "files",
                    label = { Text("Dosyalar", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Filled.Folder, contentDescription = "PC Dosyaları") },
                    onClick = {
                        navController.navigate("files") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.testTag("nav_files")
                )

                // Pairing Tab
                NavigationBarItem(
                    selected = currentRoute == "pairing",
                    label = { Text("Eşle", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Filled.Sync, contentDescription = "Eşleştirme") },
                    onClick = {
                        navController.navigate("pairing") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.testTag("nav_pairing")
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") {
                DashboardScreen(mainViewModel = mainViewModel)
            }

            composable("chat") {
                ChatScreen(
                    chatViewModel = chatViewModel,
                    currentDevice = currentDevice,
                    isDeviceOnline = isDeviceOnline
                )
            }

            composable("files") {
                FileBrowserScreen(
                    explorerViewModel = explorerViewModel,
                    chatViewModel = chatViewModel,
                    currentDevice = currentDevice,
                    isDeviceOnline = isDeviceOnline
                )
            }

            composable("pairing") {
                PairingScreen(
                    viewModel = mainViewModel,
                    onPairingSuccess = {
                        navController.navigate("dashboard") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
