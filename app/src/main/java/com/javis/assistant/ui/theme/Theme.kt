package com.javis.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val JavisBlue = Color(0xFF00D4FF)
val JavisBlueDark = Color(0xFF0099BB)
val JavisBlueLight = Color(0xFF80EAFF)
val JavisBackground = Color(0xFF050A0F)
val JavisSurface = Color(0xFF0D1A26)
val JavisSurfaceVariant = Color(0xFF112233)
val JavisOnSurface = Color(0xFFE0F4FF)
val JavisOnSurfaceVariant = Color(0xFF8ABCCC)
val JavisError = Color(0xFFFF4D6D)
val JavisSuccess = Color(0xFF00E5A0)
val JavisWarning = Color(0xFFFFB300)
val JavisDivider = Color(0xFF1A3040)

private val DarkColorScheme = darkColorScheme(
    primary = JavisBlue,
    onPrimary = Color(0xFF001820),
    primaryContainer = Color(0xFF003344),
    onPrimaryContainer = JavisBlueLight,
    secondary = JavisBlueDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF002233),
    onSecondaryContainer = JavisBlueLight,
    background = JavisBackground,
    onBackground = JavisOnSurface,
    surface = JavisSurface,
    onSurface = JavisOnSurface,
    surfaceVariant = JavisSurfaceVariant,
    onSurfaceVariant = JavisOnSurfaceVariant,
    error = JavisError,
    onError = Color.White,
    outline = JavisDivider
)

@Composable
fun JavisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = JavisTypography,
        content = content
    )
}
