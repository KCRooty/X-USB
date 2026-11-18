package com.xusb.desktop.backend

import java.util.concurrent.TimeUnit

internal data class Res(val code: Int, val out: String, val err: String) {
    val ok get() = code == 0
    val text get() = (err.ifBlank { out }).trim().lines().lastOrNull().orEmpty()
}

/** Lanza un proceso con lista de argumentos (sin shell: no hay inyección por nombres o etiquetas). */
internal fun run(vararg cmd: String, timeoutSec: Long = 300): Res = runList(cmd.toList(), timeoutSec)

internal fun runList(cmd: List<String>, timeoutSec: Long = 300): Res {
    return try {
        val p = ProcessBuilder(cmd).start()
        p.outputStream.close()
        var err = ""
        val t = Thread { err = p.errorStream.bufferedReader().readText() }.also { it.start() }
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            return Res(124, out, "Tiempo agotado: ${cmd.first()}")
        }
        t.join(2000)
        Res(p.exitValue(), out, err)
    } catch (e: Exception) {
        Res(127, "", "No se pudo ejecutar ${cmd.firstOrNull()}: ${e.message}")
    }
}

/** Propiedad de sistema solo para pruebas: discos extra aceptados (p. ej. /dev/loop7 o el nº de un VHD). */
internal fun testDevices(): Set<String> =
    (System.getProperty("xusb.testDevices") ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
