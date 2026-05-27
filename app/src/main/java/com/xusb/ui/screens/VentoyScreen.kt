package com.xusb.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xusb.ui.MainViewModel
import com.xusb.ui.components.*
import com.xusb.usb.*
import kotlinx.coroutines.launch

@Composable
fun VentoyScreen(vm: MainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isEs  by vm.lang.collectAsState()
    val sp = isEs == 1
    var progress by remember { mutableStateOf(OperationProgress()) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(0.06f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Ventoy X", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    if (sp) "Instala Ventoy en tu USB para arrancar múltiples ISOs sin reformatear. Copia ISOs directamente al USB después de la instalación."
                    else "Install Ventoy on your USB to boot multiple ISOs without reformatting. Just copy ISO files directly to the USB after install.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (sp) "Basado en Ventoy (GPL-3.0) — Tema y UI: X-USB"
                    else "Based on Ventoy (GPL-3.0) — Theme & UI: X-USB",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (progress.isRunning || progress.isComplete || progress.error != null)
            OperationProgressCard(progress = progress, onCancel = {})

        Button(
            onClick = {
                if (!drive.isConnected) return@Button
                scope.launch { installVentoy(context, drive.path).collect { progress = it } }
            },
            enabled = drive.isConnected && !progress.isRunning,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Usb, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (sp) "INSTALAR VENTOY X" else "INSTALL VENTOY X",
                style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp)
        }
    }
}
