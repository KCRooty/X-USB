package com.xusb.usb

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

// ── Root Shell Init ───────────────────────────────────────────────────────
object RootInit {
    fun init() {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }
    fun isAvailable(): Boolean = Shell.getShell().isRoot
}

// ── Format Filesystem ─────────────────────────────────────────────────────
enum class Filesystem(val cmd: String, val label: String) {
    FAT16("mkfs.fat -F 16", "FAT16"),
    FAT32("mkfs.fat -F 32", "FAT32"),
    EXFAT("mkexfatfs", "exFAT"),
    NTFS("mkfs.ntfs -f", "NTFS"),
    EXT2("mkfs.ext2", "Ext2"),
    EXT3("mkfs.ext3", "Ext3"),
    EXT4("mkfs.ext4", "Ext4"),
    F2FS("mkfs.f2fs", "F2FS"),
}

fun formatUsb(device: String, fs: Filesystem, label: String = "X-USB", clusterSize: Int = 0): Flow<OperationProgress> = flow {
    emit(OperationProgress(isRunning = true, label = "Formatting as ${fs.label}…", progressPct = 0.1f))
    val clusterFlag = buildClusterFlag(fs, clusterSize)
    // exFAT uses -n for label (mkexfatfs), all others use -L
    val cmd = when (fs) {
        Filesystem.EXFAT -> "mkexfatfs -n \"$label\"$clusterFlag $device"
        else             -> "${fs.cmd} -L \"$label\"$clusterFlag $device"
    }
    val result = Shell.cmd(cmd).exec()
    if (result.isSuccess) {
        emit(OperationProgress(isRunning = false, label = "Format complete ✓", progressPct = 1f, isComplete = true))
    } else {
        emit(OperationProgress(isRunning = false, error = result.out.joinToString("\n")))
    }
}.flowOn(Dispatchers.IO)

private fun buildClusterFlag(fs: Filesystem, clusterBytes: Int): String {
    if (clusterBytes == 0) return ""
    return when (fs) {
        Filesystem.FAT16, Filesystem.FAT32                 -> " -s ${clusterBytes / 512}"
        Filesystem.EXFAT, Filesystem.NTFS                  -> " -c $clusterBytes"
        Filesystem.EXT2, Filesystem.EXT3, Filesystem.EXT4 -> " -b $clusterBytes"
        Filesystem.F2FS                                    -> ""
    }
}

// ── Wipe USB ─────────────────────────────────────────────────────────────
fun wipeUsb(device: String): Flow<OperationProgress> = flow {
    emit(OperationProgress(isRunning = true, label = "Wiping all partitions…", progressPct = 0.05f))
    // Zero out the first 100 MB (partition table + superblocks)
    Shell.cmd("dd if=/dev/zero of=$device bs=1M count=100 status=progress").exec()
    emit(OperationProgress(isRunning = true, label = "Rebuilding partition table…", progressPct = 0.6f))
    Shell.cmd("parted $device --script mklabel msdos").exec()
    Shell.cmd("parted $device --script mkpart primary fat32 1MiB 100%").exec()
    emit(OperationProgress(isRunning = true, label = "Formatting FAT32…", progressPct = 0.85f))
    val fmt = Shell.cmd("mkfs.fat -F 32 -L X-USB ${device}1").exec()
    if (fmt.isSuccess) {
        emit(OperationProgress(isRunning = false, label = "Wipe complete ✓", progressPct = 1f, isComplete = true))
    } else {
        emit(OperationProgress(isRunning = false, error = fmt.out.joinToString("\n")))
    }
}.flowOn(Dispatchers.IO)

// ── Raw Write (dd-style) ──────────────────────────────────────────────────
fun rawWrite(isoPath: String, device: String): Flow<OperationProgress> = flow {
    val file = File(isoPath)
    val total = file.length()
    emit(OperationProgress(isRunning = true, label = "Preparing…", progressPct = 0f, totalBytes = total))

    val bufSize = 4 * 1024 * 1024 // 4 MB buffer
    val buf = ByteArray(bufSize)
    var written = 0L
    val startMs = System.currentTimeMillis()

    FileInputStream(file).use { input ->
        RandomAccessFile(device, "rw").use { out ->
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                out.write(buf, 0, read)
                written += read
                val elapsedSec = (System.currentTimeMillis() - startMs) / 1000.0
                val speed = if (elapsedSec > 0) written / elapsedSec / 1_048_576.0 else 0.0
                emit(OperationProgress(
                    isRunning    = true,
                    label        = "Writing image…",
                    progressPct  = written.toFloat() / total,
                    writtenBytes = written,
                    totalBytes   = total,
                    speedMbs     = speed
                ))
            }
        }
    }
    emit(OperationProgress(isRunning = false, label = "Write complete ✓", progressPct = 1f,
        writtenBytes = total, totalBytes = total, isComplete = true))
}.flowOn(Dispatchers.IO)

// ── Ventoy Install ────────────────────────────────────────────────────────
fun installVentoy(
    context: Context,
    device: String,
    useGpt: Boolean = false,
    secureBoot: Boolean = false,
    update: Boolean = false,
): Flow<OperationProgress> = flow {
    emit(OperationProgress(isRunning = true, label = "Extracting Ventoy…", progressPct = 0.05f))
    val ventoyDir = File(context.filesDir, "ventoy")
    if (!ventoyDir.exists()) {
        context.assets.list("ventoy")?.forEach { asset ->
            val src = context.assets.open("ventoy/$asset")
            val dst = File(ventoyDir, asset).also { it.parentFile?.mkdirs() }
            dst.outputStream().use { src.copyTo(it) }
            Shell.cmd("chmod +x ${dst.absolutePath}").exec()
        }
    }
    emit(OperationProgress(isRunning = true, label = "Installing Ventoy…", progressPct = 0.3f))
    val script = File(ventoyDir, "Ventoy2Disk.sh").absolutePath
    val flags = buildString {
        append(if (update) "-u" else "-i")
        if (useGpt) append(" -g")
        if (secureBoot) append(" -s")
    }
    val result = Shell.cmd("$script $flags $device").exec()
    if (result.isSuccess) {
        emit(OperationProgress(isRunning = false, label = "Ventoy installed ✓", progressPct = 1f, isComplete = true))
    } else {
        emit(OperationProgress(isRunning = false, error = result.out.joinToString("\n")))
    }
}.flowOn(Dispatchers.IO)

// ── Backup USB ────────────────────────────────────────────────────────────
fun backupUsb(device: String, imagePath: String): Flow<OperationProgress> = flow {
    val total = try {
        Shell.cmd("blockdev --getsize64 $device").exec().out[0].toLong()
    } catch (e: Exception) { -1L }

    emit(OperationProgress(isRunning = true, label = "Starting backup…", progressPct = 0f, totalBytes = total))

    // Use dd with status=progress and capture stderr to parse progress if possible,
    // or just run it and emit periodic updates. For simplicity here:
    val result = Shell.cmd("dd if=$device of=\"$imagePath\" bs=4M conv=fsync").exec()

    if (result.isSuccess) {
        emit(OperationProgress(isRunning = false, label = "Backup complete ✓", progressPct = 1f, isComplete = true))
    } else {
        emit(OperationProgress(isRunning = false, error = result.out.joinToString("\n")))
    }
}.flowOn(Dispatchers.IO)

// ── Restore USB ───────────────────────────────────────────────────────────
fun restoreUsb(imagePath: String, device: String): Flow<OperationProgress> = flow {
    val total = File(imagePath).length()
    emit(OperationProgress(isRunning = true, label = "Starting restore…", progressPct = 0f, totalBytes = total))

    val result = Shell.cmd("dd if=\"$imagePath\" of=$device bs=4M conv=fsync").exec()

    if (result.isSuccess) {
        emit(OperationProgress(isRunning = false, label = "Restore complete ✓", progressPct = 1f, isComplete = true))
    } else {
        emit(OperationProgress(isRunning = false, error = result.out.joinToString("\n")))
    }
}.flowOn(Dispatchers.IO)

// ── Partition Mounting ────────────────────────────────────────────────────
fun mountPartition(partition: String, mountPoint: String): Boolean {
    File(mountPoint).mkdirs()
    val result = Shell.cmd("mount $partition $mountPoint").exec()
    return result.isSuccess
}

fun unmountPartition(mountPoint: String): Boolean {
    val result = Shell.cmd("umount $mountPoint").exec()
    return result.isSuccess
}

// ── Partition Operations ──────────────────────────────────────────────────
data class PartitionInfo(
    val number: Int,
    val start: String,
    val end: String,
    val size: String,
    val filesystem: String,
    val flags: String
)

fun listPartitions(device: String): List<PartitionInfo> {
    val result = Shell.cmd("parted $device --script print").exec()
    val partitions = mutableListOf<PartitionInfo>()
    result.out.forEach { line ->
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size >= 5 && parts[0].toIntOrNull() != null) {
            partitions.add(PartitionInfo(
                number     = parts[0].toInt(),
                start      = parts[1],
                end        = parts[2],
                size       = parts[3],
                filesystem = parts.getOrElse(4) { "unknown" },
                flags      = parts.getOrElse(5) { "" }
            ))
        }
    }
    return partitions
}

// ── Partition Table Type ──────────────────────────────────────────────────
fun getPartitionTableType(device: String): String {
    return try {
        val r = Shell.cmd("parted $device --script print 2>/dev/null").exec()
        val line = r.out.find { it.contains("Partition Table", ignoreCase = true) } ?: return "Unknown"
        when {
            line.contains("gpt",   ignoreCase = true) -> "GPT"
            line.contains("msdos", ignoreCase = true) -> "MBR"
            else -> "Unknown"
        }
    } catch (_: Exception) { "Unknown" }
}

// ── Convert Partition Table MBR↔GPT ──────────────────────────────────────
fun convertPartitionTable(device: String, toGpt: Boolean): Flow<OperationProgress> = flow {
    emit(OperationProgress(isRunning = true,
        label = if (toGpt) "Converting to GPT…" else "Converting to MBR…", progressPct = 0.2f))
    val cmd = if (toGpt) "sgdisk -g $device" else "sgdisk -m $device"
    val result = Shell.cmd(cmd).exec()
    if (result.isSuccess) {
        emit(OperationProgress(isRunning = false,
            label = if (toGpt) "Converted to GPT ✓" else "Converted to MBR ✓",
            progressPct = 1f, isComplete = true))
    } else {
        emit(OperationProgress(isRunning = false, error = result.out.joinToString("\n")))
    }
}.flowOn(Dispatchers.IO)

// ── Create Partition (uses first available free region) ───────────────────
fun createPartition(device: String, fs: Filesystem): Flow<OperationProgress> = flow {
    emit(OperationProgress(isRunning = true, label = "Locating free space…", progressPct = 0.1f))
    val printOut = Shell.cmd("parted -s $device unit MiB print free 2>/dev/null").exec().out
    var startMiB = "1MiB"
    var endMiB   = "100%"
    printOut.forEach { line ->
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size >= 4 && line.contains("Free Space", ignoreCase = true)) {
            startMiB = parts[0]; endMiB = parts[1]
        }
    }
    emit(OperationProgress(isRunning = true, label = "Creating partition…", progressPct = 0.3f))
    val mkpart = Shell.cmd("parted $device --script mkpart primary ${fs.label.lowercase()} $startMiB $endMiB").exec()
    if (!mkpart.isSuccess) {
        emit(OperationProgress(isRunning = false, error = mkpart.out.joinToString("\n"))); return@flow
    }
    emit(OperationProgress(isRunning = true, label = "Formatting…", progressPct = 0.6f))
    val partNum = Shell.cmd("parted -s $device print 2>/dev/null").exec()
        .out.mapNotNull { it.trim().split("\\s+".toRegex()).firstOrNull()?.toIntOrNull() }
        .maxOrNull() ?: 1
    val fmt = Shell.cmd("${fs.cmd} -L X-USB ${device}${partNum}").exec()
    if (fmt.isSuccess)
        emit(OperationProgress(isRunning = false, label = "Partition created ✓", progressPct = 1f, isComplete = true))
    else
        emit(OperationProgress(isRunning = false, error = fmt.out.joinToString("\n")))
}.flowOn(Dispatchers.IO)

// ── Delete Partition ──────────────────────────────────────────────────────
fun deletePartition(device: String, partNum: Int): Flow<OperationProgress> = flow {
    emit(OperationProgress(isRunning = true, label = "Deleting partition $partNum…", progressPct = 0.3f))
    val result = Shell.cmd("parted $device --script rm $partNum").exec()
    if (result.isSuccess)
        emit(OperationProgress(isRunning = false, label = "Deleted ✓", progressPct = 1f, isComplete = true))
    else
        emit(OperationProgress(isRunning = false, error = result.out.joinToString("\n")))
}.flowOn(Dispatchers.IO)

// ── BIN → ISO (Mode 1: 2352 → 2048 bytes/sector) ─────────────────────────
private const val BIN_SECTOR_SIZE  = 2352
private const val ISO_DATA_OFFSET  = 16
private const val ISO_DATA_SIZE    = 2048

fun convertBinToIso(binPath: String, isoPath: String): Flow<OperationProgress> = flow {
    val binFile = File(binPath)
    if (!binFile.exists()) { emit(OperationProgress(isRunning = false, error = "BIN file not found")); return@flow }
    val totalSectors = binFile.length() / BIN_SECTOR_SIZE
    if (totalSectors == 0L) { emit(OperationProgress(isRunning = false, error = "Invalid BIN (too small)")); return@flow }

    emit(OperationProgress(isRunning = true, label = "Converting BIN → ISO…",
        progressPct = 0f, totalBytes = totalSectors * ISO_DATA_SIZE))

    val sector   = ByteArray(BIN_SECTOR_SIZE)
    var done     = 0L
    val startMs  = System.currentTimeMillis()

    RandomAccessFile(binFile, "r").use { bin ->
        File(isoPath).also { it.parentFile?.mkdirs() }.outputStream().use { out ->
            repeat(totalSectors.toInt()) {
                bin.readFully(sector)
                out.write(sector, ISO_DATA_OFFSET, ISO_DATA_SIZE)
                done++
                if (done % 200 == 0L || done == totalSectors) {
                    val written  = done * ISO_DATA_SIZE
                    val elapsed  = (System.currentTimeMillis() - startMs) / 1000.0
                    val speed    = if (elapsed > 0) written / elapsed / 1_048_576.0 else 0.0
                    emit(OperationProgress(isRunning = true, label = "Converting…",
                        progressPct = done.toFloat() / totalSectors,
                        writtenBytes = written, totalBytes = totalSectors * ISO_DATA_SIZE, speedMbs = speed))
                }
            }
        }
    }
    val total = totalSectors * ISO_DATA_SIZE
    emit(OperationProgress(isRunning = false, label = "Conversion complete ✓",
        progressPct = 1f, writtenBytes = total, totalBytes = total, isComplete = true))
}.flowOn(Dispatchers.IO)

// ── ISO Downloader ────────────────────────────────────────────────────────
fun downloadIsoToUsb(url: String, destPath: String): Flow<OperationProgress> = flow {
    emit(OperationProgress(isRunning = true, label = "Connecting…", progressPct = 0f))
    
    val client = okhttp3.OkHttpClient()
    val request = okhttp3.Request.Builder().url(url).build()
    
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            emit(OperationProgress(isRunning = false, error = "HTTP Error: ${response.code}"))
            return@use
        }
        
        val total = response.body?.contentLength() ?: -1L
        val destFile = File(destPath).also { it.parentFile?.mkdirs() }
        var downloaded = 0L
        val startMs = System.currentTimeMillis()
        
        response.body?.byteStream()?.use { input ->
            destFile.outputStream().use { out ->
                val buf = ByteArray(16384)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    downloaded += read
                    val elapsed = (System.currentTimeMillis() - startMs) / 1000.0
                    val speed = if (elapsed > 0) downloaded / elapsed / 1_048_576.0 else 0.0
                    emit(OperationProgress(
                        isRunning    = true,
                        label        = "Downloading…",
                        progressPct  = if (total > 0) downloaded.toFloat() / total else 0f,
                        writtenBytes = downloaded,
                        totalBytes   = total,
                        speedMbs     = speed
                    ))
                }
            }
        }
    }
    emit(OperationProgress(isRunning = false, label = "Download complete ✓", progressPct = 1f, isComplete = true))
}.flowOn(Dispatchers.IO)
