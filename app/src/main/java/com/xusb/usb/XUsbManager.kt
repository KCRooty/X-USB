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

    private val _connectedDrive = MutableStateFlow(UsbDriveInfo.NONE)
    val connectedDrive: StateFlow<UsbDriveInfo> = _connectedDrive

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let { requestPermission(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    _connectedDrive.value = UsbDriveInfo.NONE
                }
                ACTION_USB_PERMISSION -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) updateDriveInfo(device)
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

        // Check already-connected devices
        usbManager.deviceList.values.firstOrNull()?.let { requestPermission(it) }
    }

    fun unregister() {
        try { context.unregisterReceiver(usbReceiver) } catch (_: Exception) {}
    }

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
        // Try to get actual size via shell (requires root)
        val sizeBytes = try {
            val result = com.topjohnwu.superuser.Shell.cmd("blockdev --getsize64 /dev/sdb").exec()
            if (result.isSuccess) result.out[0].toLongOrNull() ?: (32L * 1_073_741_824L)
            else 32L * 1_073_741_824L
        } catch (e: Exception) {
            32L * 1_073_741_824L // 32 GB placeholder
        }

        _connectedDrive.value = UsbDriveInfo(
            device       = device,
            name         = device.productName ?: "USB Device",
            path         = "/dev/sdb",
            sizeBytes    = sizeBytes,
            isConnected  = true,
            usbVersion   = if (device.version?.startsWith("3") == true) "USB 3.0" else "USB 2.0"
        )
    }

    fun eject() {
        _connectedDrive.value = UsbDriveInfo.NONE
    }
}
