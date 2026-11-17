package com.xusb.desktop.backend

import com.xusb.common.usb.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Linux: lsblk / parted / sgdisk / mkfs.* / dd. Si no somos root se eleva cada orden con pkexec.
 * Seguridad: solo se actúa sobre discos que lsblk marca como USB (TRAN=usb) y que no montan
 * nada del sistema; las pruebas pueden añadir loops con -Dxusb.testDevices=/dev/loopN.
 */
class LinuxBackend : UsbBackend {
    override val platformName = "Linux"

    private val uid: Int by lazy { run("id", "-u").out.trim().toIntOrNull() ?: 1000 }
    override fun isPrivileged() = uid == 0

    private fun sudo(vararg cmd: String): List<String> = if (isPrivileged()) cmd.toList() else listOf("pkexec") + cmd
    private fun priv(vararg cmd: String, timeoutSec: Long = 600) = runList(sudo(*cmd), timeoutSec)

    // ── Descubrimiento y guardas ─────────────────────────────────────────

    private class Disk(val path: String, val size: Long, val model: String, val tran: String, val type: String)

    private fun lsblk(): List<Disk> {
        val r = run("lsblk", "-d", "-n", "-b", "-P", "-o", "NAME,SIZE,MODEL,TRAN,TYPE")
        if (!r.ok) return emptyList()
        return r.out.lines().filter { it.isNotBlank() }.map { line ->
            val kv = Regex("""(\w+)="([^"]*)"""").findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
            Disk("/dev/" + kv["NAME"].orEmpty(), kv["SIZE"]?.toLongOrNull() ?: 0L, kv["MODEL"].orEmpty().trim(), kv["TRAN"].orEmpty(), kv["TYPE"].orEmpty())
        }
    }

    private fun systemMounted(device: String): Boolean {
        val r = run("lsblk", "-n", "-r", "-o", "MOUNTPOINT", device)
        val critical = setOf("/", "/boot", "/boot/efi", "/home", "/usr", "/var", "[SWAP]")
        return r.out.lines().map { it.trim() }.any { it in critical }
    }

    override fun listDrives(): List<UsbDriveInfo> =
        lsblk().filter { it.type == "disk" && it.tran == "usb" && !systemMounted(it.path) }
            .map { UsbDriveInfo(it.model.ifBlank { "USB Drive" }, it.path, it.size, true, "USB") }

    /** Devuelve un mensaje de error si [device] no es un USB seguro, o null si se puede tocar. */
    private fun guard(device: String): String? {
        if (!Regex("^/dev/[A-Za-z0-9]+$").matches(device)) return "Ruta de dispositivo no válida."
        val d = lsblk().firstOrNull { it.path == device } ?: return "No existe el dispositivo $device."
        val allowedByTest = device in testDevices()
        if (!allowedByTest && d.tran != "usb") return "$device no es un disco USB: me niego a tocarlo."
        if (systemMounted(device)) return "$device contiene el sistema (montado en / o similar): me niego a tocarlo."
        return null
    }

    private fun sizeOf(device: String) = lsblk().firstOrNull { it.path == device }?.size ?: 0L

    private fun unmountAll(device: String) {
        run("lsblk", "-n", "-r", "-o", "MOUNTPOINT", device).out.lines().map { it.trim() }
            .filter { it.startsWith("/") }.forEach { priv("umount", it) }
    }

    override fun partitionPath(device: String, n: Int) = if (device.last().isDigit()) "${device}p$n" else "$device$n"

    private fun settle() { run("udevadm", "settle"); run("partprobe"); Thread.sleep(600) }

    private fun partitionsOf(device: String) = listPartitions(device)

    // ── Formato ──────────────────────────────────────────────────────────

    private fun partedFs(fs: Filesystem) = when (fs) {
        Filesystem.FAT16 -> "fat16"; Filesystem.FAT32 -> "fat32"
        Filesystem.EXFAT, Filesystem.NTFS -> "ntfs"
        Filesystem.EXT2 -> "ext2"; Filesystem.EXT3, Filesystem.EXT4 -> "ext4"
        Filesystem.F2FS -> "ext4"
    }

    private fun mkfsCommand(fs: Filesystem, part: String, label: String, cluster: Int): List<String> = when (fs) {
        Filesystem.FAT16, Filesystem.FAT32 -> buildList {
            add("mkfs.fat"); add("-F"); add(if (fs == Filesystem.FAT16) "16" else "32"); add("-n"); add(label)
            if (cluster >= 512) { add("-s"); add((cluster / 512).toString()) }
            add(part)
        }
        Filesystem.EXFAT -> buildList { add("mkfs.exfat"); add("-L"); add(label); if (cluster >= 512) { add("-c"); add(cluster.toString()) }; add(part) }
        Filesystem.NTFS -> buildList { add("mkntfs"); add("-f"); add("-L"); add(label); if (cluster >= 512) { add("-c"); add(cluster.toString()) }; add(part) }
        Filesystem.EXT2 -> listOf("mkfs.ext2", "-F", "-L", label, part)
        Filesystem.EXT3 -> listOf("mkfs.ext3", "-F", "-L", label, part)
        Filesystem.EXT4 -> listOf("mkfs.ext4", "-F", "-L", label, part)
        Filesystem.F2FS -> listOf("mkfs.f2fs", "-f", "-l", label, part)
    }

    private fun makeFs(device: String, n: Int, fs: Filesystem, label: String, cluster: Int): String? {
        val part = partitionPath(device, n)
        repeat(20) { if (File(part).exists()) return@repeat else Thread.sleep(300) }
        val r = priv(*mkfsCommand(fs, part, sanitizeLabel(label, fs), cluster).toTypedArray())
        return if (r.ok) null else "mkfs falló: ${r.text}"
    }

    override fun format(device: String, fs: Filesystem, label: String, clusterSize: Int): Flow<OperationProgress> = flow {
        guard(device)?.let { emit(progressError(it)); return@flow }
        emit(progressStep("Desmontando…", 0.05f)); unmountAll(device)
        emit(progressStep("Limpiando firmas…", 0.15f))
        priv("wipefs", "-a", device)
        val table = if (sizeOf(device) > MBR_LIMIT_BYTES) "gpt" else "msdos"
        emit(progressStep("Creando tabla $table…", 0.30f))
        var r = priv("parted", "-s", device, "mklabel", table)
        if (!r.ok) { emit(progressError("parted mklabel: ${r.text}")); return@flow }
        r = priv("parted", "-s", "-a", "optimal", "--", device, "mkpart", "primary", partedFs(fs), "1MiB", "-1MiB")
        if (!r.ok) { emit(progressError("parted mkpart: ${r.text}")); return@flow }
        emit(progressStep("Esperando a que el sistema vea la partición…", 0.5f)); settle()
        emit(progressStep("Formateando ${fs.label}…", 0.65f))
        makeFs(device, 1, fs, label, clusterSize)?.let { emit(progressError(it)); return@flow }
        run("sync")
        emit(progressDone("Formateado como ${fs.label} ✓"))
    }.flowOn(Dispatchers.IO)

    // ── dd con progreso ──────────────────────────────────────────────────

    private fun dd(input: String, output: String, total: Long, label: String): Flow<OperationProgress> = flow {
        emit(progressStep("Preparando…", 0f).copy(totalBytes = total))
        val cmd = sudo("dd", "if=$input", "of=$output", "bs=4M", "conv=fsync", "status=progress")
        val p = try { ProcessBuilder(cmd).start() } catch (e: Exception) { emit(progressError("No se pudo lanzar dd: ${e.message}")); return@flow }
        p.outputStream.close()
        val start = System.currentTimeMillis()
        val sb = StringBuilder()
        val err = p.errorStream.reader()
        var last = 0L
        while (true) {
            val c = err.read()
            if (c < 0) break
            if (c == '\r'.code || c == '\n'.code) {
                val n = Regex("^(\\d+) bytes").find(sb.toString().trim())?.groupValues?.get(1)?.toLongOrNull()
                if (n != null && n != last) {
                    last = n
                    val sec = (System.currentTimeMillis() - start) / 1000.0
                    emit(progressStep(label, if (total > 0) (n.toFloat() / total).coerceAtMost(0.99f) else 0f)
                        .copy(writtenBytes = n, totalBytes = total, speedMbs = if (sec > 0) n / sec / 1_048_576.0 else 0.0))
                }
                sb.setLength(0)
            } else sb.append(c.toChar())
        }
        val code = p.waitFor()
        if (code != 0 && !(total == 0L && sb.isEmpty())) {
            // dd sale con error si el destino se llena (p. ej. wipe del disco entero): es el final normal.
            if (!(input == "/dev/zero" && last > 0)) { emit(progressError("dd terminó con código $code")); return@flow }
        }
        emit(progressDone("$label — completado ✓").copy(writtenBytes = total, totalBytes = total))
    }.flowOn(Dispatchers.IO)

    override fun wipe(device: String): Flow<OperationProgress> = flow {
        guard(device)?.let { emit(progressError(it)); return@flow }
        unmountAll(device); priv("wipefs", "-a", device)
        emitAll(dd("/dev/zero", device, sizeOf(device), "Borrando disco"))
    }

    override fun rawWrite(imagePath: String, device: String): Flow<OperationProgress> = flow {
        guard(device)?.let { emit(progressError(it)); return@flow }
        val f = File(imagePath)
        if (!f.isFile) { emit(progressError("No se encuentra la imagen.")); return@flow }
        if (f.length() > sizeOf(device)) { emit(progressError("La imagen (${humanBytes(f.length())}) no cabe en el disco (${humanBytes(sizeOf(device))}).")); return@flow }
        unmountAll(device)
        emitAll(dd(imagePath, device, f.length(), "Escribiendo imagen"))
        settle()
    }

    override fun restore(imagePath: String, device: String) = rawWrite(imagePath, device)

    override fun backup(device: String, imagePath: String): Flow<OperationProgress> = flow {
        guard(device)?.let { emit(progressError(it)); return@flow }
        emitAll(dd(device, imagePath, sizeOf(device), "Copiando disco"))
    }

    override fun installVentoy(context: Any?, device: String, useGpt: Boolean, secureBoot: Boolean, update: Boolean) =
        failure("Ventoy aún no está integrado en la versión de escritorio. Descarga Ventoy desde ventoy.net y úsalo sobre este USB; el soporte llegará en una versión próxima.")

    override fun downloadFile(url: String, destPath: String) = JvmOps.download(url, destPath)
    override fun binToIso(binPath: String, isoPath: String) = JvmOps.binToIso(binPath, isoPath)

    // ── Particiones ──────────────────────────────────────────────────────

    private fun partedMachine(device: String) = priv("parted", "-m", "-s", device, "unit", "MB", "print")

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
        val r = priv("sgdisk", if (toGpt) "-g" else "-m", device)
        if (!r.ok) {
            val hint = if (toGpt) " Si la última partición llega hasta el final del disco, no hay sitio para la cabecera GPT: reduce esa partición ~1 MiB." else ""
            emit(progressError("sgdisk: ${r.text}.$hint")); return@flow
        }
        settle()
        emit(progressDone("Tabla convertida a ${if (toGpt) "GPT" else "MBR"} ✓"))
    }.flowOn(Dispatchers.IO)

    override fun createPartition(device: String, fs: Filesystem): Flow<OperationProgress> = flow {
        guard(device)?.let { emit(progressError(it)); return@flow }
        val parts = partitionsOf(device)
        val startMb = parts.lastOrNull()?.end?.removeSuffix("MB")?.toDoubleOrNull()?.let { "${it.toLong() + 1}MB" } ?: "1MiB"
        emit(progressStep("Creando partición…", 0.3f))
        val r = priv("parted", "-s", "-a", "optimal", "--", device, "mkpart", "primary", partedFs(fs), startMb, "-1MiB")
        if (!r.ok) { emit(progressError("parted: ${r.text}")); return@flow }
        settle()
        val n = (partitionsOf(device).maxOfOrNull { it.number } ?: 1)
        emit(progressStep("Formateando ${fs.label}…", 0.7f))
        makeFs(device, n, fs, "X-USB", 0)?.let { emit(progressError(it)); return@flow }
        emit(progressDone("Partición $n creada ✓"))
    }.flowOn(Dispatchers.IO)

    override fun deletePartition(device: String, partNum: Int): Flow<OperationProgress> = flow {
        guard(device)?.let { emit(progressError(it)); return@flow }
        unmountAll(device)
        val r = priv("parted", "-s", device, "rm", partNum.toString())
        if (!r.ok) { emit(progressError("parted: ${r.text}")); return@flow }
        settle()
        emit(progressDone("Partición $partNum eliminada ✓"))
    }.flowOn(Dispatchers.IO)

    override fun mountPartition(partition: String, mountPoint: String): Boolean {
        if (!Regex("^/dev/[A-Za-z0-9]+$").matches(partition)) return false
        File(mountPoint).mkdirs()
        return priv("mount", partition, mountPoint).ok
    }

    override fun unmountPartition(mountPoint: String) = priv("umount", mountPoint).ok

    override fun eject(device: String): Boolean {
        if (guard(device) != null) return false
        unmountAll(device); run("sync")
        run("udisksctl", "power-off", "-b", device)
        return true
    }
}
