package com.xusb.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.xusb.common.App

fun main() = application {
    val viewModel = DesktopViewModel()
    Window(
        onCloseRequest = ::exitApplication,
        title = "X-USB Desktop",
        state = rememberWindowState(width = 1000.dp, height = 800.dp)
    ) {
        App(viewModel)
    }
}
