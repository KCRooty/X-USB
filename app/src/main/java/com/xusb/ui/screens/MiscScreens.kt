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
import com.xusb.ui.MainViewModel
import com.xusb.ui.components.*
import com.xusb.usb.*
import kotlinx.coroutines.launch

@Composable
fun BackupRestoreScreen(vm: MainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isEs by vm.lang.collectAsState()
    val sp = isEs == 1
    var progress by remember { mutableStateOf(OperationProgress()) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = if (sp) "COPIA DE SEGURIDAD" else "BACKUP") {
            Text(if (sp) "Crea una imagen completa del USB (.img)" else "Create a full image of the USB (.img)",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val path = "/sdcard/Download/usb_backup_${System.currentTimeMillis()}.img"
                    scope.launch { backupUsb(drive.path, path).collect { progress = it } }
                },
                enabled = drive.isConnected && !progress.isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (sp) "INICIAR RESPALDO" else "START BACKUP")
            }
        }

        SectionCard(title = if (sp) "RESTAURAR" else "RESTORE") {
            Text(if (sp) "Escribe una imagen (.img) de vuelta al USB" else "Write an image (.img) back to the USB",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { /* TODO: file picker for .img */ },
                enabled = drive.isConnected && !progress.isRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Restore, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (sp) "RESTAURAR IMAGEN" else "RESTORE IMAGE")
            }
        }

        if (progress.isRunning || progress.isComplete || progress.error != null)
            OperationProgressCard(progress = progress, onCancel = {})
    }
}

@Composable
fun FileManagerScreen(vm: MainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isEs by vm.lang.collectAsState()
    val sp = isEs == 1

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(0.4f))
        Spacer(Modifier.height(16.dp))
        Text(if (sp) "Explorador de Archivos" else "File Explorer", style = MaterialTheme.typography.titleLarge)
        Text(if (sp) "Accede a los archivos de tu USB" else "Access files on your USB",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        if (drive.isConnected) {
            Text("${if (sp) "Ruta:" else "Path:"} ${drive.path}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun Ps2UtilsScreen(vm: MainViewModel) {
    val isEs by vm.lang.collectAsState()
    val sp = isEs == 1

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.SportsEsports, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary.copy(0.4f))
        Spacer(Modifier.height(16.dp))
        Text("PS2 USB Utils", style = MaterialTheme.typography.titleLarge)
        Text(if (sp) "Gestiona tus juegos de PS2" else "Manage your PS2 games",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = {}, shape = RoundedCornerShape(10.dp)) {
            Text(if (sp) "BUSCAR JUEGOS" else "SCAN FOR GAMES")
        }
    }
}

@Composable
fun RawWriteScreen(vm: MainViewModel) {
    IsoBurnerScreen(vm)
}
