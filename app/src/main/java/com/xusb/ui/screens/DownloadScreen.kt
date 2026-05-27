package com.xusb.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xusb.ui.MainViewModel
import com.xusb.ui.components.*
import com.xusb.usb.*
import kotlinx.coroutines.launch

@Composable
fun IsoDownloaderScreen(vm: MainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isEs  by vm.lang.collectAsState()
    val isSp = isEs == 1
    var progress by remember { mutableStateOf(OperationProgress()) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (progress.isRunning || progress.isComplete || progress.error != null) {
            OperationProgressCard(progress = progress, onCancel = {})
            Spacer(Modifier.height(8.dp))
        }

        Text(if (isSp) "LINUX" else "LINUX",
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp))

        ISO_CATALOG.filter { it.category == IsoCategory.LINUX }.forEach { iso ->
            IsoEntryRow(iso = iso, drive = drive, isSpanish = isSp, onDownload = {
                scope.launch {
                    val dest = "/mnt/media_rw/XUSB/${iso.id}.iso"
                    downloadIsoToUsb(iso.downloadUrl, dest).collect { progress = it }
                }
            })
        }

        Text(if (isSp) "WINDOWS" else "WINDOWS",
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp))

        ISO_CATALOG.filter { it.category == IsoCategory.WINDOWS }.forEach { iso ->
            IsoEntryRow(iso = iso, drive = drive, isSpanish = isSp, onDownload = {
                scope.launch {
                    val dest = "/mnt/media_rw/XUSB/${iso.id}.iso"
                    downloadIsoToUsb(iso.downloadUrl, dest).collect { progress = it }
                }
            })
        }

        Text(if (isSp) "UTILIDADES" else "UTILITIES",
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp))

        ISO_CATALOG.filter { it.category == IsoCategory.UTILITIES }.forEach { iso ->
            IsoEntryRow(iso = iso, drive = drive, isSpanish = isSp, isBundledTag = true, onDownload = {
                scope.launch {
                    val dest = "/mnt/media_rw/XUSB/${iso.id}.iso"
                    downloadIsoToUsb(iso.downloadUrl, dest).collect { progress = it }
                }
            })
        }

        Text(if (isSp) "EMBEBIDOS" else "EMBEDDED",
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp))

        ISO_CATALOG.filter { it.category == IsoCategory.EMBEDDED }.forEach { iso ->
            IsoEntryRow(iso = iso, drive = drive, isSpanish = isSp, onDownload = {
                scope.launch {
                    val dest = "/mnt/media_rw/XUSB/${iso.id}.iso"
                    downloadIsoToUsb(iso.downloadUrl, dest).collect { progress = it }
                }
            })
        }
    }
}

@Composable
fun IsoEntryRow(iso: IsoEntry, drive: UsbDriveInfo, isSpanish: Boolean, isBundledTag: Boolean = false, onDownload: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f),
                RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${iso.nameDisplay} ${iso.version}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    if (iso.isBundled) {
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondary.copy(0.15f)) {
                            Text(if (isSpanish) "INCLUIDO" else "BUNDLED",
                                Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary, fontSize = 7.sp)
                        }
                    }
                }
                Text(iso.sizeDisplay, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.CloudDownload, null,
                    tint = if (drive.isConnected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface.copy(0.3f))
            }
        }
    }
}
