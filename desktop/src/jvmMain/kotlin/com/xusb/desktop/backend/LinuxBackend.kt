package com.xusb.desktop.backend

import com.xusb.common.usb.ShellBackend
import com.xusb.common.usb.ShellExec
import com.xusb.common.usb.ShellResult
import java.util.concurrent.TimeUnit

/** Ejecuta órdenes con sh; si no somos root, las eleva con pkexec. */
internal class ProcessShell : ShellExec {
    private val uid: Int by lazy { runCatching { ProcessBuilder("id", "-u").start().inputStream.bufferedReader().readText().trim().toInt() }.getOrDefault(1000) }
    override val isRoot get() = uid == 0

    override fun exec(cmd: String, timeoutSec: Long, onChunk: ((String) -> Unit)?): ShellResult {
        val argv = if (isRoot) listOf("sh", "-c", cmd) else listOf("pkexec", "sh", "-c", cmd)
        return try {
            val p = ProcessBuilder(argv).start()
            p.outputStream.close()
            val out = StringBuilder()
            val outT = Thread {
                p.inputStream.bufferedReader().useLines { it.forEach { l -> out.appendLine(l); onChunk?.invoke(l) } }
            }.also { it.start() }
            val err = StringBuilder()
            val sb = StringBuilder()
            val r = p.errorStream.reader()
            while (true) {
                val c = r.read()
                if (c < 0) break
                if (c == '\r'.code || c == '\n'.code) {
                    if (sb.isNotEmpty()) { onChunk?.invoke(sb.toString()); err.appendLine(sb); sb.setLength(0) }
                } else sb.append(c.toChar())
            }
            if (sb.isNotEmpty()) { onChunk?.invoke(sb.toString()); err.appendLine(sb) }
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) { p.destroyForcibly(); return ShellResult(124, out.toString(), "Tiempo agotado") }
            outT.join(2000)
            ShellResult(p.exitValue(), out.toString(), err.toString())
        } catch (e: Exception) {
            ShellResult(127, "", "No se pudo ejecutar: ${e.message}")
        }
    }
}

/**
 * Linux de escritorio. Toda la lógica vive en ShellBackend (compartida con Android);
 * aquí solo se elige cómo ejecutar órdenes. Las pruebas añaden loops con -Dxusb.testDevices.
 */
class LinuxBackend : ShellBackend(ProcessShell(), "Linux", { testDevices() })
