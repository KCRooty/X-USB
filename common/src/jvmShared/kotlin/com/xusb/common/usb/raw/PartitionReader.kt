package com.xusb.common.usb.raw

import com.xusb.common.usb.PartitionInfo
import com.xusb.common.usb.humanBytes
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lee la tabla de particiones (MBR o GPT) directamente de los sectores del disco, sin parted ni root.
 * Solo lectura: no modifica nada.
 */
object PartitionReader {

    data class Table(val type: String, val partitions: List<PartitionInfo>)

    fun read(io: BlockIo): Table {
        val bps = io.blockSize
        val s0 = sector(io, 0)
        if ((s0.get(510).toInt() and 0xFF) != 0x55 || (s0.get(511).toInt() and 0xFF) != 0xAA) return Table("Ninguna", emptyList())

        val entries = (0 until 4).map { i -> 446 + i * 16 }
        if (entries.any { (s0.get(it + 4).toInt() and 0xFF) == 0xEE }) return readGpt(io)

        // Sin entradas válidas pero con firma: puede ser un sistema de archivos sin tabla ("superfloppy").
        val parts = entries.mapIndexedNotNull { i, e ->
            val type = s0.get(e + 4).toInt() and 0xFF
            val start = s0.getInt(e + 8).toLong() and 0xFFFFFFFFL
            val count = s0.getInt(e + 12).toLong() and 0xFFFFFFFFL
            if (type == 0 || count == 0L) return@mapIndexedNotNull null
            val fs = detectFs(io, start) ?: mbrTypeName(type)
            PartitionInfo(i + 1, humanBytes(start * bps), humanBytes((start + count) * bps), humanBytes(count * bps), fs,
                if ((s0.get(e).toInt() and 0xFF) == 0x80) "boot" else "")
        }
        if (parts.isEmpty()) {
            detectFs(io, 0)?.let { return Table("Sin tabla", listOf(PartitionInfo(1, "0 B", humanBytes(io.blocks * bps), humanBytes(io.blocks * bps), it, ""))) }
        }
        return Table("MBR", parts)
    }

    private fun readGpt(io: BlockIo): Table {
        val bps = io.blockSize
        val h = sector(io, 1)
        val sig = ByteArray(8).also { h.position(0); h.get(it) }
        if (String(sig, Charsets.US_ASCII) != "EFI PART") return Table("GPT (dañada)", emptyList())
        val entriesLba = h.getLong(72)
        val count = h.getInt(80).coerceIn(0, 256)
        val size = h.getInt(84).coerceIn(128, 1024)
        val bytes = count * size
        val sectors = (bytes + bps - 1) / bps
        val buf = ByteBuffer.allocate(sectors * bps).order(ByteOrder.LITTLE_ENDIAN)
        io.read(entriesLba, buf)
        val parts = (0 until count).mapNotNull { i ->
            val o = i * size
            val typeEmpty = (0 until 16).all { buf.get(o + it).toInt() == 0 }
            if (typeEmpty) return@mapNotNull null
            val first = buf.getLong(o + 32); val last = buf.getLong(o + 40)
            val name = String(ByteArray(72) { buf.get(o + 56 + it) }, Charsets.UTF_16LE).trimEnd('\u0000')
            val fs = detectFs(io, first) ?: name.ifBlank { "?" }
            PartitionInfo(i + 1, humanBytes(first * bps), humanBytes((last + 1) * bps), humanBytes((last - first + 1) * bps), fs, name)
        }
        return Table("GPT", parts)
    }

    /** Reconoce el sistema de archivos por su firma en el primer sector de la partición. */
    private fun detectFs(io: BlockIo, startLba: Long): String? = runCatching {
        if (startLba < 0 || startLba >= io.blocks) return null
        val b = sector(io, startLba)
        val at = { off: Int, len: Int -> String(ByteArray(len) { b.get(off + it) }, Charsets.US_ASCII) }
        when {
            at(3, 8) == "EXFAT   " -> "exFAT"
            at(3, 8) == "NTFS    " -> "NTFS"
            at(82, 8) == "FAT32   " -> "FAT32"
            at(54, 8).startsWith("FAT16") -> "FAT16"
            at(54, 8).startsWith("FAT12") -> "FAT12"
            else -> {
                // ext2/3/4: el superbloque empieza a 1024 bytes; la firma 0xEF53 está en el byte 56 de él.
                val lba = startLba + 1024 / io.blockSize
                val sb = sector(io, lba)
                val off = (1024 % io.blockSize) + 56
                if (off + 1 < io.blockSize && (sb.getShort(off).toInt() and 0xFFFF) == 0xEF53) "ext" else null
            }
        }
    }.getOrNull()

    private fun mbrTypeName(t: Int) = when (t) {
        0x01 -> "FAT12"; 0x04, 0x06, 0x0E -> "FAT16"; 0x0B, 0x0C -> "FAT32"
        0x07 -> "NTFS/exFAT"; 0x05, 0x0F -> "extendida"; 0x82 -> "Linux swap"; 0x83 -> "Linux"
        0xEF -> "EFI"; 0x17 -> "NTFS oculta"; else -> "tipo 0x%02X".format(t)
    }

    private fun sector(io: BlockIo, lba: Long): ByteBuffer {
        val b = ByteBuffer.allocate(io.blockSize).order(ByteOrder.LITTLE_ENDIAN)
        io.read(lba, b)
        b.rewind()
        return b
    }
}
