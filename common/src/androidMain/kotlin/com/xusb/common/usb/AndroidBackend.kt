package com.xusb.common.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.xusb.common.usb.raw.RawOps
import com.xusb.common.usb.raw.UsbBlockDevice
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/** Shell root de Android vía libsu (el usuario debe conceder root a la app). */
class LibsuShell : ShellExec {
    init {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(15))
    }

    override val isRoot: Boolean get() = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    override val canElevate: Boolean get() = isRoot

    override fun exec(cmd: String, timeoutSec: Long, onChunk: ((String) -> Unit)?): ShellResult {
        val out = StringBuilder()
        val err = StringBuilder()
        val inline = Executor { it.run() }
        val outCb = object : CallbackList<String>(inline) {
            override fun onAddElement(s: String) { synchronized(out) { out.appendLine(s) }; onChunk?.invoke(s) }
        }
        val errCb = object : CallbackList<String>(inline) {
            override fun onAddElement(s: String) { synchronized(err) { err.appendLine(s) }; onChunk?.invoke(s) }
        }
        return try {
            val r = Shell.cmd(cmd).to(outCb, errCb).exec()
            ShellResult(r.code, out.toString(), err.toString())
        } catch (e: Exception) {
            ShellResult(127, "", "No se pudo ejecutar: ${e.message}")
        }
    }
}

/**
 * Android. Grabar, borrar, copiar y restaurar van por USB Host (sin root): la app habla con el
 * pendrive directamente y solo necesita que el usuario acepte el aviso de permiso de Android.
 * Formatear, particiones y Ventoy usan todavía las herramientas del sistema (parted, mkfs…) y
 * necesitan root; sin root fallan con un mensaje claro.
 *
 * Las unidades se identifican como «usb:<ruta de USB Host>» (por ejemplo usb:/dev/bus/usb/001/002).
 */
class AndroidBackend(context: Context) : UsbBackend {
    private val app = context.applicationContext
    private val usb = app.getSystemService(Context.USB_SERVICE) as UsbManager
    private val raw = RawOps(app)
    private val shell = ShellBackend(LibsuShell(), "Android")
    private val sizes = ConcurrentHashMap<String, Long>()
    private val asked = ConcurrentHashMap.newKeySet<Int>()

    override val platformName = "Android"

    /** Lo básico (grabar, borrar, copiar) funciona sin root, así que la app no bloquea nada de entrada. */
    override fun isPrivileged() = true

    override fun listDrives(): List<UsbDriveInfo> {
        val devices = usb.deviceList.values.filter { UsbBlockDevice.isMassStorage(it) }
        val live = devices.map { it.deviceName }.toSet()
        sizes.keys.retainAll(live)
        return devices.mapNotNull { d ->
            if (!usb.hasPermission(d)) { askPermission(d); return@mapNotNull null }
            val size = sizes[d.deviceName] ?: readSize(d)?.also { sizes[d.deviceName] = it } ?: 0L
            UsbDriveInfo(
                name = d.productName?.trim()?.ifEmpty { null } ?: d.manufacturerName?.trim()?.ifEmpty { null } ?: "USB",
                path = PREFIX + d.deviceName,
                sizeBytes = size,
                isConnected = true,
                usbVersion = if (d.version.startsWith("3")) "USB 3" else "USB 2",
            )
        }
    }

    // ── Sin root ─────────────────────────────────────────────────────────

    override fun rawWrite(imagePath: String, device: String) = withDevice(device) { raw.writeImage(it, imagePath) }
    override fun restore(imagePath: String, device: String) = withDevice(device) { raw.writeImage(it, imagePath) }
    override fun backup(device: String, imagePath: String) = withDevice(device) { raw.backup(it, imagePath) }
    override fun wipe(device: String) = withDevice(device) { raw.wipe(it) }
    override fun downloadFile(url: String, destPath: String) = shell.downloadFile(url, destPath)
    override fun binToIso(binPath: String, isoPath: String) = shell.binToIso(binPath, isoPath)
    override fun eject(device: String) = true

    // ── Con root (de momento) ────────────────────────────────────────────

    override fun format(device: String, fs: Filesystem, label: String, clusterSize: Int) = rooted(device) { shell.format(it, fs, label, clusterSize) }
    override fun installVentoy(context: Any?, device: String, useGpt: Boolean, secureBoot: Boolean, update: Boolean) =
        rooted(device) { shell.installVentoy(context, it, useGpt, secureBoot, update) }
    override fun convertPartitionTable(device: String, toGpt: Boolean) = rooted(device) { shell.convertPartitionTable(it, toGpt) }
    override fun createPartition(device: String, fs: Filesystem) = rooted(device) { shell.createPartition(it, fs) }
    override fun deletePartition(device: String, partNum: Int) = rooted(device) { shell.deletePartition(it, partNum) }
    override fun listPartitions(device: String): List<PartitionInfo> = blockNode(device)?.let { shell.listPartitions(it) } ?: emptyList()
    override fun partitionTableType(device: String): String = blockNode(device)?.let { shell.partitionTableType(it) } ?: "Unknown"
    override fun partitionPath(device: String, n: Int): String = shell.partitionPath(blockNode(device) ?: device, n)
    override fun mountPartition(partition: String, mountPoint: String) = shell.mountPartition(partition, mountPoint)
    override fun unmountPartition(mountPoint: String) = shell.unmountPartition(mountPoint)

    // ── Internos ─────────────────────────────────────────────────────────

    private fun find(device: String): UsbDevice? =
        usb.deviceList.values.firstOrNull { PREFIX + it.deviceName == device }

    private fun withDevice(device: String, op: (UsbDevice) -> Flow<OperationProgress>): Flow<OperationProgress> {
        val d = find(device) ?: return failure("El USB ya no está conectado.")
        if (!usb.hasPermission(d)) { askPermission(d); return failure("Acepta el aviso de permiso de Android y vuelve a intentarlo.") }
        return op(d)
    }

    /** Nodo de bloque (/dev/sdX) del USB, que solo existe para root. Se empareja por tamaño. */
    private fun blockNode(device: String): String? {
        if (!device.startsWith(PREFIX)) return device
        if (!shell.isPrivileged()) return null
        val want = sizes[device.removePrefix(PREFIX)] ?: 0L
        val drives = shell.listDrives()
        return drives.firstOrNull { want > 0 && kotlin.math.abs(it.sizeBytes - want) < 1_048_576L }?.path
            ?: drives.singleOrNull()?.path
    }

    private fun rooted(device: String, op: (String) -> Flow<OperationProgress>): Flow<OperationProgress> {
        if (!shell.isPrivileged()) return failure("Esta función todavía necesita root. Grabar, borrar y copiar funcionan sin root.")
        val node = blockNode(device) ?: return failure("No encuentro el nodo de bloque de este USB.")
        return op(node)
    }

    private fun readSize(d: UsbDevice): Long? =
        try { UsbBlockDevice.open(app, d).use { it.sizeBytes } } catch (_: Exception) { null }

    private fun askPermission(d: UsbDevice) {
        if (!asked.add(d.deviceId)) return
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(app.packageName)
        usb.requestPermission(d, PendingIntent.getBroadcast(app, d.deviceId, intent, flags))
    }

    companion object {
        const val PREFIX = "usb:"
        const val ACTION_USB_PERMISSION = "com.xusb.USB_PERMISSION"
    }
}
