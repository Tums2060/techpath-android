package com.techpath.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A fixed palette (no dynamic colour) so every study participant sees the same UI.
private val Light = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2CF),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4C6358),
    secondaryContainer = Color(0xFFCEE9DA),
    onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF8A5100),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFF6FBF8),
    surface = Color(0xFFF6FBF8),
    surfaceVariant = Color(0xFFDBE5DE),
    surfaceContainer = Color(0xFFEAF0EC),
    surfaceContainerHigh = Color(0xFFE4EAE6),
    outline = Color(0xFF707973),
    error = Color(0xFFBA1A1A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AD6B3),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFFA6F2CF),
    secondary = Color(0xFFB3CCBF),
    secondaryContainer = Color(0xFF354B41),
    onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFFFB870),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDCBE),
    background = Color(0xFF101513),
    surface = Color(0xFF101513),
    surfaceVariant = Color(0xFF404943),
    surfaceContainer = Color(0xFF1C211F),
    surfaceContainerHigh = Color(0xFF262B29),
    outline = Color(0xFF8A938D),
    error = Color(0xFFFFB4AB),
)

@Composable
fun TechPathTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(), content = content)
}
