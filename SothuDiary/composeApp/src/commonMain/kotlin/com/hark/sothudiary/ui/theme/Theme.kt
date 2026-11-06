package com.hark.sothudiary.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.sothudiary.data.DealStatus
import com.hark.sothudiary.data.ThemeMode
import com.hark.sothudiary.domain.pricing.Advice
import com.hark.sothudiary.domain.pricing.Verdict

// v2.1.1 palette: warm neutral surfaces, one restrained accent (deep teal-green), soft elevation.
val Accent = Color(0xFF1F5F57)
val AccentDark = Color(0xFF8FCFC4)
val Ink = Color(0xFF1D1C1A)
val Muted = Color(0xFF68655E)
val Canvas = Color(0xFFF6F5F2)
// Kept for older call sites.
val Blue = Accent
val Teal = Accent
val Amber = Color(0xFFA8680F)

private val lightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2EEEB),
    onPrimaryContainer = Color(0xFF0E3A34),
    secondary = Color(0xFF4C5A5E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EBEB),
    onSecondaryContainer = Color(0xFF243033),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5EBDA),
    onTertiaryContainer = Color(0xFF4A2F05),
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0EEEA),
    onSurfaceVariant = Muted,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFAF8),
    surfaceContainer = Color(0xFFF7F6F3),
    surfaceContainerHigh = Color(0xFFF1EFEB),
    surfaceContainerHighest = Color(0xFFEBE9E4),
    outline = Color(0xFFD5D2CB),
    outlineVariant = Color(0xFFEAE7E1),
    error = Color(0xFFB3261E),
)

private val darkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF00372F),
    primaryContainer = Color(0xFF1E3B37),
    onPrimaryContainer = Color(0xFFCBE9E3),
    secondary = Color(0xFFB4C1C3),
    onSecondary = Color(0xFF1F2A2C),
    secondaryContainer = Color(0xFF2C3537),
    onSecondaryContainer = Color(0xFFD8E2E3),
    tertiary = Color(0xFFE2B36A),
    onTertiary = Color(0xFF3D2700),
    tertiaryContainer = Color(0xFF3B2E19),
    onTertiaryContainer = Color(0xFFF6DDB5),
    background = Color(0xFF111312),
    onBackground = Color(0xFFE7E5E0),
    surface = Color(0xFF1A1C1B),
    onSurface = Color(0xFFE7E5E0),
    surfaceVariant = Color(0xFF242726),
    onSurfaceVariant = Color(0xFFA9A59C),
    surfaceTint = Color(0xFF9C9A94),
    surfaceContainerLowest = Color(0xFF0D0F0E),
    surfaceContainerLow = Color(0xFF171918),
    surfaceContainer = Color(0xFF1D1F1E),
    surfaceContainerHigh = Color(0xFF252827),
    surfaceContainerHighest = Color(0xFF2E3130),
    outline = Color(0xFF3B3F3D),
    outlineVariant = Color(0xFF2A2D2C),
    error = Color(0xFFF2B8B5),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val base = Typography()
private val typography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyMedium = base.bodyMedium.copy(lineHeight = 20.sp),
)

/** Extra brand colours that are not part of the Material scheme. */
data class BrandColors(
    val bar: Color,
    val onBar: Color,
    /** Home header background (calm, no gradient). */
    val hero: Color,
    val dark: Boolean,
    val warnText: Color,
)

val LocalBrand = staticCompositionLocalOf {
    BrandColors(Color.White, Ink, Color.White, false, Color(0xFF8A5A00))
}

@Composable
fun SothuTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val brand = if (dark) {
        BrandColors(Color(0xFF161817), Color(0xFFE7E5E0), Color(0xFF1A1C1B), true, Color(0xFFE2B36A))
    } else {
        BrandColors(Canvas, Ink, Color.White, false, Color(0xFF8A5A00))
    }
    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(colorScheme = if (dark) darkColors else lightColors, shapes = shapes, typography = typography) {
            // Text drawn directly on the background (outside a Surface) must follow the theme too.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
        }
    }
}

fun verdictColor(v: Verdict): Color = when (v) {
    Verdict.SUSPICIOUS_LOW -> Color(0xFFA3345C)
    Verdict.GOOD -> Color(0xFF2B7A70)
    Verdict.FAIR -> Color(0xFF3D7A45)
    Verdict.HIGH -> Color(0xFFB8641C)
    Verdict.TOO_HIGH -> Color(0xFFB0352B)
    Verdict.UNKNOWN -> Color(0xFF6B7785)
}

fun verdictArgb(v: Verdict): Long = when (v) {
    Verdict.SUSPICIOUS_LOW -> 0xFFA3345C
    Verdict.GOOD -> 0xFF2B7A70
    Verdict.FAIR -> 0xFF3D7A45
    Verdict.HIGH -> 0xFFB8641C
    Verdict.TOO_HIGH -> 0xFFB0352B
    Verdict.UNKNOWN -> 0xFF6B7785
}

fun statusColor(s: DealStatus): Color = when (s) {
    DealStatus.NEW -> Color(0xFF3F6C9A)
    DealStatus.CONTACTED -> Color(0xFF6F5E9A)
    DealStatus.SITE_VISIT -> Color(0xFF2F7A78)
    DealStatus.NEGOTIATING -> Color(0xFFB0700E)
    DealStatus.BOUGHT -> Color(0xFF3D7A45)
    DealStatus.DROPPED -> Color(0xFF8A8780)
}

fun adviceColor(a: Advice): Color = when (a) {
    Advice.GO -> Color(0xFF3D7A45)
    Advice.HOLD -> Color(0xFFB8741A)
    Advice.DROP -> Color(0xFFB0352B)
}

/** A semantic colour made readable as text on the current surface (lighter tint in dark theme). */
@Composable
fun readable(c: Color): Color = if (LocalBrand.current.dark) androidx.compose.ui.graphics.lerp(c, Color.White, 0.5f) else c

@Suppress("unused")
private val unusedTextStyle = TextStyle.Default
