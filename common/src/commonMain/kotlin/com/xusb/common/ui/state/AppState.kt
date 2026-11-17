package com.xusb.common.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xusb.common.usb.OperationProgress
import com.xusb.common.usb.UsbDriveInfo
import com.xusb.common.usb.UsbPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Unidades detectadas y la elegida. Todo lo que toca el sistema va por el backend, nunca en el hilo de la UI. */
class DriveState(private val scope: CoroutineScope) {
    var drives by mutableStateOf<List<UsbDriveInfo>>(emptyList())
        private set
    var selectedPath by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
        private set
    var scanError by mutableStateOf<String?>(null)
        private set

    /** Mientras hay una operación en curso no se vuelve a escanear. */
    var busy by mutableStateOf(false)

    val selected: UsbDriveInfo? get() = drives.firstOrNull { it.path == selectedPath } ?: drives.firstOrNull()

    fun refresh() {
        if (loading || busy) return
        scope.launch {
            loading = true
            try {
                drives = withContext(Dispatchers.IO) { UsbPlatform.backend.listDrives() }
                scanError = null
            } catch (e: Exception) {
                scanError = e.message ?: e.toString()
            }
            if (selectedPath != null && drives.none { it.path == selectedPath }) selectedPath = null
            loading = false
        }
    }

    fun eject(drive: UsbDriveInfo, onResult: (Boolean) -> Unit) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { UsbPlatform.backend.eject(drive.path) }.getOrDefault(false) }
            onResult(ok)
            refresh()
        }
    }
}

/** Ejecuta una operación del backend y expone su progreso. No hay cancelación: dd no se puede interrumpir a medias de forma segura. */
class OpRunner(private val scope: CoroutineScope, private val drives: DriveState) {
    var progress by mutableStateOf(OperationProgress())
        private set
    var started by mutableStateOf(false)
        private set
    val running: Boolean get() = progress.isRunning

    fun run(flow: Flow<OperationProgress>) {
        if (running) return
        started = true
        drives.busy = true
        progress = OperationProgress(isRunning = true)
        scope.launch {
            try {
                flow.flowOn(Dispatchers.IO).collect { progress = it }
                if (progress.isRunning) progress = progress.copy(isRunning = false)
            } catch (e: Exception) {
                progress = OperationProgress(error = e.message ?: e.toString())
            }
            drives.busy = false
            drives.refresh()
        }
    }

    fun clear() {
        if (!running) { started = false; progress = OperationProgress() }
    }
}
