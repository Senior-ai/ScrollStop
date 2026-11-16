package com.senior.scrollstop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color

// Calm teal. A fixed palette rather than Material You, so every phone sees the colours we tested.
private val LightColors = lightColorScheme(
    primary = Color(0xFF006A62),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2E6),
    onPrimaryContainer = Color(0xFF00201D),
    secondaryContainer = Color(0xFFCCE8E3),
    onSecondaryContainer = Color(0xFF051F1C),
    background = Color(0xFFF4F7F6),
    surface = Color(0xFFF4F7F6),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE8EEEC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5CA),
    onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF005049),
    onPrimaryContainer = Color(0xFF9EF2E6),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCCE8E3),
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainer = Color(0xFF17191A),
    surfaceContainerHigh = Color(0xFF222526),
)

/** Follows the phone's light/dark setting. */
@Composable
fun ScrollStopTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors) {
        // Surface sets the default text colour; without it, plain Text is black in dark mode.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}
