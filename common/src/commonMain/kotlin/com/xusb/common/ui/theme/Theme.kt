package com.xusb.common.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Los datos técnicos (rutas, tamaños, UUID) van en monoespaciada: es la voz de "herramienta". */
val DataFont = FontFamily.Monospace

enum class AppTheme { FORGE, PAPER }

/** Colores de estado que Material no tiene. */
@Immutable
class StatusColors(val ok: Color, val danger: Color)

val LocalStatusColors = staticCompositionLocalOf { StatusColors(Forge.Ok, Forge.Danger) }

private val Type = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge    = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleMedium   = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    titleSmall    = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    bodyLarge     = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium    = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall     = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge    = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium   = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp),
)

// Dos radios, con función: 4 dp para etiquetas pequeñas, 8 dp para controles y paneles.
private val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

fun buildColorScheme(theme: AppTheme): ColorScheme = when (theme) {
    AppTheme.FORGE -> darkColorScheme(
        primary = Forge.Accent, onPrimary = Forge.OnAccent,
        secondary = Forge.Accent, onSecondary = Forge.OnAccent,
        background = Forge.Background, onBackground = Forge.Text,
        surface = Forge.Surface, onSurface = Forge.Text,
        surfaceVariant = Forge.SurfaceAlt, onSurfaceVariant = Forge.Muted,
        outline = Forge.Border, outlineVariant = Forge.Border,
        error = Forge.Danger, onError = Forge.OnAccent,
    )
    AppTheme.PAPER -> lightColorScheme(
        primary = Paper.Accent, onPrimary = Paper.OnAccent,
        secondary = Paper.Accent, onSecondary = Paper.OnAccent,
        background = Paper.Background, onBackground = Paper.Text,
        surface = Paper.Surface, onSurface = Paper.Text,
        surfaceVariant = Paper.SurfaceAlt, onSurfaceVariant = Paper.Muted,
        outline = Paper.Border, outlineVariant = Paper.Border,
        error = Paper.Danger, onError = Paper.OnAccent,
    )
}

@Composable
fun XUsbTheme(appTheme: AppTheme = AppTheme.FORGE, content: @Composable () -> Unit) {
    val status = if (appTheme == AppTheme.FORGE) StatusColors(Forge.Ok, Forge.Danger) else StatusColors(Paper.Ok, Paper.Danger)
    CompositionLocalProvider(LocalStatusColors provides status) {
        MaterialTheme(colorScheme = buildColorScheme(appTheme), typography = Type, shapes = Shapes, content = content)
    }
}
