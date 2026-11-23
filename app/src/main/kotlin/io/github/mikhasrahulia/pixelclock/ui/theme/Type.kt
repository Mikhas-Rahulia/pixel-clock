package io.github.mikhasrahulia.pixelclock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Material 3 Expressive typography: bold, glanceable tabular time scales paired with clean UI hierarchy.
private fun style(
    weight: FontWeight,
    size: Int,
    line: Int,
    tracking: Double = 0.0,
    tabular: Boolean = false
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = if (tabular) "tnum" else null
)

val Typography = Typography(
    displayLarge = style(FontWeight.Bold, 64, 68, -1.5, tabular = true),
    displayMedium = style(FontWeight.Bold, 52, 56, -1.0, tabular = true),
    displaySmall = style(FontWeight.Bold, 36, 42, -0.5, tabular = true),
    headlineLarge = style(FontWeight.Bold, 34, 40, -0.4),
    headlineMedium = style(FontWeight.Bold, 28, 34, -0.2),
    headlineSmall = style(FontWeight.SemiBold, 24, 30),
    titleLarge = style(FontWeight.Bold, 22, 28),
    titleMedium = style(FontWeight.SemiBold, 17, 24, 0.1),
    titleSmall = style(FontWeight.SemiBold, 15, 20, 0.1),
    bodyLarge = style(FontWeight.Medium, 16, 24, 0.15),
    bodyMedium = style(FontWeight.Normal, 14, 20, 0.2),
    bodySmall = style(FontWeight.Medium, 13, 18, 0.2),
    labelLarge = style(FontWeight.SemiBold, 15, 20, 0.1),
    labelMedium = style(FontWeight.SemiBold, 13, 18, 0.25),
    labelSmall = style(FontWeight.SemiBold, 12, 16, 0.3)
)
