package com.techpath.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Spacing and sizes. Screens use these instead of literal dp values. */
object Dimens {
    val spaceXs = 4.dp
    val spaceS = 8.dp
    val spaceM = 16.dp
    val spaceL = 24.dp
    val spaceXl = 32.dp
    val iconM = 24.dp
    val progressHeight = 8.dp
    val indent = 24.dp
    /** Alpha for content that is inactive, e.g. an opportunity past its deadline. */
    const val DISABLED_ALPHA = 0.45f
}

@Composable
fun TechPathTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
