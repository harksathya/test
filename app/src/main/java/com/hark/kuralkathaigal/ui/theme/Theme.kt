package com.hark.kuralkathaigal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palette taken from the Read.io reference: soft grey canvas, white cards, coral-orange accent.
val Accent = Color(0xFFFF5A36)
val AccentSoft = Color(0xFFFFE7DF)
val Canvas = Color(0xFFF6F6F8)
val CardWhite = Color(0xFFFFFFFF)
val Ink = Color(0xFF1C1C28)
val InkSoft = Color(0xFF5B5B6B)
val Muted = Color(0xFF9A9AA8)
val Chip = Color(0xFFF0F0F3)
val Highlight = Color(0xFFFFD9CE)

private val colors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = Chip,
    onSurfaceVariant = InkSoft,
)

private val typography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 28.sp, color = Ink),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = InkSoft),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, color = Muted),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

@Composable
fun KuralTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
