package com.xusb.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val ACTION_USB_PERMISSION = "com.xusb.USB_PERMISSION"

class XUsbManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectedDrives = MutableStateFlow<List<UsbDriveInfo>>(emptyList())
    val connectedDrives: StateFlow<List<UsbDriveInfo>> = _connectedDrives

    private val _selectedDrive = MutableStateFlow(UsbDriveInfo.NONE)
    val selectedDrive: StateFlow<UsbDriveInfo> = _selectedDrive

    // Mapeo device.deviceId → UsbDriveInfo para updates rápidos
    private val driveMap = mutableMapOf<Int, UsbDriveInfo>()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    getDevice(intent)?.let { requestPermission(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = getDevice(intent) ?: return
                    driveMap.remove(device.deviceId)
                    publishDrives()
                }
                ACTION_USB_PERMISSION -> {
                    val device = getDevice(intent) ?: return
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) updateDriveInfo(device)
                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else
            context.registerReceiver(usbReceiver, filter)

        // Enumerar dispositivos ya conectados
        usbManager.deviceList.values.forEach { requestPermission(it) }
    }

    fun unregister() {
        try { context.unregisterReceiver(usbReceiver) } catch (_: Exception) {}
    }

    fun selectDrive(drive: UsbDriveInfo) {
        _selectedDrive.value = drive
    }

    fun eject() {
        _selectedDrive.value = UsbDriveInfo.NONE
    }

    private fun getDevice(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

    private fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            updateDriveInfo(device)
            return
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
        usbManager.requestPermission(device, pi)
    }

    private fun updateDriveInfo(device: UsbDevice) {
        val blockPath = resolveBlockPath(device)
        val sizeBytes = if (blockPath.isNotEmpty()) queryBlockSize(blockPath) else 0L

        val info = UsbDriveInfo(
            device      = device,
            name        = device.productName ?: device.manufacturerName ?: "USB Device",
            path        = blockPath,
            sizeBytes   = sizeBytes,
            isConnected = true,
            usbVersion  = if (device.version?.startsWith("3") == true) "USB 3.0" else "USB 2.0"
        )
        driveMap[device.deviceId] = info
        publishDrives()
    }

    // Intenta descubrir el nodo de bloque real. Requiere root.
    private fun resolveBlockPath(device: UsbDevice): String {
        return try {
            // Busca nodos USB en lsblk
            val result = com.topjohnwu.superuser.Shell
                .cmd("lsblk -d -n -o NAME,TRAN 2>/dev/null | grep usb | head -5")
                .exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                // Puede haber varios; heurística: el más reciente (último)
                // Por ahora coge el primero que no sea ya conocido
                val known = driveMap.values.map { it.path.removePrefix("/dev/") }.toSet()
                val candidate = result.out
                    .map { it.trim().split("\\s+".toRegex()).firstOrNull() ?: "" }
                    .filter { it.isNotEmpty() && "/dev/$it" !in known.map { k -> "/dev/$k" } }
                    .firstOrNull()
                if (candidate != null) "/dev/$candidate" else "/dev/sdb"
            } else "/dev/sdb"
        } catch (_: Exception) { "/dev/sdb" }
    }

    private fun queryBlockSize(path: String): Long {
        return try {
            val result = com.topjohnwu.superuser.Shell
                .cmd("blockdev --getsize64 $path 2>/dev/null")
                .exec()
            if (result.isSuccess && result.out.isNotEmpty())
                result.out[0].trim().toLongOrNull() ?: 0L
            else 0L
        } catch (_: Exception) { 0L }
    }

    private fun publishDrives() {
        val list = driveMap.values.toList()
        _connectedDrives.value = list
        // Si el seleccionado ya no está en la lista, auto-seleccionar el primero
        if (_selectedDrive.value == UsbDriveInfo.NONE || !list.any { it.path == _selectedDrive.value.path }) {
            _selectedDrive.value = list.firstOrNull() ?: UsbDriveInfo.NONE
        }
    }
}
