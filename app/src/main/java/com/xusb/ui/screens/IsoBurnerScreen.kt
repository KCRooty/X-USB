package com.xusb.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xusb.ui.MainViewModel
import com.xusb.ui.components.*
import com.xusb.usb.*
import kotlinx.coroutines.*

enum class WriteMode(val labelEn: String, val labelEs: String) {
    ISO_DD("ISO/DD", "ISO/DD"),
    VENTOY("Ventoy", "Ventoy"),
    RAW("Raw Write", "Raw Write")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsoBurnerScreen(vm: MainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isEs  by vm.lang.collectAsState()
    val sp = isEs == 1

    var selectedIsoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedIsoName by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(WriteMode.ISO_DD) }
    var progress by remember { mutableStateOf(OperationProgress()) }
    var showWarning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedIsoUri = it
            selectedIsoName = it.lastPathSegment?.substringAfterLast('/') ?: "image.iso"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ISO file picker
        SectionCard(title = if (sp) "ARCHIVO ISO / IMG" else "ISO / IMG FILE") {
            if (selectedIsoName.isEmpty()) {
                OutlinedButton(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth().height(70.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FileOpen, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(if (sp) "Seleccionar archivo…" else "Browse file…",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.FilePresent, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selectedIsoName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        Text(if (sp) "Seleccionado ✓" else "Selected ✓",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(onClick = { selectedIsoUri = null; selectedIsoName = "" }) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Target USB
        SectionCard(title = if (sp) "DESTINO" else "TARGET DEVICE") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    if (drive.isConnected) Icons.Default.Usb else Icons.Default.UsbOff,
                    null,
                    tint = if (drive.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        if (drive.isConnected) "${drive.name} · ${drive.path}" else if (sp) "Sin dispositivo USB" else "No USB device",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (drive.isConnected)
                        Text("${drive.sizeDisplay} · ${drive.usbVersion}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Write mode
        SectionCard(title = if (sp) "MODO DE ESCRITURA" else "WRITE MODE") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WriteMode.entries.forEach { mode ->
                    val selected = selectedMode == mode
                    Surface(
                        onClick = { selectedMode = mode },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (sp) mode.labelEs else mode.labelEn,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
        }

        // Warning
        if (drive.isConnected && selectedIsoName.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
            ) {
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Text(
                        if (sp) "Se eliminarán TODOS los datos del USB." else "ALL data on the USB will be erased.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Progress
        if (progress.isRunning || progress.isComplete || progress.error != null) {
            OperationProgressCard(progress = progress, onCancel = { /* TODO: cancel coroutine */ })
        }

        // Burn button
        Button(
            onClick = {
                if (!drive.isConnected || selectedIsoUri == null) return@Button
                scope.launch {
                    val isoPath = selectedIsoUri!!.path ?: return@launch
                    rawWrite(isoPath, drive.path).collect { p -> progress = p }
                }
            },
            enabled = drive.isConnected && selectedIsoName.isNotEmpty() && !progress.isRunning,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.outline
            )
        ) {
            Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (sp) "GRABAR ISO" else "BURN ISO",
                style = MaterialTheme.typography.titleMedium,
                letterSpacing = 2.sp
            )
        }
    }
}

