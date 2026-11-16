package com.senior.scrollstop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.senior.scrollstop.R

/**
 * Dusk palette: night-indigo ground, lavender text, one warm ember accent that stands for the
 * breath (the tide on the pause screen, the main action, your numbers).
 */
private object Dusk {
    val Night = Color(0xFF15132A)
    val Raised = Color(0xFF221F3D)
    val RaisedHigh = Color(0xFF2B2749)
    val Hairline = Color(0xFF2E2A4F)
    val Text = Color(0xFFE9E6F5)
    val TextQuiet = Color(0xFFA7A2C3)
    val Ember = Color(0xFFF4A259)

    val Paper = Color(0xFFF5F3FA)
    val PaperRaised = Color(0xFFEAE6F5)
    val PaperRaisedHigh = Color(0xFFE0DBEF)
    val PaperHairline = Color(0xFFD9D4EA)
    val Ink = Color(0xFF1D1A33)
    val InkQuiet = Color(0xFF5D5876)
    val EmberDeep = Color(0xFFB3570F)
    val EmberSoft = Color(0xFFF4B97A)
    val Rose = Color(0xFFB5577A)
    val RoseSoft = Color(0xFFE9A6BC)
}

private val DarkColors = darkColorScheme(
    primary = Dusk.Ember,
    onPrimary = Dusk.Night,
    primaryContainer = Dusk.Raised,
    onPrimaryContainer = Dusk.Text,
    secondaryContainer = Dusk.Raised,
    onSecondaryContainer = Dusk.Text,
    background = Dusk.Night,
    onBackground = Dusk.Text,
    surface = Dusk.Night,
    onSurface = Dusk.Text,
    onSurfaceVariant = Dusk.TextQuiet,
    surfaceContainer = Dusk.Raised,
    surfaceContainerHigh = Dusk.RaisedHigh,
    surfaceContainerHighest = Dusk.RaisedHigh,
    outline = Dusk.TextQuiet,
    outlineVariant = Dusk.Hairline,
)

private val LightColors = lightColorScheme(
    primary = Dusk.EmberDeep,
    onPrimary = Color.White,
    primaryContainer = Dusk.PaperRaised,
    onPrimaryContainer = Dusk.Ink,
    secondaryContainer = Dusk.PaperRaised,
    onSecondaryContainer = Dusk.Ink,
    background = Dusk.Paper,
    onBackground = Dusk.Ink,
    surface = Dusk.Paper,
    onSurface = Dusk.Ink,
    onSurfaceVariant = Dusk.InkQuiet,
    surfaceContainer = Dusk.PaperRaised,
    surfaceContainerHigh = Dusk.PaperRaisedHigh,
    surfaceContainerHighest = Dusk.PaperRaisedHigh,
    outline = Dusk.InkQuiet,
    outlineVariant = Dusk.PaperHairline,
)

/** Colours outside Material's roles. */
@Immutable
data class BreathColors(val crest: Color, val deep: Color, /** The translucent swell behind the tide. */ val foam: Color)

val LocalBreathColors = staticCompositionLocalOf { BreathColors(Dusk.Ember, Dusk.Rose, Dusk.Rose) }

/** Soft Fraunces: the voice of the app, for numbers and headlines. Body text stays the system font. */
val Fraunces = FontFamily(
    Font(R.font.fraunces_regular, FontWeight.Normal),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
)

private val Type = Typography().let { base ->
    fun display(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = Fraunces, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = (-0.01).em)
    base.copy(
        displayLarge = display(120, 120),
        displayMedium = display(56, 60),
        displaySmall = display(40, 46),
        headlineLarge = display(38, 44, FontWeight.SemiBold),
        headlineMedium = display(30, 36),
        headlineSmall = display(24, 30),
        titleLarge = display(22, 28),
    )
}

/** Follows the phone's light/dark setting. */
@Composable
fun ScrollStopTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val breath = if (dark) {
        BreathColors(crest = Dusk.Ember, deep = Dusk.Rose, foam = Dusk.Rose.copy(alpha = 0.45f))
    } else {
        BreathColors(crest = Dusk.EmberSoft, deep = Dusk.RoseSoft, foam = Dusk.EmberSoft.copy(alpha = 0.22f))
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = Type) {
        androidx.compose.runtime.CompositionLocalProvider(LocalBreathColors provides breath) {
            // Surface sets the default text colour; without it, plain Text is black in dark mode.
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}
