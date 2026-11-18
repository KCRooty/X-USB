package com.xusb.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xusb.common.ui.LocalSpanish
import com.xusb.common.ui.components.*
import com.xusb.common.ui.screens.*
import com.xusb.common.ui.state.DriveState
import com.xusb.common.ui.state.OpRunner
import com.xusb.common.ui.theme.AppTheme
import com.xusb.common.ui.theme.XUsbTheme
import com.xusb.common.ui.tr
import kotlinx.coroutines.flow.Flow

/** Herramientas de la app. `planned` = todavía sin implementar: se muestran como tales, nunca como si funcionaran. */
enum class Tool(val group: Int, val planned: Boolean = false) {
    WRITE(0), DOWNLOAD(0),
    FORMAT(1), WIPE(1), PARTITIONS(1), MOUNT(1),
    BACKUP(2),
    VENTOY(3, true), MULTIBOOT(3, true), PS2(3, true), FILES(3, true);

    fun title(es: Boolean) = when (this) {
        FORMAT -> if (es) "Formatear" else "Format"
        WIPE -> if (es) "Borrar todo" else "Wipe"
        PARTITIONS -> if (es) "Particiones" else "Partitions"
        MOUNT -> if (es) "Montar" else "Mount"
        WRITE -> if (es) "Grabar imagen" else "Write image"
        DOWNLOAD -> if (es) "Descargar ISO" else "Download ISO"
        BACKUP -> if (es) "Copia de seguridad" else "Backup"
        VENTOY -> "Ventoy"
        MULTIBOOT -> if (es) "Multi-arranque UEFI" else "Multi-boot UEFI"
        PS2 -> "PS2 USB"
        FILES -> if (es) "Archivos" else "Files"
    }

    fun summary(es: Boolean) = when (this) {
        FORMAT -> if (es) "Crea un sistema de archivos nuevo" else "Create a new file system"
        WIPE -> if (es) "Pone toda la unidad a cero" else "Zero the whole drive"
        PARTITIONS -> if (es) "Ver, crear y borrar particiones" else "List, create and delete partitions"
        MOUNT -> if (es) "Montar una partición en una carpeta" else "Mount a partition on a folder"
        WRITE -> if (es) "Escribe una ISO o IMG en la unidad" else "Write an ISO or IMG to the drive"
        DOWNLOAD -> if (es) "Baja imágenes de distros a este equipo" else "Fetch distro images to this device"
        BACKUP -> if (es) "Guarda o restaura la unidad entera" else "Save or restore the whole drive"
        VENTOY -> if (es) "Varias ISO en una sola unidad" else "Several ISOs on one drive"
        MULTIBOOT -> if (es) "Arranque UEFI con varios sistemas" else "UEFI boot with several systems"
        PS2 -> if (es) "Preparar USB para juegos de PS2" else "Prepare a USB for PS2 games"
        FILES -> if (es) "Explorar archivos de la unidad" else "Browse the drive's files"
    }
}

private val GROUP_TITLES = listOf(
    "Escribir" to "Write", "Gestionar" to "Manage", "Copias" to "Backups", "Planificado" to "Planned"
)

class AppContext(val drives: DriveState, val runner: OpRunner) {
    /** Última imagen descargada; la pantalla de grabar la ofrece como origen. */
    var lastImage by mutableStateOf("")
}

@Composable
fun App(vm: IMainViewModel, usbEvents: Flow<Int>? = null) {
    val theme by vm.theme.collectAsState()
    val lang by vm.lang.collectAsState()
    val es = lang == 1
    val scope = rememberCoroutineScope()
    val drives = remember { DriveState(scope) }
    val ctx = remember { AppContext(drives, OpRunner(scope, drives)) }
    var route by remember { mutableStateOf<Tool?>(null) }

    LaunchedEffect(Unit) { drives.refresh() }
    LaunchedEffect(usbEvents) {
        // Android avisa de conexiones; el sistema tarda un instante en crear el nodo de bloque.
        usbEvents?.collect { kotlinx.coroutines.delay(1500); drives.refresh() }
    }

    CompositionLocalProvider(LocalSpanish provides es) {
        XUsbTheme(theme) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (maxWidth >= 840.dp) {
                        val current = route ?: Tool.WRITE
                        Row(Modifier.fillMaxSize()) {
                            Sidebar(vm, theme, es, ctx, current, onRoute = { route = it }, modifier = Modifier.width(300.dp))
                            Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                            ToolFrame(current, ctx, es, showBack = false, onBack = {}, modifier = Modifier.weight(1f))
                        }
                    } else {
                        val t = route
                        if (t == null) PhoneHome(vm, theme, es, ctx, onRoute = { route = it })
                        else ToolFrame(t, ctx, es, showBack = true, onBack = { route = null }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

// ── Escritorio: barra lateral con unidad y herramientas ──────────────────
@Composable
private fun Sidebar(vm: IMainViewModel, theme: AppTheme, es: Boolean, ctx: AppContext, current: Tool, onRoute: (Tool) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Brand()
        DrivePicker(ctx.drives)
        Tool.entries.groupBy { it.group }.forEach { (g, tools) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel(if (es) GROUP_TITLES[g].first else GROUP_TITLES[g].second)
                tools.forEach { NavItem(it.title(es), it == current, it.planned) { onRoute(it) } }
            }
        }
        Preferences(vm, theme, es)
    }
}

@Composable
private fun Brand() {
    Column {
        Text("X-USB", style = MaterialTheme.typography.displayLarge, fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
        Text("Static Forge", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, planned: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) accent.copy(alpha = 0.14f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.Tab, onClick = onClick)
    ) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else if (planned) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun Preferences(vm: IMainViewModel, theme: AppTheme, es: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(tr("Apariencia", "Appearance"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(tr("Forja", "Forge"), theme == AppTheme.FORGE, { vm.setTheme(AppTheme.FORGE) })
            Choice(tr("Papel", "Paper"), theme == AppTheme.PAPER, { vm.setTheme(AppTheme.PAPER) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("ES", es, { if (!es) vm.toggleLang() })
            Choice("EN", !es, { if (es) vm.toggleLang() })
        }
        Text("KCRooty (Core) · Static Forge", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Móvil: inicio con unidad y lista de herramientas ─────────────────────
@Composable
private fun PhoneHome(vm: IMainViewModel, theme: AppTheme, es: Boolean, ctx: AppContext, onRoute: (Tool) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Brand()
        DrivePicker(ctx.drives)
        OperationPanel(ctx.runner)
        Tool.entries.groupBy { it.group }.forEach { (g, tools) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(if (es) GROUP_TITLES[g].first else GROUP_TITLES[g].second)
                tools.forEach { t ->
                    Surface(
                        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().clickable { onRoute(t) }
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(t.title(es) + if (t.planned) tr("  (planificado)", "  (planned)") else "", style = MaterialTheme.typography.titleMedium)
                            Text(t.summary(es), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Preferences(vm, theme, es)
    }
}

// ── Marco común de cada herramienta ──────────────────────────────────────
@Composable
private fun ToolFrame(tool: Tool, ctx: AppContext, es: Boolean, showBack: Boolean, onBack: () -> Unit, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showBack) SecondaryButton(tr("Volver", "Back"), onClick = onBack)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tool.title(es), style = MaterialTheme.typography.headlineMedium)
                Text(tool.summary(es), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showBack) DrivePill(ctx.drives)
            if (tool.planned) PlannedScreen(tool)
            else when (tool) {
                Tool.FORMAT -> FormatScreen(ctx)
                Tool.WIPE -> WipeScreen(ctx)
                Tool.PARTITIONS -> PartitionsScreen(ctx)
                Tool.MOUNT -> MountScreen(ctx)
                Tool.WRITE -> WriteScreen(ctx)
                Tool.DOWNLOAD -> DownloadScreen(ctx)
                Tool.BACKUP -> BackupScreen(ctx)
                else -> {}
            }
            OperationPanel(ctx.runner)
        }
    }
}
