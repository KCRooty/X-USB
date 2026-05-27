package com.xusb.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.xusb.R

// ── App Typography ────────────────────────────────────────────────────────
val OrbitronFamily = FontFamily.Default
val Exo2Family = FontFamily.Default

// ── App Typography ────────────────────────────────────────────────────────
val XUsbTypography = Typography(
    displayLarge  = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold,      fontSize = 26.sp),
    titleLarge    = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold,      fontSize = 16.sp, letterSpacing = 1.sp),
    titleMedium   = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold,      fontSize = 12.sp, letterSpacing = 2.sp),
    titleSmall    = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold,      fontSize = 9.sp,  letterSpacing = 3.sp),
    bodyLarge     = TextStyle(fontFamily = Exo2Family,     fontWeight = FontWeight.Normal,    fontSize = 15.sp),
    bodyMedium    = TextStyle(fontFamily = Exo2Family,     fontWeight = FontWeight.Normal,    fontSize = 13.sp),
    bodySmall     = TextStyle(fontFamily = Exo2Family,     fontWeight = FontWeight.Light,     fontSize = 11.sp),
    labelLarge    = TextStyle(fontFamily = Exo2Family,     fontWeight = FontWeight.SemiBold,  fontSize = 13.sp),
    labelSmall    = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold,      fontSize = 8.sp, letterSpacing = 2.sp),
)

// ── Theme Enum ────────────────────────────────────────────────────────────
enum class AppTheme { DARK_PURPLE, LIGHT, BLUE_YELLOW }

// ── Theme State ───────────────────────────────────────────────────────────
val LocalAppTheme = staticCompositionLocalOf { AppTheme.DARK_PURPLE }

fun buildColorScheme(theme: AppTheme): ColorScheme {
    return when (theme) {
        AppTheme.DARK_PURPLE -> darkColorScheme(
            primary          = DarkPurple.Primary,
            onPrimary        = DarkPurple.OnPrimary,
            secondary        = DarkPurple.Accent,
            background       = DarkPurple.Background,
            onBackground     = DarkPurple.OnBackground,
            surface          = DarkPurple.Surface,
            onSurface        = DarkPurple.OnSurface,
            surfaceVariant   = DarkPurple.SurfaceAlt,
            onSurfaceVariant = DarkPurple.Muted,
            outline          = DarkPurple.Border,
            error            = DarkPurple.Error,
        )
        AppTheme.LIGHT -> lightColorScheme(
            primary          = LightTheme.Primary,
            onPrimary        = LightTheme.OnPrimary,
            secondary        = LightTheme.Accent,
            background       = LightTheme.Background,
            onBackground     = LightTheme.OnBackground,
            surface          = LightTheme.Surface,
            onSurface        = LightTheme.OnSurface,
            surfaceVariant   = LightTheme.SurfaceAlt,
            onSurfaceVariant = LightTheme.Muted,
            outline          = LightTheme.Border,
            error            = LightTheme.Error,
        )
        AppTheme.BLUE_YELLOW -> darkColorScheme(
            primary          = BluYellow.Primary,
            onPrimary        = BluYellow.OnPrimary,
            secondary        = BluYellow.Accent,
            background       = BluYellow.Background,
            onBackground     = BluYellow.OnBackground,
            surface          = BluYellow.Surface,
            onSurface        = BluYellow.OnSurface,
            surfaceVariant   = BluYellow.SurfaceAlt,
            onSurfaceVariant = BluYellow.Muted,
            outline          = BluYellow.Border,
            error            = BluYellow.Error,
        )
    }
}

@Composable
fun XUsbTheme(
    appTheme: AppTheme = AppTheme.DARK_PURPLE,
    content: @Composable () -> Unit
) {
    val colorScheme = buildColorScheme(appTheme)
    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = XUsbTypography,
            content = content
        )
    }
}
