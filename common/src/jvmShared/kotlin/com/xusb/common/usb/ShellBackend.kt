package com.xusb.common.usb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

data class ShellResult(val code: Int, val out: String, val err: String = "") {
    val ok get() = code == 0
    val text get() = (err.ifBlank { out }).trim().lines().lastOrNull().orEmpty()
}

/** Ejecuta órdenes de shell con privilegios (pkexec/root en PC, su vía libsu en Android). */
interface ShellExec {
    val isRoot: Boolean

    /** false si no hay forma de obtener privilegios (p. ej. Android sin root): las escrituras se rechazan. */
    val canElevate: Boolean get() = true

    /** [onChunk] recibe cada trozo de salida separado por \r o \n (dd status=progress usa \r). */
    fun exec(cmd: String, timeoutSec: Long = 600, onChunk: ((String) -> Unit)? = null): ShellResult
}

/** Comillas simples seguras para meter un valor en una orden de shell. */
fun shq(s: String) = "'" + s.replace("'", "'\\''") + "'"

/**
 * Operaciones de USB sobre un sistema Linux/Android con herramientas estándar (parted, sgdisk, mkfs.*, dd).
 * Descubrimiento y guardas por sysfs y /proc/mounts: no dependen de lsblk.
 * Seguridad: solo se toca un disco cuyo bus sea USB y que no tenga montado nada del sistema;
 * las pruebas pueden añadir loops con [testDevices].
 */
open class ShellBackend(private val sh: ShellExec, override val platformName: String, private val testDevices: () -> Set<String> = { emptySet() }) : UsbBackend {

    override fun isPrivileged() = sh.isRoot

    private fun x(cmd: String, timeoutSec: Long = 600) = sh.exec(cmd, timeoutSec)

    // ── Descubrimiento y guardas ─────────────────────────────────────────

    private class Disk(val path: String, val size: Long, val model: String, val usb: Boolean)

    private fun disks(): List<Disk> {
        val script = "for b in /sys/block/*; do n=\${b##*/}; case \$n in loop*|ram*|zram*|dm-*|md*|nbd*) continue;; esac; " +
            "echo \"\$n|\$(cat \$b/size 2>/dev/null)|\$(cat \$b/device/model 2>/dev/null)|\$(readlink -f \$b)\"; done"
        val r = x(script, 60)
        return r.out.lines().map { it.trim() }.filter { it.contains('|') }.mapNotNull { l ->
            val f = l.split('|')
            val name = f.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Disk("/dev/$name", (f.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L) * 512L, f.getOrElse(2) { "" }.trim(), f.getOrElse(3) { "" }.contains("/usb"))
        }
    }

    private fun mounts(device: String): List<String> {
        val re = Regex("^" + Regex.escape(device) + "(p?\\d+)?$")
        return x("cat /proc/mounts", 15).out.lines().mapNotNull { l ->
            val f = l.split(' ')
            if (f.size >= 2 && re.matches(f[0])) f[1].replace("\\040", " ") else null
        }
    }

    private fun systemMounted(device: String): Boolean {
        val critical = setOf("/", "/boot", "/boot/efi", "/home", "/usr", "/var", "/data", "/system", "/vendor", "/storage/emulated")
        return mounts(device).any { it in critical }
    }

    override fun listDrives(): List<UsbDriveInfo> =
        disks().filter { it.usb && !systemMounted(it.path) }
            .map { UsbDriveInfo(it.model.ifBlank { "USB Drive" }, it.path, it.size, true, "USB") }

    /** Mensaje de error si [device] no es un USB seguro; null si se puede tocar. */
    private fun guard(device: String): String? {
        if (!Regex("^/dev/[A-Za-z0-9]+$").matches(device)) return "Ruta de dispositivo no válida."
        if (!sh.canElevate) return "Se necesita acceso root para modificar discos USB."
        if (!x("test -b ${shq(device)}", 15).ok) return "No existe el dispositivo $device."
        val d = disks().firstOrNull { it.path == device }
        val test = device in testDevices()
        if (!test && (d == null || !d.usb)) return "$device no es un disco USB: me niego a tocarlo."
        if (systemMounted(device)) return "$device contiene el sistema (montado en / o similar): me niego a tocarlo."
        return null
    }

    private fun sizeOf(device: String): Long =
        x("cat /sys/block/${device.removePrefix("/dev/")}/size", 15).out.trim().toLongOrNull()?.times(512L)
            ?: x("blockdev --getsize64 ${shq(device)}", 15).out.trim().toLongOrNull() ?: 0L

    private fun unmountAll(device: String) { mounts(device).forEach { x("umount ${shq(it)}", 60) } }

    override fun partitionPath(device: String, n: Int) = if (device.last().isDigit()) "${device}p$n" else "$device$n"

    private fun settle(device: String) {
        x("udevadm settle 2>/dev/null; (partprobe ${shq(device)} || blockdev --rereadpt ${shq(device)}) 2>/dev/null; sleep 1", 60)
    }

    // ── Formato ──────────────────────────────────────────────────────────

    private fun partedFs(fs: Filesystem) = when (fs) {
        Filesystem.FAT16 -> "fat16"; Filesystem.FAT32 -> "fat32"
        Filesystem.EXFAT, Filesystem.NTFS -> "ntfs"
        Filesystem.EXT2 -> "ext2"; Filesystem.EXT3, Filesystem.EXT4 -> "ext4"
        Filesystem.F2FS -> "ext4"
    }

    private fun mkfsCommand(fs: Filesystem, part: String, label: String, cluster: Int): String {
        val l = shq(label); val p = shq(part)
        return when (fs) {
            Filesystem.FAT16, Filesystem.FAT32 ->
                "mkfs.fat -F ${if (fs == Filesystem.FAT16) 16 else 32} -n $l${if (cluster >= 512) " -s ${cluster / 512}" else ""} $p"
            Filesystem.EXFAT -> "mkfs.exfat -L $l${if (cluster >= 512) " -c $cluster" else ""} $p"
            Filesystem.NTFS -> "mkntfs -f -L $l${if (cluster >= 512) " -c $cluster" else ""} $p"
            Filesystem.EXT2 -> "mkfs.ext2 -F -L $l $p"
            Filesystem.EXT3 -> "mkfs.ext3 -F -L $l $p"
            Filesystem.EXT4 -> "mkfs.ext4 -F -L $l $p"
            Filesystem.F2FS -> "mkfs.f2fs -f -l $l $p"
        }
    }

    private fun makeFs(device: String, n: Int, fs: Filesystem, label: String, cluster: Int): String? {
        val part = partitionPath(device, n)
        repeat(20) { if (x("test -b ${shq(part)}", 10).ok) return@repeat else Thread.sleep(300) }
        val r = x(mkfsCommand(fs, part, sanitizeLabel(label, fs), cluster))
        return if (r.ok) null else "mkfs falló (¿falta la herramienta mkfs para ${fs.label}?): ${r.text}"
    }

    override fun format(device: String, fs: Filesystem, label: String, clusterSize: Int): Flow<OperationProgress> = flow {
        guard(device)?.let { emit(progressError(it)); return@flow }
        emit(progressStep("Desmontando…", 0.05f)); unmountAll(device)
        emit(progressStep("Limpiando firmas…", 0.15f))
        x("wipefs -a ${shq(device)}")
        val table = if (sizeOf(device) > MBR_LIMIT_BYTES) "gpt" else "msdos"
        emit(progressStep("Creando tabla $table…", 0.30f))
        var r = x("parted -s ${shq(device)} mklabel $table")
        if (!r.ok) { emit(progressError("parted mklabel: ${r.text}")); return@flow }
        r = x("parted -s -a optimal -- ${shq(device)} mkpart primary ${partedFs(fs)} 1MiB -1MiB")
        if (!r.ok) { emit(progressError("parted mkpart: ${r.text}")); return@flow }
        emit(progressStep("Esperando a que el sistema vea la partición…", 0.5f)); settle(device)
        emit(progressStep("Formateando ${fs.label}…", 0.65f))
        makeFs(device, 1, fs, label, clusterSize)?.let { emit(progressError(it)); return@flow }
        x("sync")
        emit(progressDone("Formateado como ${fs.label} ✓"))
    }.flowOn(Dispatchers.IO)

    // ── dd con progreso ──────────────────────────────────────────────────

    private fun dd(input: String, output: String, total: Long, label: String): Flow<OperationProgress> = flow {
        emit(progressStep("Preparando…", 0f).copy(totalBytes = total))
        val start = System.currentTimeMillis()
        var last = 0L
        val pending = mutableListOf<OperationProgress>()
        val onChunk: (String) -> Unit = { raw ->
            // libsu entrega líneas enteras: el progreso de dd (separado por \r) llega junto.
            val n = raw.split('\r', '\n').mapNotNull { Regex("^\\s*(\\d+) bytes").find(it)?.groupValues?.get(1)?.toLongOrNull() }.maxOrNull()
            if (n != null && n != last) {
                last = n
                val sec = (System.currentTimeMillis() - start) / 1000.0
                synchronized(pending) {
                    pending += progressStep(label, if (total > 0) (n.toFloat() / total).coerceAtMost(0.99f) else 0f)
                        .copy(writtenBytes = n, totalBytes = total, speedMbs = if (sec > 0) n / sec / 1_048_576.0 else 0.0)
                }
            }
        }
        // Los chunks se acumulan desde el hilo del ejecutor; se emiten desde el flujo con un sondeo corto.
        var res: ShellResult? = null
        val t = Thread { res = sh.exec("dd if=${shq(input)} of=${shq(output)} bs=4M conv=fsync status=progress", 24 * 3600L, onChunk) }
        t.start()
        while (t.isAlive) {
            t.join(300)
            val batch = synchronized(pending) { pending.toList().also { pending.clear() } }
            batch.lastOrNull()?.let { emit(it) }
        }
        val r = res ?: ShellResult(1, "", "dd no devolvió resultado")
        // Con /dev/zero el final normal es "No space left on device".
        val normalEnd = input == "/dev/zero" && (last > 0 || (r.out + r.err).contains("No space left", ignoreCase = true))
        if (!r.ok && !normalEnd) {
            // toybox/busybox antiguos no admiten status=progress: reintento sin él.
            if (last == 0L && (r.out + r.err).contains("status", ignoreCase = true)) {
                val r2 = x("dd if=${shq(input)} of=${shq(output)} bs=4M conv=fsync", 24 * 3600L)
                if (!r2.ok) { emit(progressError("dd terminó con error: ${r2.text}")); return@flow }
            } else { emit(progressError("dd terminó con error: ${r.text}")); return@flow }
        }
        emit(progressDone("$label — completado ✓").copy(writtenBytes = total, totalBytes = total))
    }.flowOn(Dispatchers.IO)

    override fun wipe(device: String): Flow<OperationProgress> = flow {
        guard(device)?.let { emit(progressError(it)); return@flow }
        unmountAll(device); x("wipefs -a ${shq(device)}")
        emitAll(dd("/dev/zero", device, sizeOf(device), "Borrando disco"))
    }

    override fun rawWrite(imagePath: String, device: String): Flow<OperationProgress> = flow {
        guard(device)?.let { emit(progressError(it)); return@flow }
        val len = x("stat -c %s ${shq(imagePath)}", 15).out.trim().toLongOrNull() ?: File(imagePath).length()
        if (len <= 0) { emit(progressError("No se encuentra la imagen.")); return@flow }
        if (len > sizeOf(device)) { emit(progressError("La imagen (${humanBytes(len)}) no cabe en el disco (${humanBytes(sizeOf(device))}).")); return@flow }
        unmountAll(device)
        emitAll(dd(imagePath, device, len, "Escribiendo imagen"))
        settle(device)
    }

    override fun restore(imagePath: String, device: String) = rawWrite(imagePath, device)

    override fun backup(device: String, imagePath: String): Flow<OperationProgress> = flow {
        guard(device)?.let { emit(progressError(it)); return@flow }
        emitAll(dd(device, imagePath, sizeOf(device), "Copiando disco"))
    }

    override fun installVentoy(context: Any?, device: String, useGpt: Boolean, secureBoot: Boolean, update: Boolean) =
        failure("Ventoy aún no está integrado. Descarga Ventoy desde ventoy.net y úsalo sobre este USB; el soporte llegará en una versión próxima.")

    override fun downloadFile(url: String, destPath: String) = JvmOps.download(url, destPath)
    override fun binToIso(binPath: String, isoPath: String) = JvmOps.binToIso(binPath, isoPath)

    // ── Particiones ──────────────────────────────────────────────────────

    private fun partedMachine(device: String) = x("parted -m -s ${shq(device)} unit MB print", 60)

    override fun listPartitions(device: String): List<PartitionInfo> {
        if (guard(device) != null) return emptyList()
        return partedMachine(device).out.lines().filter { Regex("^\\d+:").containsMatchIn(it) }.map { l ->
            val f = l.trimEnd(';').split(':')
            PartitionInfo(f[0].toInt(), f.getOrElse(1) { "" }, f.getOrElse(2) { "" }, f.getOrElse(3) { "" }, f.getOrElse(4) { "" }, f.getOrElse(6) { "" })
        }
    }

    override fun partitionTableType(device: String): String {
        if (guard(device) != null) return "Unknown"
        val line = partedMachine(device).out.lines().firstOrNull { it.startsWith("/dev/") } ?: return "Unknown"
        return when (line.split(':').getOrNull(5)) { "msdos" -> "MBR"; "gpt" -> "GPT"; null, "" -> "Unknown"; else -> "Other" }
    }

    override fun convertPartitionTable(device: String, toGpt: Boolean): Flow<OperationProgress> = flow {
        guard(device)?.let { emit(progressError(it)); return@flow }
        if (!toGpt && sizeOf(device) > MBR_LIMIT_BYTES) { emit(progressError("El disco supera 2 TiB: no cabe en MBR.")); return@flow }
        emit(progressStep("Convirtiendo a ${if (toGpt) "GPT" else "MBR"}…", 0.4f))
        val r = x("sgdisk ${if (toGpt) "-g" else "-m"} ${shq(device)}")
        if (!r.ok) {
            val hint = if (toGpt) " Si la última partición llega hasta el final del disco, no hay sitio para la cabecera GPT: reduce esa partición ~1 MiB." else ""
            emit(progressError("sgdisk: ${r.text}.$hint")); return@flow
        }
        settle(device)
        emit(progressDone("Tabla convertida a ${if (toGpt) "GPT" else "MBR"} ✓"))
    }.flowOn(Dispatchers.IO)

    override fun createPartition(device: String, fs: Filesystem): Flow<OperationProgress> = flow {
        guard(device)?.let { emit(progressError(it)); return@flow }
        val startMb = listPartitions(device).lastOrNull()?.end?.removeSuffix("MB")?.toDoubleOrNull()?.let { "${it.toLong() + 1}MB" } ?: "1MiB"
        emit(progressStep("Creando partición…", 0.3f))
        val r = x("parted -s -a optimal -- ${shq(device)} mkpart primary ${partedFs(fs)} $startMb -1MiB")
        if (!r.ok) { emit(progressError("parted: ${r.text}")); return@flow }
        settle(device)
        val n = listPartitions(device).maxOfOrNull { it.number } ?: 1
        emit(progressStep("Formateando ${fs.label}…", 0.7f))
        makeFs(device, n, fs, "X-USB", 0)?.let { emit(progressError(it)); return@flow }
        emit(progressDone("Partición $n creada ✓"))
    }.flowOn(Dispatchers.IO)

    override fun deletePartition(device: String, partNum: Int): Flow<OperationProgress> = flow {
        guard(device)?.let { emit(progressError(it)); return@flow }
        unmountAll(device)
        val r = x("parted -s ${shq(device)} rm $partNum")
        if (!r.ok) { emit(progressError("parted: ${r.text}")); return@flow }
        settle(device)
        emit(progressDone("Partición $partNum eliminada ✓"))
    }.flowOn(Dispatchers.IO)

    override fun mountPartition(partition: String, mountPoint: String): Boolean {
        if (!Regex("^/dev/[A-Za-z0-9]+$").matches(partition)) return false
        return x("mkdir -p ${shq(mountPoint)} && mount ${shq(partition)} ${shq(mountPoint)}", 60).ok
    }

    override fun unmountPartition(mountPoint: String) = x("umount ${shq(mountPoint)}", 60).ok

    override fun eject(device: String): Boolean {
        if (guard(device) != null) return false
        unmountAll(device); x("sync")
        x("udisksctl power-off -b ${shq(device)} 2>/dev/null", 30)
        return true
    }
}
