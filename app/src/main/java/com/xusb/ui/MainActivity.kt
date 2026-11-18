package com.xusb.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.xusb.common.App
import com.xusb.common.usb.AndroidBackend
import com.xusb.common.usb.UsbPlatform
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** La interfaz es la misma que en escritorio (módulo :common); aquí se registran el backend y el selector de archivos de Android. */
class MainActivity : ComponentActivity() {
    private val vm: AndroidViewModel2 by viewModels()

    // El selector del sistema es asíncrono; la interfaz común lo pide desde un hilo de IO y espera la respuesta.
    @Volatile private var pickLatch: CountDownLatch? = null
    @Volatile private var pickResult: String? = null

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> finishPick(uri?.toString()) }
    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> finishPick(uri?.toString()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (UsbPlatform.backend !is AndroidBackend) UsbPlatform.backend = AndroidBackend(applicationContext)
        UsbPlatform.workDir = (getExternalFilesDir(null) ?: filesDir).absolutePath
        UsbPlatform.openFile = { _ -> pick { openDoc.launch(arrayOf("*/*")) } }
        UsbPlatform.saveFile = { _, name -> pick { createDoc.launch(name) } }
        enableEdgeToEdge()
        setContent { App(vm, usbEvents = vm.usbEvents) }
    }

    private fun pick(launch: () -> Unit): String? {
        val latch = CountDownLatch(1)
        pickResult = null
        pickLatch = latch
        runOnUiThread { runCatching(launch).onFailure { finishPick(null) } }
        latch.await(10, TimeUnit.MINUTES)
        return pickResult
    }

    private fun finishPick(value: String?) {
        pickResult = value
        pickLatch?.countDown()
    }
}
