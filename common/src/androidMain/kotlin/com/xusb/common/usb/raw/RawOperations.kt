package com.xusb.common.usb.raw

import android.content.Context
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.provider.OpenableColumns
import com.xusb.common.usb.OperationProgress
import com.xusb.common.usb.humanBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/*
 * Operaciones por sectores sin root: grabar una imagen, borrar, copiar y restaurar.
 * Las rutas de archivo pueden ser una ruta normal o un content:// del selector de Android.
 * Cancelar la corrutina que recoge el Flow detiene la operación entre dos bloques.
 */

private const val CHUNK = 1 shl 20 // 1 MiB: múltiplo de 512 y de 4096

class RawOps(private val context: Context) {

    /** Graba una imagen (ISO, IMG) desde el sector 0, como dd, y la verifica leyéndola de vuelta. */
    fun writeImage(device: UsbDevice, imagePath: String, verify: Boolean = true): Flow<OperationProgress> = flow {
        val total = sizeOf(imagePath)
        if (total <= 0) throw IOException("No se puede saber el tamaño de «${displayName(imagePath)}»")
        emit(OperationProgress(isRunning = true, label = "Abriendo el USB…", totalBytes = total))
        UsbBlockDevice.open(context, device).use { dev ->
            if (total > dev.sizeBytes) throw IOException("La imagen (${humanBytes(total)}) no cabe en el USB (${humanBytes(dev.sizeBytes)})")
            openIn(imagePath).use { copyToDevice(it, dev, total, if (verify) 0.5f else 1f) }
            if (verify) openIn(imagePath).use { verifyDevice(it, dev, total) }
        }
        emit(OperationProgress(isRunning = false, label = if (verify) "Grabada y verificada" else "Grabada",
            progressPct = 1f, writtenBytes = total, totalBytes = total, isComplete = true))
    }.asOperation()

    /** Escribe ceros en todo el USB. */
    fun wipe(device: UsbDevice): Flow<OperationProgress> = flow {
        emit(OperationProgress(isRunning = true, label = "Abriendo el USB…"))
        UsbBlockDevice.open(context, device).use { dev ->
            val zero = ByteBuffer.allocate(CHUNK)
            val per = (CHUNK / dev.blockSize).toLong()
            val total = dev.sizeBytes
            val start = System.currentTimeMillis()
            var lba = 0L
            while (lba < dev.blocks) {
                currentCoroutineContext().ensureActive()
                val n = minOf(per, dev.blocks - lba)
                zero.clear(); zero.limit((n * dev.blockSize).toInt())
                dev.write(lba, zero)
                lba += n
                emitProgress("Escribiendo ceros…", lba * dev.blockSize, total, start, 0f, 1f)
            }
            emit(OperationProgress(isRunning = false, label = "USB borrado", progressPct = 1f,
                writtenBytes = total, totalBytes = total, isComplete = true))
        }
    }.asOperation()

    /** Copia el USB entero a un archivo. */
    fun backup(device: UsbDevice, imagePath: String): Flow<OperationProgress> = flow {
        emit(OperationProgress(isRunning = true, label = "Abriendo el USB…"))
        UsbBlockDevice.open(context, device).use { dev ->
            val total = dev.sizeBytes
            openOut(imagePath).use { out ->
                val buf = ByteBuffer.allocate(CHUNK)
                val per = (CHUNK / dev.blockSize).toLong()
                val start = System.currentTimeMillis()
                var lba = 0L
                while (lba < dev.blocks) {
                    currentCoroutineContext().ensureActive()
                    val n = minOf(per, dev.blocks - lba)
                    val len = (n * dev.blockSize).toInt()
                    buf.clear(); buf.limit(len)
                    dev.read(lba, buf)
                    out.write(buf.array(), 0, len)
                    lba += n
                    emitProgress("Copiando el USB…", lba * dev.blockSize, total, start, 0f, 1f)
                }
                out.flush()
            }
            emit(OperationProgress(isRunning = false, label = "Copia guardada", progressPct = 1f,
                writtenBytes = total, totalBytes = total, isComplete = true))
        }
    }.asOperation()

    /** Tabla MBR con una partición FAT32 en todo el USB, sin root. */
    fun formatFat32(device: UsbDevice, label: String, clusterBytes: Int): Flow<OperationProgress> = channelFlow {
        send(OperationProgress(isRunning = true, label = "Abriendo el USB…"))
        UsbBlockDevice.open(context, device).use { dev ->
            var last = -1
            Fat32Formatter.format(dev, label, clusterBytes) { f ->
                val pct = (f * 100).toInt()
                if (pct != last) { last = pct; trySend(OperationProgress(isRunning = true, label = "Formateando FAT32…", progressPct = f)) }
            }
        }
        send(OperationProgress(isRunning = false, label = "Formateado en FAT32", progressPct = 1f, isComplete = true))
    }.asOperation()

    /** Tabla de particiones leída del propio USB, sin root. */
    fun readTable(device: UsbDevice): PartitionReader.Table =
        UsbBlockDevice.open(context, device).use { PartitionReader.read(it) }

    // ── Archivos: ruta normal o content:// ───────────────────────────────

    private fun isContent(path: String) = path.startsWith("content://")

    private fun openIn(path: String): InputStream =
        if (isContent(path)) context.contentResolver.openInputStream(Uri.parse(path)) ?: throw IOException("No se puede abrir la imagen")
        else FileInputStream(File(path))

    private fun openOut(path: String): OutputStream =
        if (isContent(path)) context.contentResolver.openOutputStream(Uri.parse(path), "w") ?: throw IOException("No se puede crear el archivo")
        else File(path).also { it.parentFile?.mkdirs() }.let { FileOutputStream(it) }

    private fun sizeOf(path: String): Long {
        if (!isContent(path)) return File(path).takeIf { it.isFile }?.length() ?: -1L
        val uri = Uri.parse(path)
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
        }
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    }

    private fun displayName(path: String): String {
        if (!isContent(path)) return File(path).name
        context.contentResolver.query(Uri.parse(path), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { return it }
        }
        return path.substringAfterLast('/')
    }
}

// ── Internos ──────────────────────────────────────────────────────────────

private suspend fun FlowCollector<OperationProgress>.copyToDevice(input: InputStream, dev: UsbBlockDevice, total: Long, share: Float) {
    val buf = ByteBuffer.allocate(CHUNK)
    val arr = buf.array()
    var lba = 0L
    var written = 0L
    val start = System.currentTimeMillis()
    while (written < total) {
        currentCoroutineContext().ensureActive()
        val filled = readFully(input, arr)
        if (filled <= 0) break
        // El último trozo se completa con ceros hasta un bloque entero.
        val aligned = ((filled + dev.blockSize - 1) / dev.blockSize) * dev.blockSize
        if (aligned > filled) arr.fill(0, filled, aligned)
        buf.clear(); buf.limit(aligned)
        dev.write(lba, buf)
        lba += aligned / dev.blockSize
        written += filled
        emitProgress("Grabando…", written, total, start, 0f, share)
    }
    if (written < total) throw IOException("La imagen terminó antes de lo esperado (${humanBytes(written)} de ${humanBytes(total)})")
}

private suspend fun FlowCollector<OperationProgress>.verifyDevice(input: InputStream, dev: UsbBlockDevice, total: Long) {
    val src = ByteArray(CHUNK)
    val buf = ByteBuffer.allocate(CHUNK)
    var lba = 0L
    var checked = 0L
    val start = System.currentTimeMillis()
    while (checked < total) {
        currentCoroutineContext().ensureActive()
        val filled = readFully(input, src)
        if (filled <= 0) break
        val aligned = ((filled + dev.blockSize - 1) / dev.blockSize) * dev.blockSize
        buf.clear(); buf.limit(aligned)
        dev.read(lba, buf)
        val got = buf.array()
        for (i in 0 until filled) {
            if (got[i] != src[i]) throw IOException("La verificación ha fallado en el byte ${checked + i}: el USB no guarda bien los datos")
        }
        lba += aligned / dev.blockSize
        checked += filled
        emitProgress("Verificando…", checked, total, start, 0.5f, 0.5f)
    }
}

private suspend fun FlowCollector<OperationProgress>.emitProgress(label: String, done: Long, total: Long, startMs: Long, base: Float, share: Float) {
    val secs = (System.currentTimeMillis() - startMs) / 1000.0
    emit(OperationProgress(
        isRunning = true, label = label,
        progressPct = base + share * (done.toFloat() / total.coerceAtLeast(1)),
        writtenBytes = done, totalBytes = total,
        speedMbs = if (secs > 0) done / secs / 1_048_576.0 else 0.0,
    ))
}

private fun readFully(input: InputStream, arr: ByteArray): Int {
    var off = 0
    while (off < arr.size) {
        val r = input.read(arr, off, arr.size - off)
        if (r < 0) break
        off += r
    }
    return off
}

/** Ejecuta en IO y convierte los errores en un estado de error en lugar de romper la interfaz. */
private fun Flow<OperationProgress>.asOperation(): Flow<OperationProgress> = flow {
    try {
        this@asOperation.collect { emit(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: SecurityException) {
        emit(OperationProgress(error = "Android no ha dado permiso para usar este USB. Desconéctalo, vuelve a conectarlo y acepta el aviso."))
    } catch (e: Exception) {
        emit(OperationProgress(error = e.message ?: e.javaClass.simpleName))
    }
}.flowOn(Dispatchers.IO)
