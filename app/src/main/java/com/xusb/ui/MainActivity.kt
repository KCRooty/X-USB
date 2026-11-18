package com.xusb.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.xusb.common.App
import com.xusb.common.usb.AndroidBackend
import com.xusb.common.usb.UsbPlatform

/** La interfaz es la misma que en escritorio (módulo :common); aquí solo se registra el backend de Android. */
class MainActivity : ComponentActivity() {
    private val vm: AndroidViewModel2 by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (UsbPlatform.backend !is AndroidBackend) UsbPlatform.backend = AndroidBackend()
        UsbPlatform.workDir = (getExternalFilesDir(null) ?: filesDir).absolutePath
        enableEdgeToEdge()
        setContent { App(vm, usbEvents = vm.usbEvents) }
    }
}
