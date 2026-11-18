package com.xusb.common.usb

// ── Unidad USB ────────────────────────────────────────────────────────────
data class UsbDriveInfo(
    val name: String,
    val path: String,          // /dev/sdb, o el número de disco en Windows
    val sizeBytes: Long,
    val isConnected: Boolean,
    val usbVersion: String = ""
) {
    val sizeDisplay: String get() = humanBytes(sizeBytes)

    /** Identificador corto que el usuario escribe para confirmar una operación destructiva. */
    val shortId: String get() = path.substringAfterLast('/').substringAfterLast('\\').ifEmpty { path }

    companion object {
        val NONE = UsbDriveInfo("", "", 0L, false)
    }
}

// ── Progreso de una operación ─────────────────────────────────────────────
data class OperationProgress(
    val isRunning: Boolean = false,
    val label: String = "",
    val progressPct: Float = 0f,
    val speedMbs: Double = 0.0,
    val writtenBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isComplete: Boolean = false,
    val error: String? = null
) {
    val writtenDisplay: String get() = humanBytes(writtenBytes)
    val totalDisplay: String get() = humanBytes(totalBytes)
}

// ── Catálogo de imágenes descargables ─────────────────────────────────────
// Solo enlaces directos a un .iso, comprobados el 2026-11-17 (los que dan 404 se retiran).
data class IsoEntry(
    val id: String,
    val name: String,
    val version: String,
    val category: IsoCategory,
    val sizeBytes: Long,
    val url: String,
)

enum class IsoCategory(val es: String, val en: String) {
    LINUX("Linux", "Linux"),
    UTILITIES("Utilidades", "Utilities"),
}

val ISO_CATALOG = listOf(
    IsoEntry("debian", "Debian netinst", "13.7", IsoCategory.LINUX, 792_723_456L,
        "https://cdimage.debian.org/debian-cd/current/amd64/iso-cd/debian-13.7.0-amd64-netinst.iso"),
    IsoEntry("ubuntu2404", "Ubuntu Desktop", "24.04.4 LTS", IsoCategory.LINUX, 6_655_619_072L,
        "https://releases.ubuntu.com/24.04/ubuntu-24.04.4-desktop-amd64.iso"),
    IsoEntry("ubuntu2204", "Ubuntu Desktop", "22.04.5 LTS", IsoCategory.LINUX, 4_762_707_968L,
        "https://releases.ubuntu.com/22.04/ubuntu-22.04.5-desktop-amd64.iso"),
    IsoEntry("mint22", "Linux Mint Cinnamon", "22", IsoCategory.LINUX, 2_907_832_320L,
        "https://mirrors.edge.kernel.org/linuxmint/stable/22/linuxmint-22-cinnamon-64bit.iso"),
    IsoEntry("arch", "Arch Linux", "última", IsoCategory.LINUX, 1_317_568_512L,
        "https://geo.mirror.pkgbuild.com/iso/latest/archlinux-x86_64.iso"),
    IsoEntry("tinycore", "TinyCore", "14 (current)", IsoCategory.LINUX, 24_117_248L,
        "http://www.tinycorelinux.net/14.x/x86/release/TinyCore-current.iso"),
    IsoEntry("hirens", "Hiren's BootCD PE", "x64", IsoCategory.UTILITIES, 3_291_686_912L,
        "https://www.hirensbootcd.org/files/HBCD_PE_x64.iso"),
)

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

data class PartitionInfo(
    val number: Int,
    val start: String,
    val end: String,
    val size: String,
    val filesystem: String,
    val flags: String
)
