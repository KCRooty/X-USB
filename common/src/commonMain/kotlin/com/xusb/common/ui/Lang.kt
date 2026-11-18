package com.xusb.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

val LocalSpanish = compositionLocalOf { true }

/** Texto en el idioma activo: `tr("Formatear", "Format")`. */
@Composable
fun tr(es: String, en: String): String = if (LocalSpanish.current) es else en
