package com.xusb.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.xusb.common.App
import com.xusb.common.usb.UsbPlatform
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

private fun pick(title: String, mode: Int, name: String?): String? {
    val d = FileDialog(null as Frame?, title, mode)
    if (name != null) d.file = name
    d.isVisible = true
    val f = d.file ?: return null
    return File(d.directory, f).absolutePath
}

fun main() = application {
    UsbPlatform.backend = PlatformUtils.createBackend()
    UsbPlatform.workDir = File(System.getProperty("user.home"), "X-USB").absolutePath
    UsbPlatform.openFile = { title -> pick(title, FileDialog.LOAD, null) }
    UsbPlatform.saveFile = { title, name -> pick(title, FileDialog.SAVE, name) }
    val viewModel = DesktopViewModel()
    Window(
        onCloseRequest = ::exitApplication,
        title = "X-USB",
        state = rememberWindowState(width = 1100.dp, height = 760.dp)
    ) {
        App(viewModel)
    }
}
