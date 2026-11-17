package com.xusb.common.usb

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Operaciones sobre dispositivos USB. Cada plataforma aporta su implementación:
 * Android (root + USB Host), Linux (parted/mkfs/dd) y Windows (PowerShell).
 * La interfaz y las pantallas son comunes; solo cambia lo que toca el disco.
 *
 * Regla de seguridad: cada backend comprueba por su cuenta que el destino es un
 * USB extraíble conocido (nunca el disco del sistema) antes de escribir nada.
 */
interface UsbBackend {
    val platformName: String

    /** true si el proceso ya puede tocar discos (root en Linux, administrador en Windows). */
    fun isPrivileged(): Boolean

    fun listDrives(): List<UsbDriveInfo>

    fun format(device: String, fs: Filesystem, label: String, clusterSize: Int): Flow<OperationProgress>
    fun wipe(device: String): Flow<OperationProgress>
    fun rawWrite(imagePath: String, device: String): Flow<OperationProgress>
    fun backup(device: String, imagePath: String): Flow<OperationProgress>
    fun restore(imagePath: String, device: String): Flow<OperationProgress>
    fun installVentoy(context: Any?, device: String, useGpt: Boolean, secureBoot: Boolean, update: Boolean): Flow<OperationProgress>

    /** Descarga una URL a un fichero (no toca discos, pero el destino lo decide cada plataforma). */
    fun downloadFile(url: String, destPath: String): Flow<OperationProgress>
    /** Convierte una imagen BIN (2352 bytes/sector) a ISO (2048). */
    fun binToIso(binPath: String, isoPath: String): Flow<OperationProgress>

    fun listPartitions(device: String): List<PartitionInfo>
    fun partitionTableType(device: String): String
    fun convertPartitionTable(device: String, toGpt: Boolean): Flow<OperationProgress>
    fun createPartition(device: String, fs: Filesystem): Flow<OperationProgress>
    fun deletePartition(device: String, partNum: Int): Flow<OperationProgress>

    /** Ruta con la que referirse a la partición [n] de [device] (en Windows no basta concatenar). */
    fun partitionPath(device: String, n: Int): String
    fun mountPartition(partition: String, mountPoint: String): Boolean
    fun unmountPartition(mountPoint: String): Boolean
    fun eject(device: String): Boolean
}

/** Backend activo. Cada aplicación lo fija al arrancar. */
object UsbPlatform {
    @Volatile
    var backend: UsbBackend = NoBackend
}

/** Marcador: se usa mientras ninguna plataforma haya registrado el suyo. Todo falla de forma explícita. */
object NoBackend : UsbBackend {
    private const val MSG = "Esta plataforma no tiene soporte de USB todavía."
    override val platformName = "ninguna"
    override fun isPrivileged() = false
    override fun listDrives() = emptyList<UsbDriveInfo>()
    override fun format(device: String, fs: Filesystem, label: String, clusterSize: Int) = failure(MSG)
    override fun wipe(device: String) = failure(MSG)
    override fun rawWrite(imagePath: String, device: String) = failure(MSG)
    override fun backup(device: String, imagePath: String) = failure(MSG)
    override fun restore(imagePath: String, device: String) = failure(MSG)
    override fun installVentoy(context: Any?, device: String, useGpt: Boolean, secureBoot: Boolean, update: Boolean) = failure(MSG)
    override fun downloadFile(url: String, destPath: String) = failure(MSG)
    override fun binToIso(binPath: String, isoPath: String) = failure(MSG)
    override fun listPartitions(device: String) = emptyList<PartitionInfo>()
    override fun partitionTableType(device: String) = "Unknown"
    override fun convertPartitionTable(device: String, toGpt: Boolean) = failure(MSG)
    override fun createPartition(device: String, fs: Filesystem) = failure(MSG)
    override fun deletePartition(device: String, partNum: Int) = failure(MSG)
    override fun partitionPath(device: String, n: Int) = "$device$n"
    override fun mountPartition(partition: String, mountPoint: String) = false
    override fun unmountPartition(mountPoint: String) = false
    override fun eject(device: String) = false
}

// ── Utilidades compartidas por todos los backends ─────────────────────────

fun failure(message: String): Flow<OperationProgress> =
    flowOf(OperationProgress(isRunning = false, error = message))

fun progressStep(label: String, pct: Float) =
    OperationProgress(isRunning = true, label = label, progressPct = pct)

fun progressDone(label: String) =
    OperationProgress(isRunning = false, label = label, progressPct = 1f, isComplete = true)

fun progressError(message: String) = OperationProgress(isRunning = false, error = message)

fun Filesystem.maxLabelLength(): Int = when (this) {
    Filesystem.FAT16, Filesystem.FAT32 -> 11
    Filesystem.EXFAT -> 15
    Filesystem.NTFS -> 32
    Filesystem.EXT2, Filesystem.EXT3, Filesystem.EXT4 -> 16
    Filesystem.F2FS -> 19
}

/**
 * Deja solo ASCII seguro (letras, cifras, guion, guion bajo y espacio) y recorta al máximo del sistema
 * de archivos. Las etiquetas acaban en líneas de comandos, así que esto también evita inyecciones.
 */
fun sanitizeLabel(raw: String, fs: Filesystem): String {
    val ok = raw.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' || it == ' ' }.trim()
    val base = ok.ifEmpty { "X-USB" }.take(fs.maxLabelLength())
    return if (fs == Filesystem.FAT16 || fs == Filesystem.FAT32) base.uppercase() else base
}

fun humanBytes(b: Long): String = when {
    b >= 1_073_741_824L -> "%.1f GB".format(b / 1_073_741_824.0)
    b >= 1_048_576L -> "%.1f MB".format(b / 1_048_576.0)
    b >= 1024L -> "%.1f KB".format(b / 1024.0)
    else -> "$b B"
}

/** Un disco de más de 2 TiB no cabe en una tabla MBR. */
const val MBR_LIMIT_BYTES: Long = 2L * 1024 * 1024 * 1024 * 1024
