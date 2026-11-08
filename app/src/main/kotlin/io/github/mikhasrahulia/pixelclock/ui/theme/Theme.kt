package io.github.mikhasrahulia.pixelclock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Fixed "sunset" palette: coral primary, amber secondary, violet tertiary.
 * Wallpaper colours are not used so the app never turns muddy or green.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB4A2),
    onPrimary = Color(0xFF561E10),
    primaryContainer = Color(0xFF7A3022),
    onPrimaryContainer = Color(0xFFFFDAD2),
    inversePrimary = Color(0xFF9A4538),
    secondary = Color(0xFFF5C76B),
    onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFF4A3F52),
    onSecondaryContainer = Color(0xFFF0DCFA),
    tertiary = Color(0xFFCDBDFF),
    onTertiary = Color(0xFF342868),
    tertiaryContainer = Color(0xFF4B3F80),
    onTertiaryContainer = Color(0xFFE8DEFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE8E0E8),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE8E0E8),
    surfaceVariant = Color(0xFF4A444D),
    onSurfaceVariant = Color(0xFFCCC4CE),
    surfaceTint = Color(0xFFFFB4A2),
    inverseSurface = Color(0xFFE8E0E8),
    inverseOnSurface = Color(0xFF322F36),
    outline = Color(0xFF958E98),
    outlineVariant = Color(0xFF4A444D),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B383F),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF141318),
    surfaceContainer = Color(0xFF1E1D24),
    surfaceContainerHigh = Color(0xFF28272F),
    surfaceContainerHighest = Color(0xFF33323B)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF9A4538),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF3C0A02),
    inversePrimary = Color(0xFFFFB4A2),
    secondary = Color(0xFF7A5900),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF2DDFF),
    onSecondaryContainer = Color(0xFF2A1736),
    tertiary = Color(0xFF65558F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8DEFF),
    onTertiaryContainer = Color(0xFF201047),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1D1B1F),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1D1B1F),
    surfaceVariant = Color(0xFFE8E0EB),
    onSurfaceVariant = Color(0xFF4A454E),
    surfaceTint = Color(0xFF9A4538),
    inverseSurface = Color(0xFF322F34),
    inverseOnSurface = Color(0xFFF5EFF4),
    outline = Color(0xFF7B757F),
    outlineVariant = Color(0xFFCCC4CF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFBFF),
    surfaceDim = Color(0xFFE0D8E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F2F9),
    surfaceContainer = Color(0xFFF2ECF3),
    surfaceContainerHigh = Color(0xFFECE6EE),
    surfaceContainerHighest = Color(0xFFE6E0E8)
)

@Composable
fun PixelClockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
