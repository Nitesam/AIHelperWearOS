package com.base.aihelperwearos.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val DarkColorPalette = Colors(
    primary = Color(0xFF6DB6FF),
    primaryVariant = Color(0xFF4A90E2),
    secondary = Color(0xFFFFA726),
    secondaryVariant = Color(0xFFFF9800),
    background = Color(0xFF000000),
    surface = Color(0xFF1E1E1E),
    error = Color(0xFFCF6679),
    onPrimary = Color(0xFF000000),
    onSecondary = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFB0B0B0),
    onError = Color(0xFFFFFFFF)
)

/**
 * Applies the app theme and background surface for Wear OS screens.
 *
 * @param content composable content to render inside the theme.
 * @return `Unit` after composing the themed content.
 */
@Composable
fun AIHelperWearOSTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = DarkColorPalette
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            content()
        }
    }
}
