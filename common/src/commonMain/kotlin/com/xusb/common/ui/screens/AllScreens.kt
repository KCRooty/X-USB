package com.xusb.common.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import com.xusb.common.IMainViewModel
import com.xusb.common.ui.components.*
import com.xusb.common.usb.*
import kotlinx.coroutines.launch
import com.xusb.common.*

@Composable
fun FormatUsbScreen(vm: IMainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isSp = vm.lang.collectAsState().value == 1
    var selectedFs by remember { mutableStateOf(Filesystem.FAT32) }
    var progress by remember { mutableStateOf(OperationProgress()) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(title = if (isSp) "DESTINO" else "TARGET") {
            UsbDeviceCard(drive = drive, isSpanish = isSp, onEject = { vm.ejectUsb() })
        }
        
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
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(0.7f) else MaterialTheme.colorScheme.outline.copy(0.3f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                                Text(fs.label, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.5f), fontSize = 8.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        if (progress.isRunning || progress.isComplete || progress.error != null)
            OperationProgressCard(progress = progress, onCancel = {})

        Button(
            onClick = { scope.launch { formatUsb(drive.path, selectedFs, "X-USB").collect { progress = it } } },
            enabled = drive.isConnected && !progress.isRunning,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Storage, null)
            Spacer(Modifier.width(8.dp))
            Text(if (isSp) "FORMATEAR" else "FORMAT")
        }
    }
}

@Composable
fun WipeUsbScreen(vm: IMainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isSp = vm.lang.collectAsState().value == 1
    var progress by remember { mutableStateOf(OperationProgress()) }
    var confirmed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.error.copy(0.1f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.4f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                    Text(if (isSp) "Borrado Seguro" else "Secure Wipe", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                }
                Text(if (isSp) "Esto borrará TODOS los datos." else "This will erase ALL data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = confirmed, onCheckedChange = { confirmed = it }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error))
            Text(if (isSp) "Entiendo que se perderán todos los datos." else "I understand all data will be permanently lost.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }

        if (progress.isRunning || progress.isComplete || progress.error != null)
            OperationProgressCard(progress = progress, onCancel = {})

        Button(
            onClick = { scope.launch { wipeUsb(drive.path).collect { progress = it } } },
            enabled = drive.isConnected && confirmed && !progress.isRunning,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.DeleteForever, null)
            Spacer(Modifier.width(8.dp))
            Text(if (isSp) "BORRAR USB" else "WIPE USB")
        }
    }
}

@Composable
fun IsoBurnerScreen(vm: IMainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isSp = vm.lang.collectAsState().value == 1
    var progress by remember { mutableStateOf(OperationProgress()) }
    var selectedIsoPath by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(title = if (isSp) "ARCHIVO ISO" else "ISO FILE") {
            Button(onClick = {}) { Text(if (isSp) "SELECCIONAR" else "SELECT") }
        }

        SectionCard(title = if (isSp) "DESTINO" else "TARGET") {
            UsbDeviceCard(drive = drive, isSpanish = isSp, onEject = { vm.ejectUsb() })
        }

        if (progress.isRunning || progress.isComplete || progress.error != null)
            OperationProgressCard(progress = progress, onCancel = {})

        Button(
            onClick = { scope.launch { rawWrite(selectedIsoPath, drive.path).collect { progress = it } } },
            enabled = drive.isConnected && selectedIsoPath.isNotEmpty() && !progress.isRunning,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.FlashOn, null)
            Spacer(Modifier.width(8.dp))
            Text(if (isSp) "GRABAR" else "BURN")
        }
    }
}

@Composable
fun IsoDownloaderScreen(vm: IMainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isSp = vm.lang.collectAsState().value == 1
    var progress by remember { mutableStateOf(OperationProgress()) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (progress.isRunning || progress.isComplete || progress.error != null)
            OperationProgressCard(progress = progress, onCancel = {})

        ISO_CATALOG.forEach { iso ->
            IsoEntryRow(iso = iso, drive = drive, isSpanish = isSp) {
                scope.launch { downloadIsoToUsb(iso.downloadUrl, "XUSB/${iso.id}.iso").collect { progress = it } }
            }
        }
    }
}

@Composable
fun VentoyScreen(vm: IMainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isSp = vm.lang.collectAsState().value == 1
    var progress by remember { mutableStateOf(OperationProgress()) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(title = "VENTOY X") {
            Text(if (isSp) "Instala Ventoy en tu USB para arrancar múltiples ISOs sin reformatear." else "Install Ventoy on your USB to boot multiple ISOs without reformatting.", style = MaterialTheme.typography.bodySmall)
        }
        
        if (progress.isRunning || progress.isComplete || progress.error != null)
            OperationProgressCard(progress = progress, onCancel = {})

        Button(
            onClick = { scope.launch { installVentoy(null, drive.path).collect { progress = it } } },
            enabled = drive.isConnected && !progress.isRunning,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Usb, null)
            Spacer(Modifier.width(8.dp))
            Text(if (isSp) "INSTALAR VENTOY X" else "INSTALL VENTOY X")
        }
    }
}

@Composable
fun BackupRestoreScreen(vm: IMainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isSp = vm.lang.collectAsState().value == 1
    var progress by remember { mutableStateOf(OperationProgress()) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = if (isSp) "COPIA DE SEGURIDAD" else "BACKUP") {
            Button(
                onClick = { scope.launch { backupUsb(drive.path, "backup.img").collect { progress = it } } },
                enabled = drive.isConnected && !progress.isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isSp) "INICIAR RESPALDO" else "START BACKUP")
            }
        }
        
        if (progress.isRunning || progress.isComplete || progress.error != null)
            OperationProgressCard(progress = progress, onCancel = {})
    }
}

@Composable
fun MultiOsUefiScreen(vm: IMainViewModel) {
    val isSp = vm.lang.collectAsState().value == 1
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.DeveloperBoard, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("MultiOS USB (UEFI)", style = MaterialTheme.typography.titleLarge)
        Text(if (isSp) "Crea un USB multi-arranque para sistemas UEFI" else "Create a multi-boot USB for UEFI systems")
    }
}

@Composable
fun PartitionWizardScreen(vm: IMainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isSp = vm.lang.collectAsState().value == 1
    var partitions by remember { mutableStateOf(listOf<PartitionInfo>()) }
    
    LaunchedEffect(drive) {
        if (drive.isConnected) partitions = listPartitions(drive.path)
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (isSp) "PARTICIONES" else "PARTITIONS", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { if (drive.isConnected) partitions = listPartitions(drive.path) }) {
                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        partitions.forEach { part ->
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${if (isSp) "Partición" else "Partition"} ${part.number}", fontWeight = FontWeight.Bold)
                        Text("${part.size} · ${part.filesystem}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun PartitionMounterScreen(vm: IMainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isSp = vm.lang.collectAsState().value == 1
    var partitions by remember { mutableStateOf(listOf<PartitionInfo>()) }

    LaunchedEffect(drive) {
        if (drive.isConnected) partitions = listPartitions(drive.path)
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (isSp) "MONTAR PARTICIONES" else "MOUNT PARTITIONS", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

        partitions.forEach { part ->
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${if (isSp) "Partición" else "Partition"} #${part.number} (${part.size})")
                        Text(part.filesystem, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { mountPartition("${drive.path}${part.number}", "/mnt/xusb") }) {
                        Text(if (isSp) "MONTAR" else "MOUNT")
                    }
                }
            }
        }
    }
}

@Composable
fun Ps2UtilsScreen(vm: IMainViewModel) {
    val isSp = vm.lang.collectAsState().value == 1
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.SportsEsports, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary.copy(0.4f))
        Text("PS2 USB Utils", style = MaterialTheme.typography.titleLarge)
        Text(if (isSp) "Gestiona tus juegos de PS2" else "Manage your PS2 games")
        Spacer(Modifier.height(16.dp))
        Button(onClick = {}) { Text(if (isSp) "BUSCAR JUEGOS" else "SCAN FOR GAMES") }
    }
}

@Composable
fun FileManagerScreen(vm: IMainViewModel) {
    val drive by vm.connectedDrive.collectAsState()
    val isSp = vm.lang.collectAsState().value == 1
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(0.4f))
        Text(if (isSp) "Explorador de Archivos" else "File Explorer", style = MaterialTheme.typography.titleLarge)
        if (drive.isConnected) Text("${if (isSp) "Ruta:" else "Path:"} ${drive.path}")
    }
}

@Composable
fun RawWriteScreen(vm: IMainViewModel) {
    IsoBurnerScreen(vm)
}

@Composable
fun IsoEntryRow(iso: IsoEntry, drive: UsbDriveInfo, isSpanish: Boolean, onDownload: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("${iso.nameDisplay} ${iso.version}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(iso.sizeDisplay, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.CloudDownload, null, tint = if (drive.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.3f))
            }
        }
    }
}
