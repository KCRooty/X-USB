package com.xusb.desktop

import com.xusb.common.usb.UsbDriveInfo
import java.io.BufferedReader
import java.io.InputStreamReader

object PlatformUtils {
    val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")
    val isLinux: Boolean = System.getProperty("os.name").lowercase().contains("linux")

    fun listUsbDrives(): List<UsbDriveInfo> {
        return if (isWindows) listWindowsDrives() else if (isLinux) listLinuxDrives() else emptyList()
    }

    private fun listLinuxDrives(): List<UsbDriveInfo> {
        val drives = mutableListOf<UsbDriveInfo>()
        try {
            val simpleProcess = Runtime.getRuntime().exec("lsblk -d -n -b -o NAME,SIZE,MODEL,TRAN")
            val simpleReader = BufferedReader(InputStreamReader(simpleProcess.inputStream))
            simpleReader.forEachLine { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 4 && parts.contains("usb")) {
                    drives.add(UsbDriveInfo(
                        name = parts.getOrNull(2) ?: "USB Drive",
                        path = "/dev/${parts[0]}",
                        sizeBytes = parts[1].toLongOrNull() ?: 0L,
                        isConnected = true,
                        usbVersion = "USB 3.0"
                    ))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return drives
    }

    private fun listWindowsDrives(): List<UsbDriveInfo> {
        return emptyList() // TODO
    }
}
