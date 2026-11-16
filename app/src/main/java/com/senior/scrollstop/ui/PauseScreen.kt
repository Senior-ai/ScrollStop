package com.senior.scrollstop.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.senior.scrollstop.data.PauseRules
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

internal enum class Phase { TimesUp, Breathing, Choice }

/** One breath in or out, in milliseconds. */
private const val BREATH_MS = 4_000

/** How high the tide stands, as a fraction of the screen, for each moment. */
private const val TIDE_INHALE = 0.55f
private const val TIDE_EXHALE = 0.26f
private const val TIDE_REST = 0.12f

/**
 * The pause, shared by the full-screen activity (whole apps) and the overlay (Reels/Shorts).
 * A warm tide rises as you breathe in and falls as you breathe out; when the pause ends it
 * settles low and the question takes over.
 */
@Composable
internal fun InterceptScreen(
    label: String,
    isFeature: Boolean,
    attempts: Int,
    timesUp: Boolean,
    pauseSeconds: Int,
    onLeave: () -> Unit,
    onAllow: (minutes: Int) -> Unit,
    startPhase: Phase? = null,
) {
    var phase by remember { mutableStateOf(startPhase ?: if (timesUp) Phase.TimesUp else Phase.Breathing) }
    var remaining by remember { mutableIntStateOf(pauseSeconds) }
    var inhale by remember { mutableStateOf(false) }
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }

    LaunchedEffect(phase) {
        if (phase != Phase.Breathing) return@LaunchedEffect
        remaining = pauseSeconds
        inhale = true
        while (remaining > 0) {
            delay(1_000)
            remaining--
            if ((pauseSeconds - remaining) % (BREATH_MS / 1_000) == 0) inhale = !inhale
        }
        phase = Phase.Choice
    }

    val level by animateFloatAsState(
        targetValue = when {
            phase != Phase.Breathing -> TIDE_REST
            inhale -> TIDE_INHALE
            else -> TIDE_EXHALE
        },
        animationSpec = if (motion) tween(if (phase == Phase.Breathing) BREATH_MS else 900, easing = FastOutSlowInEasing) else snap(),
        label = "tide",
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val restHeight = maxHeight * TIDE_REST
        Tide(level, motion, Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = 28.dp, end = 28.dp, top = 28.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge)
            Text(
                timesToday(attempts),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (phase) {
                Phase.Breathing -> Breathing(remaining, inhale)
                Phase.Choice -> Choice(label, isFeature, onLeave, onAllow)
                Phase.TimesUp -> TimesUp(label, isFeature, onLeave, onMore = { phase = Phase.Breathing })
            }
            if (phase != Phase.Breathing) Spacer(Modifier.height(restHeight + 24.dp))
        }
    }
}

@Composable
private fun Breathing(remaining: Int, inhale: Boolean) {
    Spacer(Modifier.height(36.dp))
    Text("$remaining", style = MaterialTheme.typography.displayLarge)
    Text(
        if (inhale) "Breathe in" else "Breathe out",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ColumnScope.Choice(label: String, isFeature: Boolean, onLeave: () -> Unit, onAllow: (Int) -> Unit) {
    Spacer(Modifier.weight(1f))
    Text(
        if (isFeature) "Still want to scroll?" else "Still want to open $label?",
        style = MaterialTheme.typography.displayMedium,
    )
    Spacer(Modifier.height(28.dp))
    PrimaryAction(if (isFeature) "Take me out of ${shortName(label)}" else "Don't open $label", onLeave)
    Spacer(Modifier.height(20.dp))
    Text(
        if (isFeature) "Or watch for" else "Or open it for",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PauseRules.ALLOWANCE_MINUTES.forEach { minutes ->
            OutlinedButton(
                onClick = { onAllow(minutes) },
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) { Text("$minutes min") }
        }
    }
}

@Composable
private fun ColumnScope.TimesUp(label: String, isFeature: Boolean, onLeave: () -> Unit, onMore: () -> Unit) {
    Spacer(Modifier.weight(1f))
    Text("Time's up.", style = MaterialTheme.typography.displayMedium)
    Text(
        "A good moment to put the phone down.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
    Spacer(Modifier.height(28.dp))
    PrimaryAction(if (isFeature) "Leave ${shortName(label)}" else "Close $label", onLeave)
    TextButton(onClick = onMore, modifier = Modifier.padding(top = 8.dp)) {
        Text("I need a bit more time", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
}

/** Two layered waves; the front one carries the breath. */
@Composable
private fun Tide(level: Float, motion: Boolean, modifier: Modifier) {
    val colors = LocalBreathColors.current
    val drift = if (motion) {
        rememberInfiniteTransition(label = "drift").animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(7_000, easing = LinearEasing), RepeatMode.Restart),
            label = "phase",
        ).value
    } else {
        0f
    }
    Canvas(modifier) {
        fun wave(height: Float, amplitude: Float, wavelength: Float, phase: Float, brush: Brush) {
            val top = size.height * (1 - height)
            val path = Path().apply {
                moveTo(0f, size.height)
                var x = 0f
                while (x <= size.width) {
                    lineTo(x, top + amplitude * sin(2 * PI.toFloat() * x / wavelength + phase))
                    x += 6f
                }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, brush)
        }
        val amp = 10.dp.toPx()
        // Dusk sea: ember at the crest sinking into rose, with a pale translucent swell behind.
        val crestY = size.height * (1 - level)
        wave(level + 0.035f, amp * 1.4f, size.width * 1.3f, -drift + 1.2f, SolidColor(colors.foam))
        wave(
            level, amp, size.width * 0.9f, drift,
            Brush.verticalGradient(listOf(colors.crest, colors.deep), startY = crestY, endY = size.height),
        )
    }
}

/** "Instagram Reels" → "Reels". */
private fun shortName(label: String) = label.substringAfterLast(' ')

private fun timesToday(attempts: Int): String = when {
    attempts <= 1 -> "First time today"
    else -> "${ordinal(attempts)} time today"
}

private fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}
