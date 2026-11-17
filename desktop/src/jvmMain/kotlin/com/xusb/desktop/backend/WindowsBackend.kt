package com.xusb.desktop.backend

import com.xusb.common.usb.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Windows: PowerShell (Get-Disk / Format-Volume / Clear-Disk) + acceso directo a \\.\PhysicalDriveN.
 * Seguridad: solo discos con BusType USB que no sean de arranque ni de sistema. Las pruebas pueden
 * añadir números de disco (VHD) con -Dxusb.testDevices=7. Escribir en discos exige administrador.
 */
class WindowsBackend : UsbBackend {
    override val platformName = "Windows"

    private fun ps(script: String, timeoutSec: Long = 600): Res =
        run("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script, timeoutSec = timeoutSec)

    private val admin: Boolean by lazy {
        ps("([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole('Administrator')", 30)
            .out.trim().equals("True", ignoreCase = true)
    }
    override fun isPrivileged() = admin

    private fun diskNumber(device: String): Int? =
        Regex("""^\\\\\.\\PhysicalDrive(\d+)$""").matchEntire(device)?.groupValues?.get(1)?.toIntOrNull()

    private fun physical(n: Int) = "\\\\.\\PhysicalDrive$n"

    override fun listDrives(): List<UsbDriveInfo> {
        val r = ps("Get-Disk | Where-Object { \$_.BusType -eq 'USB' -and -not \$_.IsBoot -and -not \$_.IsSystem } | ForEach-Object { \"\$(\$_.Number)|\$(\$_.Size)|\$(\$_.FriendlyName)\" }", 60)
        if (!r.ok) return emptyList()
        return r.out.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { l ->
            val f = l.split('|')
            val n = f.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            UsbDriveInfo(f.getOrElse(2) { "USB Drive" }.ifBlank { "USB Drive" }, physical(n), f.getOrNull(1)?.toLongOrNull() ?: 0L, true, "USB")
        }
    }

    /** Devuelve (número de disco, error). Comprueba USB / no sistema / no arranque contra Get-Disk. */
    private fun guard(device: String, needAdmin: Boolean = true): Pair<Int?, String?> {
        val n = diskNumber(device) ?: return null to "Ruta de disco no válida: $device"
        val r = ps("\$d = Get-Disk -Number $n -ErrorAction SilentlyContinue; if (-not \$d) { 'NONE' } else { \"\$(\$d.BusType)|\$(\$d.IsBoot)|\$(\$d.IsSystem)\" }", 60)
        val f = r.out.trim().split('|')
        if (r.out.trim() == "NONE" || f.size < 3) return null to "No existe el disco $n."
        val test = n.toString() in testDevices()
        if (f[0] != "USB" && !test) return null to "El disco $n no es USB (${f[0]}): me niego a tocarlo."
        if (f[1].equals("True", true) || f[2].equals("True", true)) return null to "El disco $n es de arranque/sistema: me niego a tocarlo."
        if (needAdmin && !admin) return null to "Hace falta ejecutar X-USB como administrador para modificar discos."
        return n to null
    }

    private fun sizeOf(n: Int) = ps("(Get-Disk -Number $n).Size", 30).out.trim().toLongOrNull() ?: 0L

    private fun q(s: String) = "'" + s.replace("'", "''") + "'"

    private fun fsName(fs: Filesystem): String? = when (fs) {
        Filesystem.FAT32 -> "FAT32"; Filesystem.EXFAT -> "exFAT"; Filesystem.NTFS -> "NTFS"
        Filesystem.FAT16 -> "FAT"; else -> null
    }

    private fun unsupported(fs: Filesystem) =
        "${fs.label} no se puede crear desde Windows. Usa FAT32, exFAT o NTFS (o la versión Linux de X-USB)."

    private fun formatScript(n: Int, fs: Filesystem, label: String, cluster: Int, style: String): String {
        val name = fsName(fs)!!
        val alloc = if (cluster >= 512) " -AllocationUnitSize $cluster" else ""
        return "\$ErrorActionPreference='Stop'; " +
            "Clear-Disk -Number $n -RemoveData -RemoveOEM -Confirm:\$false -ErrorAction SilentlyContinue; " +
            "Initialize-Disk -Number $n -PartitionStyle $style; " +
            "\$p = New-Partition -DiskNumber $n -UseMaximumSize -AssignDriveLetter; " +
            "Format-Volume -Partition \$p -FileSystem $name -NewFileSystemLabel ${q(sanitizeLabel(label, fs))}$alloc -Confirm:\$false | Out-Null"
    }

    override fun format(device: String, fs: Filesystem, label: String, clusterSize: Int): Flow<OperationProgress> = flow {
        val (n, err) = guard(device); if (n == null) { emit(progressError(err!!)); return@flow }
        if (fsName(fs) == null) { emit(progressError(unsupported(fs))); return@flow }
        val size = sizeOf(n)
        if (fs == Filesystem.FAT32 && size > 32L * 1_073_741_824L) {
            emit(progressError("Windows no formatea FAT32 en discos de más de 32 GB. Usa exFAT/NTFS o la versión Linux.")); return@flow
        }
        emit(progressStep("Formateando ${fs.label}…", 0.3f))
        val r = ps(formatScript(n, fs, label, clusterSize, if (size > MBR_LIMIT_BYTES) "GPT" else "MBR"))
        if (!r.ok) { emit(progressError("Formato fallido: ${r.text}")); return@flow }
        emit(progressDone("Formateado como ${fs.label} ✓"))
    }.flowOn(Dispatchers.IO)

    override fun wipe(device: String): Flow<OperationProgress> = flow {
        val (n, err) = guard(device); if (n == null) { emit(progressError(err!!)); return@flow }
        emit(progressStep("Eliminando particiones…", 0.3f))
        val r = ps("Clear-Disk -Number $n -RemoveData -RemoveOEM -Confirm:\$false")
        if (!r.ok) { emit(progressError("Clear-Disk: ${r.text}")); return@flow }
        emit(progressStep("Poniendo a cero el inicio del disco…", 0.7f))
        try { JvmOps.zeroStart(physical(n), 16L * 1024 * 1024) } catch (e: Exception) { emit(progressError("No se pudo escribir en el disco: ${e.message}")); return@flow }
        emit(progressDone("Borrado rápido completado (tablas y firmas) ✓"))
    }.flowOn(Dispatchers.IO)

    override fun rawWrite(imagePath: String, device: String): Flow<OperationProgress> = flow {
        val (n, err) = guard(device); if (n == null) { emit(progressError(err!!)); return@flow }
        val size = sizeOf(n)
        val len = java.io.File(imagePath).length()
        if (len > size) { emit(progressError("La imagen (${humanBytes(len)}) no cabe en el disco (${humanBytes(size)}).")); return@flow }
        emit(progressStep("Liberando el disco…", 0.02f))
        ps("Clear-Disk -Number $n -RemoveData -RemoveOEM -Confirm:\$false -ErrorAction SilentlyContinue")
        emitAll(JvmOps.fileToDevice(imagePath, physical(n), "Escribiendo imagen"))
        ps("Update-Disk -Number $n -ErrorAction SilentlyContinue", 60)
    }.flowOn(Dispatchers.IO)

    override fun restore(imagePath: String, device: String) = rawWrite(imagePath, device)

    override fun backup(device: String, imagePath: String): Flow<OperationProgress> = flow {
        val (n, err) = guard(device); if (n == null) { emit(progressError(err!!)); return@flow }
        emitAll(JvmOps.deviceToFile(physical(n), sizeOf(n), imagePath))
    }.flowOn(Dispatchers.IO)

    override fun installVentoy(context: Any?, device: String, useGpt: Boolean, secureBoot: Boolean, update: Boolean) =
        failure("Ventoy aún no está integrado en la versión de escritorio. Descarga Ventoy desde ventoy.net y úsalo sobre este USB; el soporte llegará en una versión próxima.")

    override fun downloadFile(url: String, destPath: String) = JvmOps.download(url, destPath)
    override fun binToIso(binPath: String, isoPath: String) = JvmOps.binToIso(binPath, isoPath)

    // ── Particiones (partitionPath = "<disco>#<n>", Windows no tiene /dev/sdb1) ──

    override fun partitionPath(device: String, n: Int) = "$device#$n"

    override fun listPartitions(device: String): List<PartitionInfo> {
        val (n, err) = guard(device, needAdmin = false); if (n == null || err != null) return emptyList()
        val r = ps("Get-Partition -DiskNumber $n | ForEach-Object { \$v = \$_ | Get-Volume -ErrorAction SilentlyContinue; \"\$(\$_.PartitionNumber)|\$(\$_.Offset)|\$(\$_.Size)|\$(\$_.Type)|\$(\$v.FileSystem)\" }", 60)
        return r.out.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { l ->
            val f = l.split('|')
            val num = f.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val off = f.getOrNull(1)?.toLongOrNull() ?: 0L
            val sz = f.getOrNull(2)?.toLongOrNull() ?: 0L
            PartitionInfo(num, humanBytes(off), humanBytes(off + sz), humanBytes(sz), f.getOrElse(4) { "" }, f.getOrElse(3) { "" })
        }
    }

    override fun partitionTableType(device: String): String {
        val (n, err) = guard(device, needAdmin = false); if (n == null || err != null) return "Unknown"
        return when (ps("(Get-Disk -Number $n).PartitionStyle", 30).out.trim().uppercase()) { "MBR" -> "MBR"; "GPT" -> "GPT"; "RAW" -> "Unknown"; else -> "Other" }
    }

    override fun convertPartitionTable(device: String, toGpt: Boolean) =
        failure("Windows no convierte MBR↔GPT sin borrar el disco. Usa «Borrar» y luego «Formatear», o la versión Linux de X-USB (sgdisk).")

    override fun createPartition(device: String, fs: Filesystem): Flow<OperationProgress> = flow {
        val (n, err) = guard(device); if (n == null) { emit(progressError(err!!)); return@flow }
        val name = fsName(fs); if (name == null) { emit(progressError(unsupported(fs))); return@flow }
        emit(progressStep("Creando partición…", 0.4f))
        val r = ps("\$ErrorActionPreference='Stop'; \$p = New-Partition -DiskNumber $n -UseMaximumSize -AssignDriveLetter; Format-Volume -Partition \$p -FileSystem $name -NewFileSystemLabel 'X-USB' -Confirm:\$false | Out-Null")
        if (!r.ok) { emit(progressError("No se pudo crear: ${r.text}")); return@flow }
        emit(progressDone("Partición creada ✓"))
    }.flowOn(Dispatchers.IO)

    override fun deletePartition(device: String, partNum: Int): Flow<OperationProgress> = flow {
        val (n, err) = guard(device); if (n == null) { emit(progressError(err!!)); return@flow }
        val r = ps("Remove-Partition -DiskNumber $n -PartitionNumber $partNum -Confirm:\$false")
        if (!r.ok) { emit(progressError("No se pudo eliminar: ${r.text}")); return@flow }
        emit(progressDone("Partición $partNum eliminada ✓"))
    }.flowOn(Dispatchers.IO)

    override fun mountPartition(partition: String, mountPoint: String): Boolean {
        val m = Regex("""^(.+)#(\d+)$""").matchEntire(partition) ?: return false
        val (n, err) = guard(m.groupValues[1]); if (n == null || err != null) return false
        java.io.File(mountPoint).mkdirs()
        return ps("Add-PartitionAccessPath -DiskNumber $n -PartitionNumber ${m.groupValues[2]} -AccessPath ${q(mountPoint)}", 60).ok
    }

    override fun unmountPartition(mountPoint: String): Boolean = ps(
        "\$mp = ${q(mountPoint)}; \$p = Get-Partition | Where-Object { \$_.AccessPaths -contains (\$mp + '\\') -or \$_.AccessPaths -contains \$mp } | Select-Object -First 1; " +
            "if (-not \$p) { exit 1 }; Remove-PartitionAccessPath -DiskNumber \$p.DiskNumber -PartitionNumber \$p.PartitionNumber -AccessPath \$mp", 60
    ).ok

    override fun eject(device: String): Boolean {
        val (n, err) = guard(device); if (n == null || err != null) return false
        return ps("Set-Disk -Number $n -IsOffline \$true", 60).ok
    }
}
