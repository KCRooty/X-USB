package com.xusb.desktop

import com.xusb.common.usb.raw.BlockIo
import com.xusb.common.usb.raw.Fat32Formatter
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Prueba del formateador FAT32 sin root contra archivos de imagen (nunca contra discos).
 * Uso: java -cp <testRuntime> com.xusb.desktop.Fat32SelfTestKt <imagen> <MiB> [sector] [cluster]
 * Después se comprueba con fsck.fat y mtools desde fuera.
 */
class FileBlockIo(private val f: RandomAccessFile, override val blockSize: Int) : BlockIo {
    override val blocks: Long get() = f.length() / blockSize
    override fun read(lba: Long, buffer: ByteBuffer) {
        f.seek(lba * blockSize); val n = buffer.remaining(); f.readFully(buffer.array(), buffer.position(), n); buffer.position(buffer.position() + n)
    }
    override fun write(lba: Long, buffer: ByteBuffer) {
        require(lba >= 0 && lba * blockSize + buffer.remaining() <= f.length()) { "fuera de la imagen" }
        f.seek(lba * blockSize); val n = buffer.remaining(); f.write(buffer.array(), buffer.arrayOffset() + buffer.position(), n); buffer.position(buffer.position() + n)
    }
}

fun main(args: Array<String>) {
    if (args[0] == "--leer") {
        RandomAccessFile(File(args[1]), "r").use { raf ->
            val t = com.xusb.common.usb.raw.PartitionReader.read(FileBlockIo(raf, 512))
            println("tabla=${t.type} ${t.partitions}")
        }
        return
    }
    val path = args[0]; val mib = args[1].toLong(); val bs = args.getOrNull(2)?.toInt() ?: 512; val cl = args.getOrNull(3)?.toInt() ?: 0
    val file = File(path); file.delete()
    RandomAccessFile(file, "rw").use { raf ->
        raf.setLength(mib shl 20)
        val io = FileBlockIo(raf, bs)
        val p = Fat32Formatter.plan(io, cl)
        println("start=${p.partStart} sectors=${p.partSectors} spc=${p.sectorsPerCluster} fat=${p.fatSectors} clusters=${p.clusters}")
        Fat32Formatter.format(io, "X-USB TEST", cl)
        val t = com.xusb.common.usb.raw.PartitionReader.read(io)
        println("tabla=${t.type} ${t.partitions}")
    }
    println("ok")
}
