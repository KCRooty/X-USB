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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xusb.ui.MainViewModel
import com.xusb.ui.components.*
import com.xusb.usb.*
import kotlinx.coroutines.launch

@Composable
fun PartitionWizardScreen(vm: MainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isEs by vm.lang.collectAsState()
    val sp = isEs == 1
    var partitions by remember { mutableStateOf(listOf<PartitionInfo>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(drive) {
        if (drive.isConnected) {
            partitions = listPartitions(drive.path)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (sp) "PARTICIONES" else "PARTITIONS", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { if (drive.isConnected) partitions = listPartitions(drive.path) }) {
                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (partitions.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(if (sp) "No hay particiones." else "No partitions found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        partitions.forEach { part ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${if (sp) "Partición" else "Partition"} ${part.number}", fontWeight = FontWeight.Bold)
                        Text("${part.size} · ${part.filesystem}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { /* TODO: delete partition */ }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { /* TODO: create partition */ },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(if (sp) "NUEVA PARTICIÓN" else "NEW PARTITION")
        }
    }
}

@Composable
fun PartitionMounterScreen(vm: MainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isEs by vm.lang.collectAsState()
    val sp = isEs == 1
    var partitions by remember { mutableStateOf(listOf<PartitionInfo>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(drive) {
        if (drive.isConnected) {
            partitions = listPartitions(drive.path)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (sp) "PARTICIONES DETECTADAS" else "DETECTED PARTITIONS",
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

        if (partitions.isEmpty()) {
            Text(if (sp) "No se detectaron particiones." else "No partitions detected.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        partitions.forEach { part ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${if (sp) "Partición" else "Partition"} #${part.number} (${part.size})",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("${part.filesystem} · ${part.flags}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            val partPath = "${drive.path}${part.number}"
                            val mountPoint = "/mnt/media_rw/XUSB_P${part.number}"
                            scope.launch { mountPartition(partPath, mountPoint) }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(if (sp) "MONTAR" else "MOUNT", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
