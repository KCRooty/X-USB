package com.xusb.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.xusb.ui.components.*
import com.xusb.ui.screens.*
import com.xusb.ui.theme.*
import com.xusb.usb.FeatureId

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { XUsbApp(vm) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XUsbApp(vm: MainViewModel) {
    val theme by vm.theme.collectAsState()
    val lang  by vm.lang.collectAsState()
    val isSp = lang == 1

    XUsbTheme(appTheme = theme) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: "home"
        val isHome = currentRoute == "home"

        var showThemePicker by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                Column {
                    XUsbTopBar(
                        isSpanish  = isSp,
                        theme      = theme,
                        onToggleLang  = { vm.toggleLang() },
                        onThemeClick  = { showThemePicker = true }
                    )
                    // Back button on sub-screens
                    if (!isHome) {
                        Surface(tonalElevation = 0.dp) {
                            Row {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary)
                                }
                                val title = FeatureId.entries
                                    .firstOrNull { it.name.lowercase() == currentRoute }
                                    ?.let { id -> com.xusb.usb.ALL_FEATURES.find { it.id == id } }
                                    ?.let { if (isSp) it.titleEs else it.titleEn } ?: ""
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(padding)
            ) {
                composable("home")                        { HomeScreen(vm, navController) }
                composable(FeatureId.VENTOY.name.lowercase())           { VentoyScreen(vm) }
                composable(FeatureId.ISO_BURNER.name.lowercase())       { IsoBurnerScreen(vm) }
                composable(FeatureId.MULTI_OS_UEFI.name.lowercase())    { PlaceholderScreen("MultiOS UEFI", isSp) }
                composable(FeatureId.FORMAT_USB.name.lowercase())       { FormatUsbScreen(vm) }
                composable(FeatureId.PARTITION_WIZARD.name.lowercase()) { PartitionWizardScreen(vm) }
                composable(FeatureId.PS2_USB.name.lowercase())          { Ps2UtilsScreen(vm) }
                composable(FeatureId.FILE_MANAGER.name.lowercase())     { FileManagerScreen(vm) }
                composable(FeatureId.PARTITION_MOUNTER.name.lowercase()){ PartitionMounterScreen(vm) }
                composable(FeatureId.WIPE_USB.name.lowercase())         { WipeUsbScreen(vm) }
                composable(FeatureId.RAW_WRITE.name.lowercase())        { RawWriteScreen(vm) }
                composable(FeatureId.BACKUP_RESTORE.name.lowercase())   { BackupRestoreScreen(vm) }
                composable(FeatureId.ISO_DOWNLOADER.name.lowercase())   { IsoDownloaderScreen(vm) }
            }

            // Theme picker bottom sheet
            if (showThemePicker) {
                ModalBottomSheet(onDismissRequest = { showThemePicker = false }) {
                    ThemePickerSheet(
                        current   = theme,
                        isSpanish = isSp,
                        onSelect  = { vm.setTheme(it) },
                        onDismiss = { showThemePicker = false }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(name: String, isSpanish: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text("🚧", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(12.dp))
            Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                if (isSpanish) "En desarrollo" else "Coming soon",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
