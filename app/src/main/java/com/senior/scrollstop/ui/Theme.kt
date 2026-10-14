package com.senior.scrollstop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF8FD3FE),
    onPrimary = Color(0xFF0F1720),
    secondary = Color(0xFFB8C7D9),
    background = Color(0xFF0F1720),
    onBackground = Color(0xFFE6EDF5),
    surface = Color(0xFF17222F),
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Color(0xFF1F2D3D),
    onSurfaceVariant = Color(0xFFA9B8C9),
    error = Color(0xFFFFB4AB),
)

@Composable
fun ScrollStopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
