package com.xusb.common.usb

import kotlinx.coroutines.flow.*

// Versión simplificada para que compile sin expect/actual por ahora
fun formatUsb(device: String, fs: Filesystem, label: String) = flow<OperationProgress> {
    emit(OperationProgress(isRunning = true, label = "Formatting..."))
    kotlinx.coroutines.delay(2000)
    emit(OperationProgress(isRunning = false, label = "Complete ✓", isComplete = true))
}

fun wipeUsb(device: String) = flow<OperationProgress> {
    emit(OperationProgress(isRunning = true, label = "Wiping..."))
    kotlinx.coroutines.delay(2000)
    emit(OperationProgress(isRunning = false, label = "Complete ✓", isComplete = true))
}

fun rawWrite(isoPath: String, device: String) = flow<OperationProgress> {
    emit(OperationProgress(isRunning = true, label = "Writing..."))
    kotlinx.coroutines.delay(2000)
    emit(OperationProgress(isRunning = false, label = "Complete ✓", isComplete = true))
}

fun installVentoy(context: Any?, device: String) = flow<OperationProgress> {
    emit(OperationProgress(isRunning = true, label = "Installing..."))
    kotlinx.coroutines.delay(2000)
    emit(OperationProgress(isRunning = false, label = "Complete ✓", isComplete = true))
}

fun backupUsb(device: String, imagePath: String) = flow<OperationProgress> {
    emit(OperationProgress(isRunning = true, label = "Backing up..."))
    kotlinx.coroutines.delay(2000)
    emit(OperationProgress(isRunning = false, label = "Complete ✓", isComplete = true))
}

fun downloadIsoToUsb(url: String, destPath: String) = flow<OperationProgress> {
    emit(OperationProgress(isRunning = true, label = "Downloading..."))
    kotlinx.coroutines.delay(2000)
    emit(OperationProgress(isRunning = false, label = "Complete ✓", isComplete = true))
}

fun listPartitions(device: String): List<PartitionInfo> = listOf(
    PartitionInfo(1, "1MB", "100%", "32GB", "FAT32", "boot")
)

fun mountPartition(partition: String, mountPoint: String) = true
