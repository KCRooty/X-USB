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
fun FormatUsbScreen(vm: MainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isEs  by vm.lang.collectAsState()
    val isSp = isEs == 1
    var selectedFs by remember { mutableStateOf(Filesystem.FAT32) }
    var label by remember { mutableStateOf("X-USB") }
    var progress by remember { mutableStateOf(OperationProgress()) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(title = if (isSp) "SISTEMA DE ARCHIVOS" else "FILESYSTEM") {
            val filesystems = Filesystem.entries
            filesystems.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { fs ->
                        val selected = selectedFs == fs
                        Surface(
                            onClick = { selectedFs = fs },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(0.2f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(0.7f)
                                                        else MaterialTheme.colorScheme.outline.copy(0.3f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                                Text(fs.label, style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface.copy(0.5f), fontSize = 8.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        SectionCard(title = if (isSp) "ETIQUETA DE VOLUMEN" else "VOLUME LABEL") {
            OutlinedTextField(
                value = label, onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.4f)
                )
            )
        }

        if (progress.isRunning || progress.isComplete || progress.error != null)
            OperationProgressCard(progress = progress, onCancel = {})

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.error.copy(0.08f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.3f))
        ) {
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Text(if (isSp) "Se borrarán TODOS los datos del USB."
                     else "ALL data on the USB will be erased.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Button(
            onClick = {
                if (!drive.isConnected) return@Button
                scope.launch { formatUsb(drive.path, selectedFs, label).collect { progress = it } }
            },
            enabled = drive.isConnected && !progress.isRunning,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Storage, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isSp) "FORMATEAR" else "FORMAT", style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp)
        }
    }
}
