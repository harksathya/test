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

// Brand palette – a property-portal blue with a teal accent.
val Blue = Color(0xFF0B5CAD)
val BlueDark = Color(0xFF07457F)
val BlueLight = Color(0xFFE3EEFA)
val Teal = Color(0xFF00897B)
val Amber = Color(0xFFF59E0B)
val Ink = Color(0xFF1B2733)
val Muted = Color(0xFF5F6B7A)
val Canvas = Color(0xFFF3F6FA)

private val lightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = BlueLight,
    onPrimaryContainer = BlueDark,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F2EE),
    onSecondaryContainer = Color(0xFF00463E),
    tertiary = Amber,
    tertiaryContainer = Color(0xFFFFF1D6),
    onTertiaryContainer = Color(0xFF5C3B00),
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDF1F6),
    onSurfaceVariant = Muted,
    surfaceContainer = Color(0xFFF7F9FC),
    surfaceContainerHigh = Color(0xFFEFF3F8),
    outline = Color(0xFFCBD3DD),
    outlineVariant = Color(0xFFE2E7EE),
    error = Color(0xFFC62828),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFF8CC0FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF12406E),
    onPrimaryContainer = Color(0xFFD5E6FF),
    secondary = Color(0xFF5FD4C3),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0B4F45),
    onSecondaryContainer = Color(0xFFB9F2E8),
    tertiary = Color(0xFFFFC266),
    tertiaryContainer = Color(0xFF4A3300),
    onTertiaryContainer = Color(0xFFFFE2B0),
    background = Color(0xFF0E141B),
    onBackground = Color(0xFFE1E6ED),
    surface = Color(0xFF161E27),
    onSurface = Color(0xFFE1E6ED),
    surfaceVariant = Color(0xFF222C38),
    onSurfaceVariant = Color(0xFFA9B4C2),
    surfaceContainer = Color(0xFF1A232D),
    surfaceContainerHigh = Color(0xFF212B36),
    outline = Color(0xFF3A4654),
    outlineVariant = Color(0xFF2A3440),
    error = Color(0xFFFF8A80),
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
    val heroGradient: Brush,
    val dark: Boolean,
    val warnText: Color,
)

val LocalBrand = staticCompositionLocalOf {
    BrandColors(Blue, Color.White, Brush.linearGradient(listOf(Blue, Teal)), false, Color(0xFF8A5A00))
}

@Composable
fun SothuTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val brand = if (dark) {
        BrandColors(Color(0xFF13304D), Color.White, Brush.linearGradient(listOf(Color(0xFF13304D), Color(0xFF0B4F45))), true, Color(0xFFFFC266))
    } else {
        BrandColors(Blue, Color.White, Brush.linearGradient(listOf(Blue, Color(0xFF0A7C8C))), false, Color(0xFF8A5A00))
    }
    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(colorScheme = if (dark) darkColors else lightColors, shapes = shapes, typography = typography) {
            // Text drawn directly on the background (outside a Surface) must follow the theme too.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
        }
    }
}

fun verdictColor(v: Verdict): Color = when (v) {
    Verdict.SUSPICIOUS_LOW -> Color(0xFFAD1457)
    Verdict.GOOD -> Color(0xFF00897B)
    Verdict.FAIR -> Color(0xFF2E7D32)
    Verdict.HIGH -> Color(0xFFE65100)
    Verdict.TOO_HIGH -> Color(0xFFC62828)
    Verdict.UNKNOWN -> Color(0xFF6B7785)
}

fun verdictArgb(v: Verdict): Long = when (v) {
    Verdict.SUSPICIOUS_LOW -> 0xFFAD1457
    Verdict.GOOD -> 0xFF00897B
    Verdict.FAIR -> 0xFF2E7D32
    Verdict.HIGH -> 0xFFE65100
    Verdict.TOO_HIGH -> 0xFFC62828
    Verdict.UNKNOWN -> 0xFF6B7785
}

fun statusColor(s: DealStatus): Color = when (s) {
    DealStatus.NEW -> Color(0xFF1E88E5)
    DealStatus.CONTACTED -> Color(0xFF7E57C2)
    DealStatus.SITE_VISIT -> Color(0xFF00838F)
    DealStatus.NEGOTIATING -> Color(0xFFEF8F00)
    DealStatus.BOUGHT -> Color(0xFF2E7D32)
    DealStatus.DROPPED -> Color(0xFF8A939E)
}

fun adviceColor(a: Advice): Color = when (a) {
    Advice.GO -> Color(0xFF2E7D32)
    Advice.HOLD -> Color(0xFFEF8F00)
    Advice.DROP -> Color(0xFFC62828)
}

@Suppress("unused")
private val unusedTextStyle = TextStyle.Default
