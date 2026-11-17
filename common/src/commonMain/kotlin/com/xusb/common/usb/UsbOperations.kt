package com.xusb.common.usb

import kotlinx.coroutines.flow.Flow

// Las pantallas llaman a estas funciones; cada una delega en el backend de la plataforma activa.
// (Antes eran simulacros con delay(2000): ya no queda ninguno.)

private val backend: UsbBackend get() = UsbPlatform.backend

fun formatUsb(device: String, fs: Filesystem, label: String = "X-USB", clusterSize: Int = 0): Flow<OperationProgress> =
    backend.format(device, fs, label, clusterSize)

fun wipeUsb(device: String): Flow<OperationProgress> = backend.wipe(device)

fun rawWrite(isoPath: String, device: String): Flow<OperationProgress> = backend.rawWrite(isoPath, device)

fun installVentoy(
    context: Any?,
    device: String,
    useGpt: Boolean = false,
    secureBoot: Boolean = false,
    update: Boolean = false,
): Flow<OperationProgress> = backend.installVentoy(context, device, useGpt, secureBoot, update)

fun backupUsb(device: String, imagePath: String): Flow<OperationProgress> = backend.backup(device, imagePath)

fun restoreUsb(imagePath: String, device: String): Flow<OperationProgress> = backend.restore(imagePath, device)

fun downloadIsoToUsb(url: String, destPath: String): Flow<OperationProgress> = backend.downloadFile(url, destPath)

fun convertBinToIso(binPath: String, isoPath: String): Flow<OperationProgress> = backend.binToIso(binPath, isoPath)

fun listPartitions(device: String): List<PartitionInfo> = backend.listPartitions(device)

fun getPartitionTableType(device: String): String = backend.partitionTableType(device)

fun convertPartitionTable(device: String, toGpt: Boolean): Flow<OperationProgress> =
    backend.convertPartitionTable(device, toGpt)

fun createPartition(device: String, fs: Filesystem): Flow<OperationProgress> = backend.createPartition(device, fs)

fun deletePartition(device: String, partNum: Int): Flow<OperationProgress> = backend.deletePartition(device, partNum)

fun partitionPath(device: String, n: Int): String = backend.partitionPath(device, n)

fun mountPartition(partition: String, mountPoint: String): Boolean = backend.mountPartition(partition, mountPoint)

fun unmountPartition(mountPoint: String): Boolean = backend.unmountPartition(mountPoint)
