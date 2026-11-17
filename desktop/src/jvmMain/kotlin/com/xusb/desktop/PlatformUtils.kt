package com.xusb.desktop

import com.xusb.common.usb.NoBackend
import com.xusb.common.usb.UsbBackend
import com.xusb.desktop.backend.LinuxBackend
import com.xusb.desktop.backend.WindowsBackend

object PlatformUtils {
    private val os = System.getProperty("os.name").lowercase()
    val isWindows: Boolean = os.contains("win")
    val isLinux: Boolean = os.contains("linux")

    /** Backend real de la plataforma (macOS: aún sin soporte, falla de forma explícita). */
    fun createBackend(): UsbBackend = when {
        isWindows -> WindowsBackend()
        isLinux -> LinuxBackend()
        else -> NoBackend
    }
}
