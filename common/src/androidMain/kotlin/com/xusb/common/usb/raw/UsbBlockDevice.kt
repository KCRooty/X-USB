package com.xusb.common.usb.raw

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import me.jahnen.libaums.core.driver.BlockDeviceDriverFactory
import me.jahnen.libaums.core.usb.UsbCommunication
import me.jahnen.libaums.core.usb.UsbCommunicationFactory
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Acceso por sectores a un pendrive conectado por OTG, sin root.
 *
 * Habla con el dispositivo por la API de USB Host de Android (protocolo Bulk-Only de almacenamiento
 * masivo y comandos SCSI, a través de libaums). Android solo pide que el usuario conceda permiso al
 * dispositivo; no hace falta root porque la app no toca /dev/block.
 *
 * Las posiciones se expresan en bloques lógicos (LBA) del propio dispositivo.
 */
class UsbBlockDevice private constructor(
    private val comm: UsbCommunication,
    private val driver: BlockDeviceDriver,
) : Closeable, BlockIo {

    override val blockSize: Int get() = driver.blockSize
    override val blocks: Long get() = driver.blocks
    val sizeBytes: Long get() = blocks * blockSize

    /** Lee `buffer.remaining()` bytes desde el bloque [lba]. Debe ser múltiplo de [blockSize]. */
    @Throws(IOException::class)
    override fun read(lba: Long, buffer: ByteBuffer) {
        require(buffer.remaining() % blockSize == 0) { "Lectura no alineada a $blockSize bytes" }
        driver.read(lba, buffer)
    }

    /** Escribe `buffer.remaining()` bytes a partir del bloque [lba]. Debe ser múltiplo de [blockSize]. */
    @Throws(IOException::class)
    override fun write(lba: Long, buffer: ByteBuffer) {
        require(buffer.remaining() % blockSize == 0) { "Escritura no alineada a $blockSize bytes" }
        require(lba >= 0 && lba + buffer.remaining() / blockSize <= blocks) { "Escritura fuera del dispositivo" }
        driver.write(lba, buffer)
    }

    override fun close() {
        try { comm.close() } catch (_: Exception) {}
    }

    companion object {
        private const val CLASS_MASS_STORAGE = UsbConstants.USB_CLASS_MASS_STORAGE // 8
        private const val SUBCLASS_SCSI = 6
        private const val PROTOCOL_BULK_ONLY = 80

        /** ¿El dispositivo tiene una interfaz de almacenamiento masivo que sepamos manejar? */
        fun isMassStorage(device: UsbDevice): Boolean = findInterface(device) != null

        /**
         * Abre el dispositivo. El usuario ya tiene que haber concedido permiso
         * ([UsbManager.hasPermission]); si no, lanza [SecurityException].
         */
        @Throws(IOException::class)
        fun open(context: Context, device: UsbDevice, lun: Int = 0): UsbBlockDevice {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            if (!usbManager.hasPermission(device)) throw SecurityException("Sin permiso para ${device.deviceName}")
            val (iface, inEp, outEp) = findInterface(device)
                ?: throw IOException("${device.productName ?: device.deviceName} no es un dispositivo de almacenamiento USB")
            val comm = UsbCommunicationFactory.createUsbCommunication(usbManager, device, iface, inEp, outEp)
            try {
                val driver = BlockDeviceDriverFactory.createBlockDevice(comm, lun.toByte())
                driver.init()
                if (driver.blocks <= 0 || driver.blockSize <= 0) throw IOException("El dispositivo no informa de su capacidad")
                return UsbBlockDevice(comm, driver)
            } catch (e: Exception) {
                try { comm.close() } catch (_: Exception) {}
                throw if (e is IOException) e else IOException(e.message ?: "No se pudo abrir el dispositivo", e)
            }
        }

        private fun findInterface(device: UsbDevice): Triple<UsbInterface, UsbEndpoint, UsbEndpoint>? {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass != CLASS_MASS_STORAGE) continue
                if (iface.interfaceSubclass != SUBCLASS_SCSI || iface.interfaceProtocol != PROTOCOL_BULK_ONLY) continue
                var inEp: UsbEndpoint? = null
                var outEp: UsbEndpoint? = null
                for (e in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(e)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep else outEp = ep
                }
                if (inEp != null && outEp != null) return Triple(iface, inEp, outEp)
            }
            return null
        }
    }
}
