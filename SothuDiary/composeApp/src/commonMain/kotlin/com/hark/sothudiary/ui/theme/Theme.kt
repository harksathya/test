package com.hark.sothudiary.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.data.DealStatus
import com.hark.sothudiary.domain.pricing.Advice
import com.hark.sothudiary.domain.pricing.Verdict

val Blue = Color(0xFF0B5CAD)
val BlueDark = Color(0xFF07457F)
val BlueLight = Color(0xFFE3EEFA)
val Teal = Color(0xFF00897B)
val Amber = Color(0xFFF59E0B)
val Ink = Color(0xFF1B2733)
val Muted = Color(0xFF5F6B7A)
val Canvas = Color(0xFFF3F6FA)

private val colors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = BlueLight,
    onPrimaryContainer = BlueDark,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F2EE),
    onSecondaryContainer = Color(0xFF00463E),
    tertiary = Amber,
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDF1F6),
    onSurfaceVariant = Muted,
    outline = Color(0xFFCBD3DD),
    outlineVariant = Color(0xFFE2E7EE),
    error = Color(0xFFC62828),
)

private val shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun SothuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, shapes = shapes, content = content)
}

fun verdictColor(v: Verdict): Color = when (v) {
    Verdict.SUSPICIOUS_LOW -> Color(0xFFAD1457)
    Verdict.GOOD -> Color(0xFF00796B)
    Verdict.FAIR -> Color(0xFF2E7D32)
    Verdict.HIGH -> Color(0xFFE65100)
    Verdict.TOO_HIGH -> Color(0xFFC62828)
    Verdict.UNKNOWN -> Color(0xFF6B7785)
}

fun statusColor(s: DealStatus): Color = when (s) {
    DealStatus.NEW -> Color(0xFF1565C0)
    DealStatus.CONTACTED -> Color(0xFF5E35B1)
    DealStatus.SITE_VISIT -> Color(0xFF00838F)
    DealStatus.NEGOTIATING -> Color(0xFFB26A00)
    DealStatus.BOUGHT -> Color(0xFF2E7D32)
    DealStatus.DROPPED -> Color(0xFF757575)
}

fun adviceColor(a: Advice): Color = when (a) {
    Advice.GO -> Color(0xFF2E7D32)
    Advice.HOLD -> Color(0xFFB26A00)
    Advice.DROP -> Color(0xFFC62828)
}
