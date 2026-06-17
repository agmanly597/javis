package com.javis.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CyanAccent = Color(0xFF00D4FF)
val BlueAccent = Color(0xFF0066FF)
val DarkBg = Color(0xFF050A0F)
val SurfaceDark = Color(0xFF0D1421)
val SurfaceMid = Color(0xFF131D2E)
val TextPrimary = Color(0xFFE8F4FD)
val TextSecondary = Color(0xFF7B9AB2)
val ErrorRed = Color(0xFFFF4D6A)
val SuccessGreen = Color(0xFF00E5B0)

private val JavisColorScheme = darkColorScheme(
    primary = CyanAccent,
    secondary = BlueAccent,
    tertiary = SuccessGreen,
    background = DarkBg,
    surface = SurfaceDark,
    surfaceVariant = SurfaceMid,
    onPrimary = DarkBg,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed
)

@Composable
fun JavisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JavisColorScheme,
        typography = JavisTypography,
        content = content
    )
}
