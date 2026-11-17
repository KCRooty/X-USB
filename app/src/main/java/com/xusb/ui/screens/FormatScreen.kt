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
fun FormatUsbScreen(vm: MainViewModel) {
    val drive    by vm.connectedDrive.collectAsState()
    val isEs     by vm.lang.collectAsState()
    val isSp = isEs == 1

    var selectedFs   by remember { mutableStateOf(Filesystem.FAT32) }
    var clusterSize  by remember { mutableStateOf(ClusterSize.DEFAULT) }
    var label        by remember { mutableStateOf("X-USB") }
    var progress     by remember { mutableStateOf(OperationProgress()) }
    var showConfirm  by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val maxLabelLen       = selectedFs.maxLabelLength()
    val availableClusters = selectedFs.availableClusterSizes()

    // Reset cluster if not valid for the newly selected filesystem
    LaunchedEffect(selectedFs) {
        if (clusterSize !in availableClusters) clusterSize = ClusterSize.DEFAULT
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── FILESYSTEM ────────────────────────────────────────────────────
        SectionCard(title = if (isSp) "SISTEMA DE ARCHIVOS" else "FILESYSTEM") {
            Filesystem.entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { fs ->
                        val sel = selectedFs == fs
                        Surface(
                            onClick = { selectedFs = fs },
                            shape = RoundedCornerShape(8.dp),
                            color = if (sel) MaterialTheme.colorScheme.primary.copy(0.2f)
                                    else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp,
                                if (sel) MaterialTheme.colorScheme.primary.copy(0.7f)
                                else     MaterialTheme.colorScheme.outline.copy(0.3f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                                Text(fs.label, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp,
                                    color = if (sel) MaterialTheme.colorScheme.primary
                                            else     MaterialTheme.colorScheme.onSurface.copy(0.5f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        // ── VOLUME LABEL ──────────────────────────────────────────────────
        SectionCard(title = if (isSp) "ETIQUETA DE VOLUMEN" else "VOLUME LABEL") {
            OutlinedTextField(
                value = label,
                onValueChange = { if (it.length <= maxLabelLen) label = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                placeholder = {
                    Text("X-USB", color = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                },
                trailingIcon = {
                    Text(
                        "${label.length}/$maxLabelLen",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (label.length >= maxLabelLen) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface.copy(0.4f),
                        modifier = Modifier.padding(end = 10.dp)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.4f)
                )
            )
        }

        // ── CLUSTER SIZE ──────────────────────────────────────────────────
        SectionCard(title = if (isSp) "TAMAÑO DE CLÚSTER" else "CLUSTER SIZE") {
            if (availableClusters.size == 1) {
                Text(
                    if (isSp) "Fijo para ${selectedFs.label} (gestionado por mkfs)"
                    else      "Fixed for ${selectedFs.label} (managed by mkfs)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                )
            } else {
                availableClusters.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { cs ->
                            val sel = clusterSize == cs
                            Surface(
                                onClick = { clusterSize = cs },
                                shape = RoundedCornerShape(8.dp),
                                color = if (sel) MaterialTheme.colorScheme.secondary.copy(0.2f)
                                        else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp,
                                    if (sel) MaterialTheme.colorScheme.secondary.copy(0.7f)
                                    else     MaterialTheme.colorScheme.outline.copy(0.3f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(Modifier.padding(vertical = 8.dp, horizontal = 2.dp), contentAlignment = Alignment.Center) {
                                    Text(cs.display, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp,
                                        color = if (sel) MaterialTheme.colorScheme.secondary
                                                else     MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                }
                            }
                        }
                        // Pad row to keep grid alignment
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        // ── PROGRESS ──────────────────────────────────────────────────────
        if (progress.isRunning || progress.isComplete || progress.error != null) {
            OperationProgressCard(progress = progress, onCancel = {})
        }

        // ── WARNING ───────────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.error.copy(0.08f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.3f))
        ) {
            Row(Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Text(
                    if (isSp) "Se borrarán TODOS los datos del USB."
                    else      "ALL data on the USB will be erased.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // ── FORMAT BUTTON ─────────────────────────────────────────────────
        Button(
            onClick  = { if (drive.isConnected) showConfirm = true },
            enabled  = drive.isConnected && !progress.isRunning,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Storage, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (isSp) "FORMATEAR" else "FORMAT",
                style = MaterialTheme.typography.titleMedium,
                letterSpacing = 2.sp
            )
        }
    }

    // ── CONFIRMATION DIALOG ───────────────────────────────────────────────
    if (showConfirm) {
        FormatConfirmDialog(
            driveName    = drive.name,
            drivePath    = drive.path,
            driveSize    = drive.sizeDisplay,
            fs           = selectedFs,
            label        = label,
            clusterSize  = clusterSize,
            isSpanish    = isSp,
            onConfirm    = {
                showConfirm = false
                progress = OperationProgress()
                scope.launch {
                    formatUsb(drive.path, selectedFs, label, clusterSize.bytes).collect { progress = it }
                }
            },
            onDismiss = { showConfirm = false }
        )
    }
}

@Composable
private fun FormatConfirmDialog(
    driveName:   String,
    drivePath:   String,
    driveSize:   String,
    fs:          Filesystem,
    label:       String,
    clusterSize: ClusterSize,
    isSpanish:   Boolean,
    onConfirm:   () -> Unit,
    onDismiss:   () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Warning, null,
                tint     = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp))
        },
        title = {
            Text(
                if (isSpanish) "¿Confirmar formato?" else "Confirm Format?",
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ConfirmRow(if (isSpanish) "Dispositivo"         else "Device",      "$driveName ($drivePath)")
                ConfirmRow(if (isSpanish) "Sistema de archivos" else "Filesystem",  fs.label)
                ConfirmRow(if (isSpanish) "Etiqueta"            else "Label",       label.ifEmpty { "—" })
                ConfirmRow(if (isSpanish) "Tamaño de clúster"   else "Cluster size", clusterSize.display)
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape  = RoundedCornerShape(8.dp),
                    color  = MaterialTheme.colorScheme.error.copy(0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.3f))
                ) {
                    Text(
                        if (isSpanish)
                            "TODOS los datos de $driveSize serán borrados de forma permanente e irrecuperable."
                        else
                            "ALL data on $driveSize will be permanently and irrecoverably erased.",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(if (isSpanish) "FORMATEAR" else "FORMAT")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(if (isSpanish) "CANCELAR" else "CANCEL")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape          = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodySmall,
            color      = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold)
    }
}
