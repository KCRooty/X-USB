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
fun WipeUsbScreen(vm: MainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isEs  by vm.lang.collectAsState()
    val isSp = isEs == 1
    var confirmed by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(OperationProgress()) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.error.copy(0.1f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.4f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                    Text(if (isSp) "Borrado Seguro" else "Secure Wipe",
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    if (isSp) "Esto borrará TODOS los datos, particiones y el sistema de archivos del USB. Esta acción NO se puede deshacer."
                    else "This will erase ALL data, partitions, and filesystem from the USB. This action CANNOT be undone.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = confirmed, onCheckedChange = { confirmed = it },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error))
            Text(
                if (isSp) "Entiendo que se perderán todos los datos."
                else "I understand all data will be permanently lost.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (progress.isRunning || progress.isComplete || progress.error != null)
            OperationProgressCard(progress = progress, onCancel = {})

        Button(
            onClick = {
                if (!drive.isConnected || !confirmed) return@Button
                scope.launch { wipeUsb(drive.path).collect { progress = it } }
            },
            enabled = drive.isConnected && confirmed && !progress.isRunning,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isSp) "BORRAR USB" else "WIPE USB", style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp)
        }
    }
}
