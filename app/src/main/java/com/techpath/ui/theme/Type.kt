package com.techpath.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

// Start from Material 3 defaults; set a custom FontFamily here to change the app's typeface.
private val AppFont = FontFamily.Default
private val base = Typography()

val AppTypography = Typography(
    headlineSmall = base.headlineSmall.copy(fontFamily = AppFont),
    titleLarge = base.titleLarge.copy(fontFamily = AppFont),
    titleMedium = base.titleMedium.copy(fontFamily = AppFont),
    titleSmall = base.titleSmall.copy(fontFamily = AppFont),
    bodyLarge = base.bodyLarge.copy(fontFamily = AppFont),
    bodyMedium = base.bodyMedium.copy(fontFamily = AppFont),
    bodySmall = base.bodySmall.copy(fontFamily = AppFont),
    labelLarge = base.labelLarge.copy(fontFamily = AppFont),
    labelMedium = base.labelMedium.copy(fontFamily = AppFont),
    labelSmall = base.labelSmall.copy(fontFamily = AppFont),
)
