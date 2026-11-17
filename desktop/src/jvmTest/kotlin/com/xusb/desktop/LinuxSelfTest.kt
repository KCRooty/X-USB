package com.xusb.desktop

import com.xusb.common.usb.*
import com.xusb.desktop.backend.LinuxBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

/**
 * Prueba de integración del backend de Linux. SOLO sobre un dispositivo loop de pruebas:
 *   java -Dxusb.testDevices=/dev/loopN -cp ... com.xusb.desktop.LinuxSelfTestKt /dev/loopN /ruta/dir /dev/loopM(señuelo)
 * Debe ejecutarse como root (en WSL). Nunca apuntarla a un disco real.
 */
private var failed = 0
private fun check(name: String, ok: Boolean, extra: String = "") {
    println((if (ok) "PASS " else "FAIL ") + name + (if (extra.isNotEmpty()) "  [$extra]" else ""))
    if (!ok) failed++
}

private fun Flow<OperationProgress>.last(): OperationProgress = runBlocking { toList().last() }
private fun sh(vararg c: String) = ProcessBuilder(*c).redirectErrorStream(true).start().let { p ->
    val o = p.inputStream.bufferedReader().readText(); p.waitFor(); o.trim()
}

fun main(args: Array<String>) {
    val dev = args[0]
    val dir = File(args[1]).also { it.mkdirs() }
    val b = LinuxBackend()
    UsbPlatform.backend = b
    println("== LinuxBackend sobre $dev (root=${b.isPrivileged()}) ==")

    // 1. Guardas: un disco que no es USB ni de pruebas debe rechazarse
    val decoy = args[2] // otro loop que NO está en testDevices: debe tratarse como disco no-USB
    val rej = b.wipe(decoy).last()
    check("guarda rechaza un disco que no es USB", rej.error != null && !rej.isComplete, rej.error ?: "")
    check("guarda rechaza ruta con inyección", b.format("$decoy; rm -rf /", Filesystem.EXT4, "x", 0).last().error != null)

    // 2. Formato FAT32 y comprobación con blkid
    var r = b.format(dev, Filesystem.FAT32, "mi usb!", 0).last()
    check("format FAT32", r.isComplete, r.error ?: "")
    val p1 = b.partitionPath(dev, 1)
    check("FAT32 detectado", sh("blkid", "-o", "value", "-s", "TYPE", p1) == "vfat", sh("blkid", p1))
    check("etiqueta saneada", sh("blkid", "-o", "value", "-s", "LABEL", p1) == "MI USB", sh("blkid", p1))
    check("tabla MBR", b.partitionTableType(dev) == "MBR", b.partitionTableType(dev))

    // 3. ext4, exFAT, NTFS
    for ((fs, t) in listOf(Filesystem.EXT4 to "ext4", Filesystem.EXFAT to "exfat", Filesystem.NTFS to "ntfs")) {
        r = b.format(dev, fs, "T", 0).last()
        val type = sh("blkid", "-o", "value", "-s", "TYPE", p1)
        check("format ${fs.label}", r.isComplete && type == t, r.error ?: type)
    }

    // 4. Particiones: borrar, crear dos, listar, convertir a GPT
    b.format(dev, Filesystem.FAT32, "A", 0).last()
    r = b.deletePartition(dev, 1).last()
    check("deletePartition", r.isComplete && b.listPartitions(dev).isEmpty(), r.error ?: "")
    r = b.createPartition(dev, Filesystem.EXT4).last()
    check("createPartition", r.isComplete && b.listPartitions(dev).size == 1, r.error ?: b.listPartitions(dev).toString())
    r = b.convertPartitionTable(dev, true).last()
    check("MBR→GPT", r.isComplete && b.partitionTableType(dev) == "GPT", r.error ?: b.partitionTableType(dev))
    check("las particiones sobreviven a la conversión", b.listPartitions(dev).size == 1)

    // 5. Escritura en bruto + backup + restore, con checksums
    val img = File(dir, "img.bin").also { f -> f.writeBytes(ByteArray(20 * 1024 * 1024 + 123) { (it * 31 + 7).toByte() }) }
    r = b.rawWrite(img.path, dev).last()
    check("rawWrite", r.isComplete, r.error ?: "")
    val written = sh("sh", "-c", "head -c ${img.length()} $dev | sha256sum | cut -d' ' -f1")
    val orig = sh("sh", "-c", "sha256sum ${img.path} | cut -d' ' -f1")
    check("contenido escrito idéntico (sha256)", written == orig, "$written vs $orig")

    val bak = File(dir, "backup.img")
    r = b.backup(dev, bak.path).last()
    check("backup", r.isComplete && bak.length() > 0, r.error ?: "${bak.length()}")
    val bakHead = sh("sh", "-c", "head -c ${img.length()} ${bak.path} | sha256sum | cut -d' ' -f1")
    check("backup contiene lo escrito", bakHead == orig)

    // 6. Borrado
    r = b.wipe(dev).last()
    val allZero = sh("sh", "-c", "head -c 4194304 $dev | tr -d '\\000' | wc -c") == "0"
    check("wipe deja el disco a cero", r.isComplete && allZero, r.error ?: "")
    r = b.restore(bak.path, dev).last()
    check("restore", r.isComplete, r.error ?: "")

    // 7. BIN→ISO
    val bin = File(dir, "t.bin").also { f -> f.writeBytes(ByteArray(2352 * 10) { (it % 251).toByte() }) }
    val iso = File(dir, "t.iso")
    r = b.binToIso(bin.path, iso.path).last()
    val expected = ByteArray(2048 * 10) { i -> val s = i / 2048; val o = i % 2048; ((s * 2352 + 16 + o) % 251).toByte() }
    check("BIN→ISO", r.isComplete && iso.length() == 20480L && iso.readBytes().contentEquals(expected), r.error ?: "${iso.length()}")

    println(if (failed == 0) "== TODO OK ==" else "== $failed FALLOS ==")
    exitProcess(if (failed == 0) 0 else 1)
}
