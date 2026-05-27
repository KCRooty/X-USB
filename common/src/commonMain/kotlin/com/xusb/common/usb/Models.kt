package com.xusb.common.usb

// ── USB Device Info ───────────────────────────────────────────────────────
data class UsbDriveInfo(
    val name: String,
    val path: String,          // e.g. /dev/sdb o \\.\PhysicalDrive1
    val sizeBytes: Long,
    val isConnected: Boolean,
    val usbVersion: String = "USB 2.0"
) {
    val sizeGb: Double get() = sizeBytes / 1_073_741_824.0
    val sizeDisplay: String get() = "%.1f GB".format(sizeGb)
    companion object {
        val NONE = UsbDriveInfo("No Device", "", 0L, false)
    }
}

// ── Operation Progress ────────────────────────────────────────────────────
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
    val writtenDisplay: String get() = formatBytes(writtenBytes)
    val totalDisplay:   String get() = formatBytes(totalBytes)
    private fun formatBytes(b: Long) = when {
        b >= 1_073_741_824L -> "%.1f GB".format(b / 1_073_741_824.0)
        b >= 1_048_576L     -> "%.1f MB".format(b / 1_048_576.0)
        else                -> "$b B"
    }
}

// ── App Feature / Menu Item ───────────────────────────────────────────────
enum class FeatureId {
    VENTOY, ISO_BURNER, MULTI_OS_UEFI, FORMAT_USB,
    PARTITION_WIZARD, PS2_USB, FILE_MANAGER,
    PARTITION_MOUNTER, WIPE_USB, RAW_WRITE,
    BACKUP_RESTORE, ISO_DOWNLOADER
}

data class Feature(
    val id: FeatureId,
    val titleEn: String,
    val titleEs: String,
    val subtitleEn: String,
    val subtitleEs: String,
    val icon: String,
    val badgeEn: String? = null,
    val badgeEs: String? = null,
    val requiresRoot: Boolean = false,
    val isNew: Boolean = false,
)

val ALL_FEATURES = listOf(
    Feature(
        FeatureId.VENTOY, "Ventoy X", "Ventoy X",
        "Multi-boot USB. No reformatting needed.",
        "USB multi-arranque. Sin reformatear.",
        "Usb", badgeEn = "Recommended", badgeEs = "Recomendado"
    ),
    Feature(
        FeatureId.ISO_BURNER, "ISO Burner", "ISO Burner",
        "Windows, Linux, macOS DMG, Raspberry Pi, FreeDOS",
        "Windows, Linux, macOS DMG, Raspberry Pi, FreeDOS",
        "Album"
    ),
    Feature(
        FeatureId.MULTI_OS_UEFI, "MultiOS USB (UEFI)", "MultiOS USB (UEFI)",
        "Unofficial UEFI multi-boot solution.",
        "Solución multi-arranque UEFI no oficial.",
        "DeveloperBoard"
    ),
    Feature(
        FeatureId.ISO_DOWNLOADER, "ISO Downloader", "Descargador de ISOs",
        "Download OS images directly to your USB.",
        "Descarga imágenes de SO directamente al USB.",
        "CloudDownload", isNew = true
    ),
    Feature(
        FeatureId.FORMAT_USB, "Format USB", "Formatear USB",
        "FAT16, FAT32, exFAT, NTFS, Ext2/3/4, F2FS",
        "FAT16, FAT32, exFAT, NTFS, Ext2/3/4, F2FS",
        "Storage", requiresRoot = true
    ),
    Feature(
        FeatureId.PARTITION_WIZARD, "Partition Wizard", "Asistente de Particiones",
        "Create, delete, resize partitions.",
        "Crear, eliminar y redimensionar particiones.",
        "TableChart", requiresRoot = true
    ),
    Feature(
        FeatureId.FILE_MANAGER, "USB File Manager", "Gestor de Archivos USB",
        "Simple USB file explorer.",
        "Explorador de archivos USB.",
        "FolderOpen"
    ),
    Feature(
        FeatureId.PARTITION_MOUNTER, "Partition Mounter", "Montar Partición",
        "Mount partition as external storage.",
        "Monta una partición como almacenamiento externo.",
        "Storage", requiresRoot = true
    ),
    Feature(
        FeatureId.WIPE_USB, "Wipe USB", "Borrar USB",
        "Secure erase — ALL data and partitions deleted.",
        "Borrado seguro — TODOS los datos y particiones eliminados.",
        "DeleteForever", requiresRoot = true
    ),
    Feature(
        FeatureId.RAW_WRITE, "Raw Write", "Escritura Raw",
        "Write image file to USB device byte by byte.",
        "Escribe imagen al USB byte a byte.",
        "Memory"
    ),
    Feature(
        FeatureId.PS2_USB, "PS2 USB Utils", "PS2 USB Utils",
        "Manage PS2 games on USB: add, remove, organize.",
        "Gestiona juegos PS2 en USB: añadir, eliminar, organizar.",
        "SportsEsports"
    ),
    Feature(
        FeatureId.BACKUP_RESTORE, "Backup / Restore", "Copia de seguridad",
        "Full backup and restore from USB device.",
        "Copia de seguridad y restauración completa.",
        "Restore"
    ),
)

// ── Bundled / Downloadable ISOs ───────────────────────────────────────────
data class IsoEntry(
    val id: String,
    val nameDisplay: String,
    val version: String,
    val category: IsoCategory,
    val sizeDisplay: String,
    val downloadUrl: String,
    val sha256: String = "",
    val isBundled: Boolean = false,
    val assetPath: String = "",
)

enum class IsoCategory { LINUX, WINDOWS, MACOS, EMBEDDED, UTILITIES, GAMING }

val ISO_CATALOG = listOf(
    IsoEntry("freedos", "FreeDOS", "1.3",     IsoCategory.UTILITIES, "~40 MB",
        "https://www.freedos.org/download/download/FD13-LiveCD.iso", isBundled = true, assetPath = "isos/freedos.iso"),
    IsoEntry("tinycore", "TinyCore Linux", "14.0", IsoCategory.LINUX, "~22 MB",
        "http://www.tinycorelinux.net/14.x/x86/release/TinyCore-current.iso", isBundled = true, assetPath = "isos/tinycore.iso"),
    IsoEntry("msdos622", "MS-DOS", "6.22",    IsoCategory.UTILITIES, "~2 MB",
        "https://archive.org/download/msdos622/msdos622.iso", isBundled = true, assetPath = "isos/msdos622.iso"),
    IsoEntry("ubuntu2404", "Ubuntu", "24.04 LTS", IsoCategory.LINUX, "~5.8 GB",
        "https://releases.ubuntu.com/24.04/ubuntu-24.04-desktop-amd64.iso"),
    IsoEntry("ubuntu2204", "Ubuntu", "22.04 LTS", IsoCategory.LINUX, "~4.7 GB",
        "https://releases.ubuntu.com/22.04/ubuntu-22.04.3-desktop-amd64.iso"),
    IsoEntry("debian12", "Debian", "12 Bookworm", IsoCategory.LINUX, "~3.7 GB",
        "https://cdimage.debian.org/debian-cd/current/amd64/iso-cd/debian-12.5.0-amd64-netinst.iso"),
    IsoEntry("fedora40", "Fedora Workstation", "40", IsoCategory.LINUX, "~2.1 GB",
        "https://download.fedoraproject.org/pub/fedora/linux/releases/40/Workstation/x86_64/iso/Fedora-Workstation-Live-x86_64-40-1.14.iso"),
    IsoEntry("arch", "Arch Linux", "2024.04", IsoCategory.LINUX, "~850 MB",
        "https://geo.mirror.pkgbuild.com/iso/latest/archlinux-x86_64.iso"),
    IsoEntry("manjaro", "Manjaro KDE", "24.0", IsoCategory.LINUX, "~3.5 GB",
        "https://download.manjaro.org/kde/24.0/manjaro-kde-24.0-240513-linux66.iso"),
    IsoEntry("mint22", "Linux Mint", "22 Wilma", IsoCategory.LINUX, "~2.8 GB",
        "https://mirrors.edge.kernel.org/linuxmint/stable/22/linuxmint-22-cinnamon-64bit.iso"),
    IsoEntry("rpios", "Raspberry Pi OS", "2024-03", IsoCategory.EMBEDDED, "~1.2 GB",
        "https://downloads.raspberrypi.com/raspios_lite_arm64/images/raspios_lite_arm64-2024-03-15/2024-03-15-raspios-bookworm-arm64-lite.img.xz"),
    IsoEntry("win11", "Windows 11", "24H2", IsoCategory.WINDOWS, "~6.2 GB",
        "https://www.microsoft.com/software-download/windows11"),
    IsoEntry("win10", "Windows 10", "22H2", IsoCategory.WINDOWS, "~5.8 GB",
        "https://www.microsoft.com/software-download/windows10"),
    IsoEntry("clonezilla", "Clonezilla Live", "3.1.2", IsoCategory.UTILITIES, "~500 MB",
        "https://clonezilla.org/downloads/download.php?branch=stable"),
    IsoEntry("gparted", "GParted Live", "1.6.0", IsoCategory.UTILITIES, "~650 MB",
        "https://gparted.org/download.php"),
    IsoEntry("hirens", "Hiren's BootCD PE", "1.0.2", IsoCategory.UTILITIES, "~2.8 GB",
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
