package com.techpath.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette. Change these (or the schemes below) to restyle the whole app.
private val Teal40 = Color(0xFF006A6A)
private val Teal80 = Color(0xFF4CDADA)
private val Teal90 = Color(0xFF6FF7F6)
private val Teal20 = Color(0xFF003737)
private val Slate40 = Color(0xFF4A6363)
private val Slate80 = Color(0xFFB0CCCB)
private val Slate90 = Color(0xFFCCE8E7)
private val Amber40 = Color(0xFF7C5800)
private val Amber80 = Color(0xFFFFBA20)
private val Amber90 = Color(0xFFFFDEA8)

val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal20,
    secondary = Slate40,
    secondaryContainer = Slate90,
    tertiary = Amber40,
    tertiaryContainer = Amber90,
    background = Color(0xFFFAFDFC),
    surface = Color(0xFFFAFDFC),
)

val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal20,
    primaryContainer = Color(0xFF004F4F),
    onPrimaryContainer = Teal90,
    secondary = Slate80,
    secondaryContainer = Color(0xFF324B4B),
    tertiary = Amber80,
    tertiaryContainer = Color(0xFF5E4200),
    background = Color(0xFF191C1C),
    surface = Color(0xFF191C1C),
)
