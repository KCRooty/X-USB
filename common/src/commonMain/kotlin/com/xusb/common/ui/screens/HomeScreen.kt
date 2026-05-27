package com.xusb.common.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.xusb.common.IMainViewModel
import com.xusb.common.ui.components.*
import com.xusb.common.ui.theme.*
import com.xusb.common.usb.*

@Composable
fun HomeScreen(vm: IMainViewModel, onNavigate: (String) -> Unit) {
    val drive    by vm.connectedDrive.collectAsState()
    val isEs     by vm.lang.collectAsState()
    val isSp = isEs == 1

    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Device card
        item {
            UsbDeviceCard(drive = drive, isSpanish = isSp, onEject = { vm.ejectUsb() })
        }

        // Section header
        item {
            Text(
                text = if (isSp) "HERRAMIENTAS (VERSIÓN PC)" else "TOOLS (PC VERSION)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
            )
        }

        // Feature list
        items(ALL_FEATURES) { feature ->
            FeatureMenuItem(
                title    = if (isSp) feature.titleEs    else feature.titleEn,
                subtitle = if (isSp) feature.subtitleEs else feature.subtitleEn,
                icon     = featureIcon(feature.id),
                badge    = if (isSp) feature.badgeEs else feature.badgeEn,
                isNew    = feature.isNew,
                requiresRoot = feature.requiresRoot,
                onClick  = { onNavigate(feature.id.name.lowercase()) }
            )
        }

        // Theme Quick Picker
        item {
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ThemeDot(color = Color(0xFF7B61FF), selected = vm.theme.collectAsState().value == AppTheme.DARK_PURPLE) { vm.setTheme(AppTheme.DARK_PURPLE) }
                    ThemeDot(color = Color(0xFFFFFFFF), selected = vm.theme.collectAsState().value == AppTheme.LIGHT) { vm.setTheme(AppTheme.LIGHT) }
                    ThemeDot(color = Color(0xFFFACC15), selected = vm.theme.collectAsState().value == AppTheme.BLUE_YELLOW) { vm.setTheme(AppTheme.BLUE_YELLOW) }
                    Text(
                        if (isSp) "TEMAS" else "THEMES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                Text(
                    if (isSp) "EN / ES" else "EN / ES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { vm.toggleLang() }
                )
            }
        }
    }
}

@Composable
fun ThemeDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) color else color.copy(alpha = 0.3f))
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                CircleShape
            )
            .clickable { onClick() }
    )
}

fun featureIcon(id: FeatureId): ImageVector = when (id) {
    FeatureId.VENTOY           -> Icons.Default.Usb
    FeatureId.ISO_BURNER       -> Icons.Default.Album
    FeatureId.MULTI_OS_UEFI    -> Icons.Default.DeveloperBoard
    FeatureId.FORMAT_USB       -> Icons.Default.Storage
    FeatureId.PARTITION_WIZARD -> Icons.Default.TableChart
    FeatureId.PS2_USB          -> Icons.Default.SportsEsports
    FeatureId.FILE_MANAGER     -> Icons.Default.FolderOpen
    FeatureId.PARTITION_MOUNTER-> Icons.Default.Storage
    FeatureId.WIPE_USB         -> Icons.Default.DeleteForever
    FeatureId.RAW_WRITE        -> Icons.Default.Memory
    FeatureId.BACKUP_RESTORE   -> Icons.Default.Restore
    FeatureId.ISO_DOWNLOADER   -> Icons.Default.CloudDownload
}
