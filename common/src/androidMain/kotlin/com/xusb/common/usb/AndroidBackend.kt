package com.xusb.common.usb

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import java.util.concurrent.Executor

/** Shell root de Android vía libsu (el usuario debe conceder root a la app). */
class LibsuShell : ShellExec {
    init {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(15))
    }

    override val isRoot: Boolean get() = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    override val canElevate: Boolean get() = isRoot

    override fun exec(cmd: String, timeoutSec: Long, onChunk: ((String) -> Unit)?): ShellResult {
        val out = StringBuilder()
        val err = StringBuilder()
        val inline = Executor { it.run() }
        val outCb = object : CallbackList<String>(inline) {
            override fun onAddElement(s: String) { synchronized(out) { out.appendLine(s) }; onChunk?.invoke(s) }
        }
        val errCb = object : CallbackList<String>(inline) {
            override fun onAddElement(s: String) { synchronized(err) { err.appendLine(s) }; onChunk?.invoke(s) }
        }
        return try {
            val r = Shell.cmd(cmd).to(outCb, errCb).exec()
            ShellResult(r.code, out.toString(), err.toString())
        } catch (e: Exception) {
            ShellResult(127, "", "No se pudo ejecutar: ${e.message}")
        }
    }
}

/**
 * Android: misma lógica que el escritorio de Linux (ShellBackend) ejecutada como root.
 * Requiere root y las herramientas parted/sgdisk/mkfs.* en el dispositivo (no vienen de serie en Android).
 */
class AndroidBackend : ShellBackend(LibsuShell(), "Android")
