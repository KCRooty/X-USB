package com.xusb.common.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Identidad "forja": grafito cálido, un solo acento (brasa) y colores de estado aparte.
 * El acento solo marca lo importante: la acción principal y el elemento activo.
 * Contrastes comprobados: texto ≥ 12:1, texto atenuado ≥ 5:1, acento sobre fondo ≥ 5:1.
 */
object Forge {
    val Background = Color(0xFF121110)
    val Surface    = Color(0xFF1A1917)
    val SurfaceAlt = Color(0xFF22201D)
    val Border     = Color(0xFF38342F)
    val Text       = Color(0xFFEDE8E0)
    val Muted      = Color(0xFF9A938A)
    val Accent     = Color(0xFFFF6B2C)
    val OnAccent   = Color(0xFF1A0E08)
    val Danger     = Color(0xFFEF5A5F)
    val Ok         = Color(0xFF5BC28A)
}

object Paper {
    val Background = Color(0xFFF4F0E8)
    val Surface    = Color(0xFFFBF9F4)
    val SurfaceAlt = Color(0xFFEAE4D8)
    val Border     = Color(0xFFD3CBBB)
    val Text       = Color(0xFF1D1A16)
    val Muted      = Color(0xFF6B645A)
    val Accent     = Color(0xFFB93A0C)
    val OnAccent   = Color(0xFFFFFFFF)
    val Danger     = Color(0xFFB42318)
    val Ok         = Color(0xFF1F7A4D)
}
