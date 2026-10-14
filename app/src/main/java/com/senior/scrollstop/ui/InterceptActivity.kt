package com.senior.scrollstop.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.senior.scrollstop.data.BlockerStore
import com.senior.scrollstop.data.InstalledApps
import com.senior.scrollstop.data.PauseRules
import kotlinx.coroutines.delay

/** The pause screen shown on top of a guarded app. */
class InterceptActivity : ComponentActivity() {

    private data class Request(val pkg: String, val label: String, val attempts: Int, val timesUp: Boolean, val nonce: Long)

    private lateinit var store: BlockerStore
    private var request by mutableStateOf<Request?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = BlockerStore(this)
        request = parse(intent)
        if (request == null) {
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this) { goHome() }

        setContent {
            ScrollStopTheme {
                request?.let { r ->
                    key(r.nonce) {
                        InterceptScreen(
                            label = r.label,
                            attempts = r.attempts,
                            timesUp = r.timesUp,
                            pauseSeconds = PauseRules.pauseSeconds(store.basePauseSeconds, store.progressivePause, r.attempts),
                            onLeave = {
                                store.recordResisted(r.pkg)
                                goHome()
                            },
                            onAllow = { minutes -> openTarget(r.pkg, minutes) },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parse(intent)?.let { request = it }
    }

    override fun onStop() {
        super.onStop()
        // Never leave a stale pause screen behind; the service brings a fresh one when needed.
        if (!isChangingConfigurations) finish()
    }

    private fun parse(intent: Intent): Request? {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return null
        return Request(
            pkg = pkg,
            label = InstalledApps.label(this, pkg),
            attempts = intent.getIntExtra(EXTRA_ATTEMPTS, 1),
            timesUp = intent.getBooleanExtra(EXTRA_TIMES_UP, false),
            nonce = System.nanoTime(),
        )
    }

    private fun openTarget(pkg: String, minutes: Int) {
        store.allow(pkg, System.currentTimeMillis() + minutes * 60_000L)
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_ATTEMPTS = "attempts"
        private const val EXTRA_TIMES_UP = "times_up"

        fun intent(context: Context, pkg: String, attempts: Int, timesUp: Boolean): Intent =
            Intent(context, InterceptActivity::class.java)
                .putExtra(EXTRA_PACKAGE, pkg)
                .putExtra(EXTRA_ATTEMPTS, attempts)
                .putExtra(EXTRA_TIMES_UP, timesUp)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

private enum class Phase { TimesUp, Breathing, Choice }

@Composable
private fun InterceptScreen(
    label: String,
    attempts: Int,
    timesUp: Boolean,
    pauseSeconds: Int,
    onLeave: () -> Unit,
    onAllow: (minutes: Int) -> Unit,
) {
    var phase by remember { mutableStateOf(if (timesUp) Phase.TimesUp else Phase.Breathing) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (phase) {
            Phase.TimesUp -> TimesUp(label, onLeave = onLeave, onMore = { phase = Phase.Breathing })
            Phase.Breathing -> Breathing(pauseSeconds, onDone = { phase = Phase.Choice })
            Phase.Choice -> Choice(label, attempts, onLeave, onAllow)
        }
    }
}

@Composable
private fun Breathing(seconds: Int, onDone: () -> Unit) {
    var remaining by remember { mutableIntStateOf(seconds) }
    // Starts deflated so the first "breathe in" visibly expands.
    var inhale by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (inhale) 1f else 0.55f,
        animationSpec = tween(durationMillis = BREATH_MS),
        label = "breath",
    )

    LaunchedEffect(Unit) {
        inhale = true
        while (remaining > 0) {
            delay(1_000)
            remaining--
            if ((seconds - remaining) % (BREATH_MS / 1_000) == 0) inhale = !inhale
        }
        onDone()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(220.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            )
            Text("$remaining", style = MaterialTheme.typography.displayMedium)
        }
        Spacer(Modifier.height(32.dp))
        Text(
            if (inhale) "Breathe in…" else "Breathe out…",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun Choice(label: String, attempts: Int, onLeave: () -> Unit, onAllow: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (attempts <= 1) "First time opening $label today." else "You've tried to open $label $attempts times today.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            "Do you still want to open it?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("No, I don't want to open $label")
        }
        Spacer(Modifier.height(8.dp))
        Text("Or open it for", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PauseRules.ALLOWANCE_MINUTES.forEach { minutes ->
                OutlinedButton(onClick = { onAllow(minutes) }) { Text("$minutes min") }
            }
        }
    }
}

@Composable
private fun TimesUp(label: String, onLeave: () -> Unit, onMore: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Time's up",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Your time on $label is over. Good moment to put the phone down.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Close $label")
        }
        TextButton(onClick = onMore) { Text("I need a bit more time") }
    }
}

private const val BREATH_MS = 4_000
