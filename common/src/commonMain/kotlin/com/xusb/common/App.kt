package com.xusb.common

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xusb.common.ui.theme.XUsbTheme
import com.xusb.common.ui.theme.AppTheme
import com.xusb.common.ui.screens.*
import com.xusb.common.usb.FeatureId

@Composable
fun App(vm: IMainViewModel) {
    val currentTheme by vm.theme.collectAsState()
    val isSp by vm.lang.collectAsState()
    val sp = isSp == 1
    
    var showWelcome by remember { mutableStateOf(true) }
    var currentRoute by remember { mutableStateOf("home") }
    
    XUsbTheme(appTheme = currentTheme) {
        if (showWelcome) {
            WelcomeScreen(sp) { showWelcome = false }
        } else {
            Scaffold(
                topBar = {
                    Column {
                        com.xusb.common.ui.components.XUsbTopBar(
                            isSpanish = sp,
                            theme = currentTheme,
                            onToggleLang = { vm.toggleLang() },
                            onThemeClick = { /* Theme Picker */ }
                        )
                        if (currentRoute != "home") {
                            Surface(tonalElevation = 0.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { currentRoute = "home" }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Text(
                                        currentRoute.uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Made by Alfy181X",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (currentRoute) {
                        "home" -> HomeScreen(vm) { currentRoute = it }
                        FeatureId.FORMAT_USB.name.lowercase() -> FormatUsbScreen(vm)
                        FeatureId.WIPE_USB.name.lowercase() -> WipeUsbScreen(vm)
                        FeatureId.ISO_BURNER.name.lowercase() -> IsoBurnerScreen(vm)
                        FeatureId.ISO_DOWNLOADER.name.lowercase() -> IsoDownloaderScreen(vm)
                        FeatureId.VENTOY.name.lowercase() -> VentoyScreen(vm)
                        FeatureId.BACKUP_RESTORE.name.lowercase() -> BackupRestoreScreen(vm)
                        FeatureId.MULTI_OS_UEFI.name.lowercase() -> MultiOsUefiScreen(vm)
                        FeatureId.PARTITION_WIZARD.name.lowercase() -> PartitionWizardScreen(vm)
                        FeatureId.PARTITION_MOUNTER.name.lowercase() -> PartitionMounterScreen(vm)
                        FeatureId.FILE_MANAGER.name.lowercase() -> FileManagerScreen(vm)
                        FeatureId.PS2_USB.name.lowercase() -> Ps2UtilsScreen(vm)
                        FeatureId.RAW_WRITE.name.lowercase() -> RawWriteScreen(vm)
                        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("🚧 ${currentRoute.uppercase()} - Coming soon")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(isSp: Boolean, onStart: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Usb, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text("X-USB", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            Text(if (isSp) "Bienvenido a la suite USB definitiva" else "Welcome to the ultimate USB suite", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(48.dp))
            Button(onClick = onStart, shape = RoundedCornerShape(12.dp)) {
                Text(if (isSp) "EMPEZAR" else "GET STARTED")
            }
            Spacer(Modifier.height(16.dp))
            Text("Made by Alfy181X", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}
