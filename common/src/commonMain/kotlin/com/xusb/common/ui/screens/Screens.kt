package com.xusb.common.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xusb.common.AppContext
import com.xusb.common.Tool
import com.xusb.common.ui.components.*
import com.xusb.common.ui.state.DriveState
import com.xusb.common.ui.tr
import com.xusb.common.usb.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Piezas comunes ────────────────────────────────────────────────────────

/** Confirmación pendiente de una acción destructiva. */
class Pending(val title: String, val lines: List<String>, val label: String, val action: () -> Unit)

@Composable
private fun ConfirmHost(pending: Pending?, drive: UsbDriveInfo, onDismiss: () -> Unit) {
    if (pending != null) {
        ConfirmDestructive(pending.title, pending.lines, drive, pending.label.ifBlank { tr("Confirmar", "Confirm") },onConfirm = { pending.action(); onDismiss() }, onDismiss = onDismiss)
    }
}

/** Selector de unidad: lista real del backend, con causa y siguiente paso cuando no hay ninguna. */
@Composable
fun DrivePicker(drives: DriveState) {
    val privileged by produceState<Boolean?>(null) { value = withContext(Dispatchers.IO) { runCatching { UsbPlatform.backend.isPrivileged() }.getOrDefault(false) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel(tr("Unidad USB", "USB drive"))
            SecondaryButton(
                if (drives.loading) tr("Buscando…", "Scanning…") else tr("Actualizar", "Refresh"),
                onClick = { drives.refresh() }, enabled = !drives.loading && !drives.busy
            )
        }
        if (privileged == false) {
            Note(
                tr("Sin permisos para tocar discos", "No permission to touch disks"),
                tr("Puedes mirar, pero no escribir. Linux: ejecuta con sudo. Windows: abre como administrador. Android: hace falta root.",
                    "You can look but not write. Linux: run with sudo. Windows: run as administrator. Android: root is required."),
                NoteKind.WARN
            )
        }
        drives.scanError?.let { Note(tr("No se pudieron listar las unidades", "Could not list drives"), it, NoteKind.ERROR) }
        if (drives.drives.isEmpty() && !drives.loading && drives.scanError == null) {
            Text(
                tr("No hay ninguna unidad USB conectada. Enchufa una y pulsa Actualizar. Los discos del sistema nunca aparecen aquí.",
                    "No USB drive connected. Plug one in and press Refresh. System disks never appear here."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val sel = drives.selected
        drives.drives.forEach { d -> DriveRow(d, selected = d.path == sel?.path, onSelect = { drives.selectedPath = d.path }) }
    }
}

/** Línea con el destino activo, para pantallas estrechas donde el selector no está a la vista. */
@Composable
fun DrivePill(drives: DriveState) {
    val d = drives.selected
    if (d == null) Note(tr("Sin unidad", "No drive"), tr("Vuelve al inicio y conecta o actualiza la unidad.", "Go back and connect or refresh the drive."), NoteKind.WARN)
    else Panel { SectionLabel(tr("Destino", "Target")); DriveSummary(d) }
}

@Composable
private fun RequireDrive(ctx: AppContext, content: @Composable (UsbDriveInfo) -> Unit) {
    val d = ctx.drives.selected
    if (d == null) {
        EmptyState(
            tr("Elige una unidad", "Pick a drive"),
            tr("Esta herramienta actúa sobre una unidad USB y no hay ninguna seleccionada.", "This tool works on a USB drive and none is selected."),
            action = { SecondaryButton(tr("Buscar unidades", "Scan for drives"), onClick = { ctx.drives.refresh() }, enabled = !ctx.drives.loading) }
        )
    } else content(d)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FsChoices(selected: Filesystem, options: List<Filesystem>, onSelect: (Filesystem) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { Choice(it.label, it == selected, { onSelect(it) }) }
    }
}

@Composable
private fun PathField(label: String, value: String, onChange: (String) -> Unit, browse: (suspend () -> String?)?) {
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextInput(value, onChange, label, modifier = Modifier.weight(1f), mono = true)
        if (browse != null) SecondaryButton(tr("Examinar", "Browse"), onClick = { scope.launch { browse()?.let(onChange) } })
    }
}

private fun browseOpen(title: String): (suspend () -> String?)? =
    UsbPlatform.openFile?.let { f -> { withContext(Dispatchers.IO) { f(title) } } }

private fun browseSave(title: String, name: String): (suspend () -> String?)? =
    UsbPlatform.saveFile?.let { f -> { withContext(Dispatchers.IO) { f(title, name) } } }

// ── Herramientas ─────────────────────────────────────────────────────────

@Composable
fun FormatScreen(ctx: AppContext) = RequireDrive(ctx) { drive ->
    val options = remember { UsbPlatform.backend.supportedFilesystems() }
    var fs by remember(options) { mutableStateOf(if (Filesystem.FAT32 in options) Filesystem.FAT32 else options.first()) }
    var label by remember { mutableStateOf("X-USB") }
    var pending by remember { mutableStateOf<Pending?>(null) }
    val busy = ctx.runner.running
    val confirmTitle = tr("Formatear ${drive.name}", "Format ${drive.name}")
    val formatWord = tr("Formatear", "Format")
    val shown = sanitizeLabel(label, fs)
    val line = tr("Se borrará todo lo que hay en la unidad y se creará un sistema ${fs.label} con la etiqueta «$shown».",
        "Everything on the drive will be erased and a ${fs.label} file system labelled “$shown” will be created.")
    Panel {
        SectionLabel(tr("Sistema de archivos", "File system"))
        FsChoices(fs, options) { fs = it }
        TextInput(label, { label = it }, tr("Etiqueta", "Label"))
        Text(tr("Se usará: $shown (solo letras, cifras, guion y espacio; máx. ${fs.maxLabelLength()})", "Will use: $shown (letters, digits, dash, space only; max ${fs.maxLabelLength()})"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton(tr("Formatear…", "Format…"), enabled = !busy, danger = true, onClick = {
            pending = Pending(confirmTitle, listOf(line), formatWord, { ctx.runner.run(formatUsb(drive.path, fs, label)) })
        })
    }
    ConfirmHost(pending, drive) { pending = null }
}

@Composable
fun WipeScreen(ctx: AppContext) = RequireDrive(ctx) { drive ->
    var pending by remember { mutableStateOf<Pending?>(null) }
    val title = tr("Borrar ${drive.name}", "Wipe ${drive.name}")
    val line = tr("Se escribirán ceros en toda la unidad (${drive.sizeDisplay}). Ni la tabla de particiones ni los archivos sobrevivirán y no hay forma de recuperarlos.",
        "Zeros will be written over the whole drive (${drive.sizeDisplay}). The partition table and every file will be gone for good.")
    Panel {
        Text(tr("Sobrescribe la unidad entera con ceros. Tarda lo que tarde en escribirse todo el disco.", "Overwrites the entire drive with zeros. It takes as long as writing the whole disk."), style = MaterialTheme.typography.bodyMedium)
        PrimaryButton(tr("Borrar todo…", "Wipe everything…"), enabled = !ctx.runner.running, danger = true, onClick = {
            pending = Pending(title, listOf(line), "", { ctx.runner.run(wipeUsb(drive.path)) })
        })
    }
    ConfirmHost(pending, drive) { pending = null }
}

@Composable
fun WriteScreen(ctx: AppContext) = RequireDrive(ctx) { drive ->
    var path by remember { mutableStateOf(ctx.lastImage) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    val title = tr("Grabar en ${drive.name}", "Write to ${drive.name}")
    val line = tr("Se sobrescribirá toda la unidad con el contenido de «$path». Lo que hay ahora se pierde.", "The whole drive will be overwritten with the contents of “$path”. What is on it now is lost.")
    Panel {
        PathField(tr("Archivo ISO o IMG", "ISO or IMG file"), path, { path = it }, browseOpen(tr("Elige una imagen", "Choose an image")))
        Text(
            tr("Funciona con imágenes híbridas (la mayoría de distros Linux y utilidades). Una ISO oficial de Windows no arranca grabada así.",
                "Works with hybrid images (most Linux distros and utilities). An official Windows ISO will not boot when written this way."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PrimaryButton(tr("Grabar…", "Write…"), enabled = path.isNotBlank() && !ctx.runner.running, danger = true, onClick = {
            pending = Pending(title, listOf(line), "", { ctx.runner.run(rawWrite(path.trim(), drive.path)) })
        })
    }
    ConfirmHost(pending, drive) { pending = null }
}

@Composable
fun DownloadScreen(ctx: AppContext) {
    val dir = UsbPlatform.workDir
    var pendingDest by remember { mutableStateOf("") }
    val done = ctx.runner.progress.isComplete
    LaunchedEffect(done) { if (done && pendingDest.isNotEmpty()) ctx.lastImage = pendingDest }
    if (dir.isBlank()) {
        Note(tr("Sin carpeta de descargas", "No download folder"), tr("La plataforma no ha definido dónde guardar archivos.", "The platform has not set where to save files."), NoteKind.ERROR)
        return
    }
    Panel {
        SectionLabel(tr("Se guardan en este equipo, no en la unidad", "Saved on this device, not on the drive"))
        DataText(dir)
    }
    IsoCategory.entries.forEach { cat ->
        val items = ISO_CATALOG.filter { it.category == cat }
        if (items.isEmpty()) return@forEach
        SectionLabel(tr(cat.es, cat.en))
        items.forEach { iso ->
            Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${iso.name} ${iso.version}", style = MaterialTheme.typography.titleMedium)
                        DataText(humanBytes(iso.sizeBytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SecondaryButton(tr("Descargar", "Download"), enabled = !ctx.runner.running, onClick = {
                        val dest = dir.trimEnd('/', '\\') + "/" + iso.url.substringAfterLast('/')
                        pendingDest = dest
                        ctx.runner.run(downloadIsoToUsb(iso.url, dest))
                    })
                }
            }
        }
    }
    if (ctx.lastImage.isNotEmpty() && done) Note(tr("Lista para grabar", "Ready to write"), ctx.lastImage, NoteKind.OK)
}

@Composable
fun BackupScreen(ctx: AppContext) = RequireDrive(ctx) { drive ->
    val dir = UsbPlatform.workDir
    val defaultName = "xusb-backup-${drive.shortId}.img"
    var out by remember(drive.path) { mutableStateOf(if (dir.isBlank()) defaultName else dir.trimEnd('/', '\\') + "/" + defaultName) }
    var src by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pending?>(null) }
    val busy = ctx.runner.running
    Panel {
        Text(tr("Guardar la unidad", "Save the drive"), style = MaterialTheme.typography.titleMedium)
        Text(tr("Copia sector a sector toda la unidad (${drive.sizeDisplay}) a un archivo. Necesitas ese espacio libre.", "Copies the whole drive (${drive.sizeDisplay}) sector by sector into a file. You need that much free space."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PathField(tr("Archivo de destino", "Destination file"), out, { out = it }, browseSave(tr("Guardar copia", "Save backup"), defaultName))
        PrimaryButton(tr("Crear copia", "Create backup"), enabled = out.isNotBlank() && !busy, onClick = { ctx.runner.run(backupUsb(drive.path, out.trim())) })
    }
    val title = tr("Restaurar en ${drive.name}", "Restore to ${drive.name}")
    val line = tr("Se sobrescribirá toda la unidad con «$src». Lo que hay ahora se pierde.", "The whole drive will be overwritten with “$src”. What is on it now is lost.")
    Panel {
        Text(tr("Restaurar una copia", "Restore a backup"), style = MaterialTheme.typography.titleMedium)
        PathField(tr("Archivo de copia", "Backup file"), src, { src = it }, browseOpen(tr("Elige una copia", "Choose a backup")))
        PrimaryButton(tr("Restaurar…", "Restore…"), enabled = src.isNotBlank() && !busy, danger = true, onClick = {
            pending = Pending(title, listOf(line), "", { ctx.runner.run(restoreUsb(src.trim(), drive.path)) })
        })
    }
    ConfirmHost(pending, drive) { pending = null }
}

private class PartInfo(val table: String, val parts: List<PartitionInfo>)

@Composable
private fun rememberPartitions(ctx: AppContext, drive: UsbDriveInfo): State<PartInfo?> {
    val running = ctx.runner.running
    return produceState<PartInfo?>(null, drive.path, running) {
        if (!running) value = withContext(Dispatchers.IO) {
            PartInfo(runCatching { getPartitionTableType(drive.path) }.getOrDefault("?"), runCatching { listPartitions(drive.path) }.getOrDefault(emptyList()))
        }
    }
}

@Composable
fun PartitionsScreen(ctx: AppContext) = RequireDrive(ctx) { drive ->
    val info by rememberPartitions(ctx, drive)
    val options = remember { UsbPlatform.backend.supportedFilesystems() }
    var fs by remember(options) { mutableStateOf(if (Filesystem.FAT32 in options) Filesystem.FAT32 else options.first()) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    val busy = ctx.runner.running
    val tooBigForMbr = drive.sizeBytes > MBR_LIMIT_BYTES

    Panel {
        val i = info
        if (i == null) Text(tr("Leyendo particiones…", "Reading partitions…"), style = MaterialTheme.typography.bodyMedium)
        else {
            Text(tr("Tabla: ${i.table}", "Table: ${i.table}"), style = MaterialTheme.typography.titleMedium)
            if (i.parts.isEmpty()) Text(tr("La unidad no tiene particiones.", "The drive has no partitions."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            i.parts.forEach { p ->
                val delTitle = tr("Borrar la partición ${p.number}", "Delete partition ${p.number}")
                val delLine = tr("Se elimina la partición ${p.number} (${p.size}, ${p.filesystem.ifBlank { "sin formato" }}) con todo lo que contiene.", "Partition ${p.number} (${p.size}, ${p.filesystem.ifBlank { "unformatted" }}) is removed with everything on it.")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DataText("${p.number}   ${p.size}   ${p.filesystem.ifBlank { "-" }}   ${p.flags}", modifier = Modifier.weight(1f))
                    SecondaryButton(tr("Borrar", "Delete"), enabled = !busy, onClick = {
                        pending = Pending(delTitle, listOf(delLine), "", { ctx.runner.run(deletePartition(drive.path, p.number)) })
                    })
                }
            }
        }
    }

    Panel {
        SectionLabel(tr("Nueva partición", "New partition"))
        FsChoices(fs, options) { fs = it }
        val t = tr("Crear partición en ${drive.name}", "Create partition on ${drive.name}")
        val l = tr("Se crea una partición ${fs.label} en el espacio libre de la unidad.", "A ${fs.label} partition is created in the drive's free space.")
        PrimaryButton(tr("Crear…", "Create…"), enabled = !busy, onClick = { pending = Pending(t, listOf(l), "", { ctx.runner.run(createPartition(drive.path, fs)) }) })
    }

    Panel {
        SectionLabel(tr("Tipo de tabla", "Table type"))
        if (tooBigForMbr) Text(tr("La unidad supera 2 TiB: solo cabe GPT.", "The drive is over 2 TiB: only GPT fits."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val tt = tr("Convertir la tabla de ${drive.name}", "Convert ${drive.name}'s table")
        val lg = tr("Se crea una tabla GPT nueva. Las particiones actuales desaparecen.", "A new GPT table is created. Current partitions disappear.")
        val lm = tr("Se crea una tabla MBR nueva. Las particiones actuales desaparecen.", "A new MBR table is created. Current partitions disappear.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("GPT", enabled = !busy, onClick = { pending = Pending(tt, listOf(lg), "", { ctx.runner.run(convertPartitionTable(drive.path, true)) }) })
            SecondaryButton("MBR", enabled = !busy && !tooBigForMbr, onClick = { pending = Pending(tt, listOf(lm), "", { ctx.runner.run(convertPartitionTable(drive.path, false)) }) })
        }
    }
    ConfirmHost(pending, drive) { pending = null }
}

@Composable
fun MountScreen(ctx: AppContext) = RequireDrive(ctx) { drive ->
    val info by rememberPartitions(ctx, drive)
    val scope = rememberCoroutineScope()
    val dir = UsbPlatform.workDir
    var mountPoint by remember { mutableStateOf(if (dir.isBlank()) "" else dir.trimEnd('/', '\\') + "/mnt") }
    var chosen by remember(drive.path) { mutableStateOf<Int?>(null) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val okMount = tr("Montada.", "Mounted.")
    val failMount = tr("No se pudo montar. Comprueba permisos, que la partición tiene sistema de archivos y que el punto de montaje existe.", "Could not mount. Check permissions, that the partition has a file system and that the mount point exists.")
    val okUn = tr("Desmontada.", "Unmounted.")
    val failUn = tr("No se pudo desmontar. Puede que algo la esté usando.", "Could not unmount. Something may be using it.")
    Panel {
        SectionLabel(tr("Partición", "Partition"))
        val parts = info?.parts
        if (parts == null) Text(tr("Leyendo particiones…", "Reading partitions…"))
        else if (parts.isEmpty()) Text(tr("La unidad no tiene particiones.", "The drive has no partitions."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            parts.forEach { Choice("${it.number}  ${it.size}", chosen == it.number, { chosen = it.number }) }
        }
        TextInput(mountPoint, { mountPoint = it }, tr("Punto de montaje", "Mount point"), mono = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(tr("Montar", "Mount"), enabled = chosen != null && mountPoint.isNotBlank() && !ctx.runner.running, onClick = {
                val n = chosen ?: return@PrimaryButton
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { mountPartition(partitionPath(drive.path, n), mountPoint.trim()) }
                    result = ok to (if (ok) okMount else failMount)
                }
            })
            SecondaryButton(tr("Desmontar", "Unmount"), enabled = mountPoint.isNotBlank() && !ctx.runner.running, onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { unmountPartition(mountPoint.trim()) }
                    result = ok to (if (ok) okUn else failUn)
                }
            })
        }
    }
    result?.let { (ok, msg) -> Note(msg, null, if (ok) NoteKind.OK else NoteKind.ERROR) }
}

@Composable
fun PlannedScreen(tool: Tool) {
    val body = when (tool) {
        Tool.VENTOY -> tr("Ventoy aún no está integrado en X-USB. Hasta entonces usa el instalador oficial de Ventoy; esta pantalla no hace nada.", "Ventoy is not integrated into X-USB yet. Until then use the official Ventoy installer; this screen does nothing.")
        else -> tr("Todavía no existe y no tiene fecha. Cuando esté, aparecerá aquí; esta pantalla no hace nada.", "Not built yet and no date. When it exists it will show up here; this screen does nothing.")
    }
    Note(tr("Planificado", "Planned"), body, NoteKind.INFO)
}
