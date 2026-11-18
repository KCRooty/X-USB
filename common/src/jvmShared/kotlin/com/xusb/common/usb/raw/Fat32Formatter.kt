package com.xusb.common.usb.raw

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/**
 * Acceso por bloques a un disco. En Android lo implementa el USB por USB Host (sin root);
 * en las pruebas, un archivo de imagen.
 */
interface BlockIo {
    val blockSize: Int
    val blocks: Long
    fun read(lba: Long, buffer: ByteBuffer)
    fun write(lba: Long, buffer: ByteBuffer)
}

/**
 * Deja el disco listo para usar en cualquier sistema: una tabla MBR con una sola partición FAT32
 * (tipo 0x0C) que empieza en 1 MiB y ocupa el resto del disco, formateada según la especificación
 * de Microsoft (fatgen103). Es lo que hace Windows al formatear un pendrive.
 *
 * Todo se escribe con bloques del propio disco: no hace falta root ni mkfs.
 */
object Fat32Formatter {

    private const val RESERVED_SECTORS = 32
    private const val NUM_FATS = 2
    private const val MBR_LIMIT_SECTORS = 0xFFFFFFFFL

    class Plan(
        val partStart: Long,
        val partSectors: Long,
        val sectorsPerCluster: Int,
        val fatSectors: Long,
        val clusters: Long,
    )

    /** Calcula la geometría. [clusterBytes] = 0 elige el tamaño que usaría Windows. */
    fun plan(io: BlockIo, clusterBytes: Int = 0): Plan {
        val bps = io.blockSize
        if (bps !in listOf(512, 1024, 2048, 4096)) throw IOException("Tamaño de sector no soportado: $bps")
        val partStart = (1L shl 20) / bps               // 1 MiB, alineado para memorias flash
        val partSectors = io.blocks - partStart
        if (io.blocks > MBR_LIMIT_SECTORS) throw IOException("El USB es demasiado grande para una tabla MBR (más de 2 TiB).")
        val volBytes = partSectors * bps
        val defaultCluster = when {
            volBytes <= 8L shl 30 -> 4096
            volBytes <= 16L shl 30 -> 8192
            volBytes <= 32L shl 30 -> 16384
            else -> 32768
        }
        val cb = if (clusterBytes > 0) clusterBytes else defaultCluster
        if (cb % bps != 0 || cb / bps !in listOf(1, 2, 4, 8, 16, 32, 64, 128) || cb > 65536)
            throw IOException("Tamaño de clúster no válido para FAT32: $cb bytes")
        val spc = cb / bps

        // fatgen103: tamaño de cada FAT (entradas de 4 bytes).
        val tmp1 = partSectors - RESERVED_SECTORS
        val tmp2 = ((bps / 2L) * spc + NUM_FATS) / 2
        val fatSectors = (tmp1 + tmp2 - 1) / tmp2
        val dataSectors = partSectors - RESERVED_SECTORS - NUM_FATS * fatSectors
        val clusters = dataSectors / spc
        if (clusters < 65525) throw IOException("El USB es demasiado pequeño para FAT32 con clústeres de $cb bytes. Prueba un clúster más pequeño.")
        if (clusters > 0x0FFFFFF5L) throw IOException("Demasiados clústeres para FAT32: usa un clúster más grande.")
        return Plan(partStart, partSectors, spc, fatSectors, clusters)
    }

    /**
     * Formatea. [onProgress] recibe la fracción hecha (0..1).
     * La etiqueta debe venir ya saneada (ASCII, máx. 11).
     */
    fun format(io: BlockIo, label: String, clusterBytes: Int = 0, onProgress: (Float) -> Unit = {}) {
        val p = plan(io, clusterBytes)
        val bps = io.blockSize
        val volId = Random.nextInt()

        // 1. Limpiar restos de tablas anteriores: principio (MBR/GPT) y final (GPT de respaldo).
        val edge = minOf(io.blocks / 2, (1L shl 20) / bps)
        zero(io, 0, edge)
        zero(io, io.blocks - edge, edge)
        onProgress(0.05f)

        // 2. Sectores reservados y las dos FAT, a cero.
        val fatStart = p.partStart + RESERVED_SECTORS
        zero(io, p.partStart, RESERVED_SECTORS.toLong())
        val fatTotal = NUM_FATS * p.fatSectors
        zero(io, fatStart, fatTotal) { done -> onProgress(0.05f + 0.85f * done / fatTotal) }

        // 3. Directorio raíz (clúster 2) a cero y con la etiqueta de volumen.
        val dataStart = fatStart + fatTotal
        zero(io, dataStart, p.sectorsPerCluster.toLong())
        val root = sector(bps)
        root.put(label11(label).toByteArray(Charsets.US_ASCII))
        root.put(11, 0x08) // atributo: etiqueta de volumen
        io.write(dataStart, root.also { it.rewind() })

        // 4. Primeras entradas de cada FAT: medio, fin de cadena y el clúster del raíz.
        val fat0 = sector(bps)
        fat0.putInt(0, 0x0FFFFFF8); fat0.putInt(4, 0x0FFFFFFF); fat0.putInt(8, 0x0FFFFFFF)
        for (i in 0 until NUM_FATS) io.write(fatStart + i * p.fatSectors, fat0.duplicate().also { it.rewind() })

        // 5. Sector de arranque, FSInfo y sus copias (sectores 6 y 7).
        val boot = bootSector(p, bps, label, volId)
        val info = fsInfo(p, bps)
        io.write(p.partStart, boot.duplicate().also { it.rewind() })
        io.write(p.partStart + 1, info.duplicate().also { it.rewind() })
        io.write(p.partStart + 6, boot.duplicate().also { it.rewind() })
        io.write(p.partStart + 7, info.duplicate().also { it.rewind() })

        // 6. La tabla MBR, lo último: hasta aquí el disco no parece formateado a medias.
        io.write(0, mbr(p, bps, volId).also { it.rewind() })
        onProgress(1f)
    }

    // ── Estructuras ──────────────────────────────────────────────────────

    private fun sector(bps: Int): ByteBuffer = ByteBuffer.allocate(bps).order(ByteOrder.LITTLE_ENDIAN)

    private fun label11(label: String): String {
        val l = label.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' || it == ' ' }.take(11)
        return l.ifBlank { "NO NAME" }.padEnd(11, ' ')
    }

    private fun bootSector(p: Plan, bps: Int, label: String, volId: Int): ByteBuffer {
        val b = sector(bps)
        b.put(0, 0xEB.toByte()); b.put(1, 0x58); b.put(2, 0x90.toByte())
        b.position(3); b.put("MSWIN4.1".toByteArray(Charsets.US_ASCII))
        b.putShort(11, bps.toShort())
        b.put(13, p.sectorsPerCluster.toByte())
        b.putShort(14, RESERVED_SECTORS.toShort())
        b.put(16, NUM_FATS.toByte())
        b.putShort(17, 0)                 // entradas del raíz: 0 en FAT32
        b.putShort(19, 0)                 // total de sectores (16 bits): 0
        b.put(21, 0xF8.toByte())          // disco fijo
        b.putShort(22, 0)                 // FAT de 16 bits: 0
        b.putShort(24, 63); b.putShort(26, 255)
        b.putInt(28, p.partStart.toInt()) // sectores ocultos = inicio de la partición
        b.putInt(32, p.partSectors.toInt())
        b.putInt(36, p.fatSectors.toInt())
        b.putShort(40, 0); b.putShort(42, 0)
        b.putInt(44, 2)                   // clúster del raíz
        b.putShort(48, 1)                 // sector FSInfo
        b.putShort(50, 6)                 // copia del arranque
        b.put(64, 0x80.toByte())          // unidad
        b.put(66, 0x29)                   // firma extendida
        b.putInt(67, volId)
        b.position(71); b.put(label11(label).toByteArray(Charsets.US_ASCII))
        b.position(82); b.put("FAT32   ".toByteArray(Charsets.US_ASCII))
        // Código mínimo: si alguien intenta arrancar desde aquí, la BIOS pasa al siguiente disco.
        b.position(90); b.put(byteArrayOf(0xCD.toByte(), 0x18))
        b.put(510, 0x55); b.put(511, 0xAA.toByte())
        return b
    }

    private fun fsInfo(p: Plan, bps: Int): ByteBuffer {
        val b = sector(bps)
        b.putInt(0, 0x41615252)
        b.putInt(484, 0x61417272)
        b.putInt(488, (p.clusters - 1).toInt()) // libres: todos menos el raíz
        b.putInt(492, 3)                         // siguiente libre
        b.putInt(508, 0xAA550000.toInt())
        return b
    }

    private fun mbr(p: Plan, bps: Int, volId: Int): ByteBuffer {
        val b = sector(bps)
        b.putInt(440, volId)                     // firma del disco
        val e = 446
        b.put(e, 0x00)                           // no activa
        b.put(e + 1, 0xFE.toByte()); b.put(e + 2, 0xFF.toByte()); b.put(e + 3, 0xFF.toByte()) // CHS: usar LBA
        b.put(e + 4, 0x0C)                       // FAT32 (LBA)
        b.put(e + 5, 0xFE.toByte()); b.put(e + 6, 0xFF.toByte()); b.put(e + 7, 0xFF.toByte())
        b.putInt(e + 8, p.partStart.toInt())
        b.putInt(e + 12, p.partSectors.toInt())
        b.put(510, 0x55); b.put(511, 0xAA.toByte())
        return b
    }

    private fun zero(io: BlockIo, start: Long, count: Long, onChunk: (Long) -> Unit = {}) {
        val per = maxOf(1L, (1L shl 20) / io.blockSize)
        val buf = ByteBuffer.allocate((per * io.blockSize).toInt())
        var done = 0L
        while (done < count) {
            val n = minOf(per, count - done)
            buf.clear(); buf.limit((n * io.blockSize).toInt())
            io.write(start + done, buf)
            done += n
            onChunk(done)
        }
    }
}
