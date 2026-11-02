package io.github.mikhasrahulia.pixelclock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Expressive type: heavier weights and no tiny sizes.
private fun style(weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp
)

val Typography = Typography(
    displayLarge = style(FontWeight.ExtraBold, 72, 76, -1.5),
    displayMedium = style(FontWeight.Bold, 52, 56, -0.5),
    displaySmall = style(FontWeight.Bold, 40, 46),
    headlineLarge = style(FontWeight.ExtraBold, 36, 42),
    headlineMedium = style(FontWeight.Bold, 30, 38),
    headlineSmall = style(FontWeight.Bold, 26, 34),
    titleLarge = style(FontWeight.Bold, 24, 30),
    titleMedium = style(FontWeight.SemiBold, 18, 26, 0.1),
    titleSmall = style(FontWeight.SemiBold, 16, 22, 0.1),
    bodyLarge = style(FontWeight.Medium, 18, 26, 0.2),
    bodyMedium = style(FontWeight.Medium, 16, 23, 0.2),
    bodySmall = style(FontWeight.Medium, 15, 21, 0.2),
    labelLarge = style(FontWeight.Bold, 16, 22, 0.1),
    labelMedium = style(FontWeight.SemiBold, 15, 20, 0.2),
    labelSmall = style(FontWeight.SemiBold, 14, 18, 0.3)
)
