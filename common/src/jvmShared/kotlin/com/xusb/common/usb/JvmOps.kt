package com.xusb.common.usb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/** Operaciones que solo necesitan java.io / java.net, iguales en Android y en PC. */
object JvmOps {
    const val BUFFER = 4 * 1024 * 1024
    const val SECTOR = 512

    private const val BIN_SECTOR = 2352
    private const val ISO_DATA_OFFSET = 16
    private const val ISO_DATA = 2048

    private fun mbs(bytes: Long, startMs: Long): Double {
        val sec = (System.currentTimeMillis() - startMs) / 1000.0
        return if (sec > 0) bytes / sec / 1_048_576.0 else 0.0
    }

    /** BIN modo 1 (2352 B/sector) → ISO (2048 B/sector). */
    fun binToIso(binPath: String, isoPath: String): Flow<OperationProgress> = flow {
        val bin = File(binPath)
        if (!bin.isFile) { emit(progressError("No se encuentra el archivo BIN.")); return@flow }
        val sectors = bin.length() / BIN_SECTOR
        if (sectors == 0L) { emit(progressError("BIN no válido (demasiado pequeño).")); return@flow }
        val total = sectors * ISO_DATA
        emit(progressStep("Convirtiendo BIN → ISO…", 0f).copy(totalBytes = total))

        val sector = ByteArray(BIN_SECTOR)
        val start = System.currentTimeMillis()
        var done = 0L
        try {
            RandomAccessFile(bin, "r").use { input ->
                File(isoPath).also { it.absoluteFile.parentFile?.mkdirs() }.outputStream().buffered(BUFFER).use { out ->
                    while (done < sectors) {
                        input.readFully(sector)
                        out.write(sector, ISO_DATA_OFFSET, ISO_DATA)
                        done++
                        if (done % 400 == 0L || done == sectors) {
                            val written = done * ISO_DATA
                            emit(progressStep("Convirtiendo…", done.toFloat() / sectors)
                                .copy(writtenBytes = written, totalBytes = total, speedMbs = mbs(written, start)))
                        }
                    }
                }
            }
        } catch (e: IOException) {
            emit(progressError("Error al convertir: ${e.message}")); return@flow
        }
        emit(progressDone("Conversión completada ✓").copy(writtenBytes = total, totalBytes = total))
    }.flowOn(Dispatchers.IO)

    /** Descarga con redirecciones (también http→https, que HttpURLConnection no sigue solo). */
    fun download(url: String, destPath: String): Flow<OperationProgress> = flow {
        emit(progressStep("Conectando…", 0f))
        var current = url
        var conn: HttpURLConnection? = null
        try {
            repeat(6) {
                val c = URL(current).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", "X-USB (Static Forge)")
                val code = c.responseCode
                if (code in 301..303 || code == 307 || code == 308) {
                    val loc = c.getHeaderField("Location")
                    c.disconnect()
                    if (loc.isNullOrBlank()) throw IOException("Redirección sin destino")
                    current = URL(URL(current), loc).toString()
                } else {
                    conn = c
                    return@repeat
                }
            }
            val c = conn ?: throw IOException("Demasiadas redirecciones")
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")

            val total = c.contentLengthLong
            val dest = File(destPath).also { it.absoluteFile.parentFile?.mkdirs() }
            val part = File("$destPath.part")
            var got = 0L
            val start = System.currentTimeMillis()
            c.inputStream.use { input ->
                FileOutputStream(part).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastEmit = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                        if (got - lastEmit >= 512 * 1024) {
                            lastEmit = got
                            emit(progressStep("Descargando…", if (total > 0) got.toFloat() / total else 0f)
                                .copy(writtenBytes = got, totalBytes = total, speedMbs = mbs(got, start)))
                        }
                    }
                }
            }
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) throw IOException("No se pudo guardar $destPath")
            emit(progressDone("Descarga completada ✓").copy(writtenBytes = got, totalBytes = got))
        } catch (e: IOException) {
            emit(progressError("Descarga fallida: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Escribe [file] en un dispositivo de bloques abierto como fichero (\\.\PhysicalDriveN en Windows).
     * Los dispositivos en bruto exigen escrituras alineadas a sector: el último bloque se rellena con ceros.
     */
    fun fileToDevice(file: String, device: String, label: String): Flow<OperationProgress> = flow {
        val src = File(file)
        if (!src.isFile) { emit(progressError("No se encuentra la imagen.")); return@flow }
        val total = src.length()
        emit(progressStep("Preparando…", 0f).copy(totalBytes = total))
        val buf = ByteArray(BUFFER)
        var written = 0L
        val start = System.currentTimeMillis()
        try {
            FileInputStream(src).use { input ->
                RandomAccessFile(device, "rw").use { out ->
                    while (true) {
                        val n = readFully(input, buf)
                        if (n <= 0) break
                        val padded = ((n + SECTOR - 1) / SECTOR) * SECTOR
                        if (padded > n) java.util.Arrays.fill(buf, n, padded, 0.toByte())
                        out.write(buf, 0, padded)
                        written += n
                        emit(progressStep(label, written.toFloat() / total)
                            .copy(writtenBytes = written, totalBytes = total, speedMbs = mbs(written, start)))
                        if (n < buf.size) break
                    }
                }
            }
        } catch (e: IOException) {
            emit(progressError("Error de escritura: ${e.message}")); return@flow
        }
        emit(progressDone("Escritura completada ✓").copy(writtenBytes = total, totalBytes = total))
    }.flowOn(Dispatchers.IO)

    /** Lee [totalBytes] del dispositivo y los guarda en [file]. */
    fun deviceToFile(device: String, totalBytes: Long, file: String): Flow<OperationProgress> = flow {
        if (totalBytes <= 0) { emit(progressError("No se conoce el tamaño del disco.")); return@flow }
        emit(progressStep("Iniciando copia…", 0f).copy(totalBytes = totalBytes))
        val buf = ByteArray(BUFFER)
        var done = 0L
        val start = System.currentTimeMillis()
        try {
            RandomAccessFile(device, "r").use { input ->
                File(file).also { it.absoluteFile.parentFile?.mkdirs() }.outputStream().use { out ->
                    while (done < totalBytes) {
                        val want = minOf(buf.size.toLong(), totalBytes - done).toInt()
                        val n = input.read(buf, 0, want)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        done += n
                        emit(progressStep("Copiando…", done.toFloat() / totalBytes)
                            .copy(writtenBytes = done, totalBytes = totalBytes, speedMbs = mbs(done, start)))
                    }
                }
            }
        } catch (e: IOException) {
            emit(progressError("Error de lectura: ${e.message}")); return@flow
        }
        if (done < totalBytes) emit(progressError("La copia quedó incompleta (${humanBytes(done)} de ${humanBytes(totalBytes)})."))
        else emit(progressDone("Copia completada ✓").copy(writtenBytes = done, totalBytes = totalBytes))
    }.flowOn(Dispatchers.IO)

    /** Pone a cero los primeros [bytes] del dispositivo (tabla de particiones y firmas). */
    fun zeroStart(device: String, bytes: Long) {
        val zeros = ByteArray(SECTOR * 2048) // 1 MiB
        RandomAccessFile(device, "rw").use { out ->
            var left = bytes
            while (left > 0) {
                val n = minOf(zeros.size.toLong(), left).toInt()
                out.write(zeros, 0, n)
                left -= n
            }
        }
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) break
            off += n
        }
        return off
    }
}
