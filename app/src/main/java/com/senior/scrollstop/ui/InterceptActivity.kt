package com.senior.scrollstop.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import com.senior.scrollstop.data.ShortFormFeatures
import com.senior.scrollstop.service.BlockerAccessibilityService
import kotlinx.coroutines.delay

/** The pause screen shown on top of a guarded app or a guarded Reels/Shorts feed. */
class InterceptActivity : ComponentActivity() {

    /** [key] is an app package name or a [ShortFormFeatures] key. */
    private data class Request(
        val key: String,
        val label: String,
        val isFeature: Boolean,
        val attempts: Int,
        val timesUp: Boolean,
        val nonce: Long,
    )

    private lateinit var store: BlockerStore
    private var request by mutableStateOf<Request?>(null)

    /** Taking audio focus pauses the Reel or Short playing behind the pause screen. */
    private val audioFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = BlockerStore(this)
        request = parse(intent)
        if (request == null) {
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this) { request?.let(::leave) ?: finish() }

        setContent {
            ScrollStopTheme {
                request?.let { r ->
                    key(r.nonce) {
                        InterceptScreen(
                            label = r.label,
                            isFeature = r.isFeature,
                            attempts = r.attempts,
                            timesUp = r.timesUp,
                            pauseSeconds = PauseRules.pauseSeconds(store.basePauseSeconds, store.progressivePause, r.attempts),
                            onLeave = { leave(r) },
                            onAllow = { minutes -> allow(r, minutes) },
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

    override fun onStart() {
        super.onStart()
        isShowing = true
        getSystemService(AudioManager::class.java).requestAudioFocus(audioFocus)
    }

    override fun onStop() {
        super.onStop()
        isShowing = false
        getSystemService(AudioManager::class.java).abandonAudioFocusRequest(audioFocus)
        // Never leave a stale pause screen behind; the service brings a fresh one when needed.
        if (!isChangingConfigurations) finish()
    }

    private fun parse(intent: Intent): Request? {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return null
        val feature = ShortFormFeatures.byKey(key)
        return Request(
            key = key,
            label = feature?.label ?: InstalledApps.label(this, key),
            isFeature = feature != null,
            attempts = intent.getIntExtra(EXTRA_ATTEMPTS, 1),
            timesUp = intent.getBooleanExtra(EXTRA_TIMES_UP, false),
            nonce = System.nanoTime(),
        )
    }

    private fun leave(r: Request) {
        store.recordResisted(r.key)
        if (r.isFeature) {
            // Stay in the app, just back out of the feed.
            BlockerAccessibilityService.instance?.ejectFrom(r.key)
            finish()
        } else {
            goHome()
        }
    }

    private fun allow(r: Request, minutes: Int) {
        store.allow(r.key, System.currentTimeMillis() + minutes * 60_000L)
        if (!r.isFeature) {
            packageManager.getLaunchIntentForPackage(r.key)?.let {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        // For a feature, closing this screen reveals the feed exactly where it was.
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
        private const val EXTRA_KEY = "key"
        private const val EXTRA_ATTEMPTS = "attempts"
        private const val EXTRA_TIMES_UP = "times_up"

        /** True while the pause screen is visible, so the service doesn't scan the app behind it. */
        @Volatile
        var isShowing = false
            private set

        fun intent(context: Context, key: String, attempts: Int, timesUp: Boolean): Intent =
            Intent(context, InterceptActivity::class.java)
                .putExtra(EXTRA_KEY, key)
                .putExtra(EXTRA_ATTEMPTS, attempts)
                .putExtra(EXTRA_TIMES_UP, timesUp)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

private enum class Phase { TimesUp, Breathing, Choice }

@Composable
private fun InterceptScreen(
    label: String,
    isFeature: Boolean,
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
            Phase.TimesUp -> TimesUp(label, isFeature, onLeave = onLeave, onMore = { phase = Phase.Breathing })
            Phase.Breathing -> Breathing(pauseSeconds, onDone = { phase = Phase.Choice })
            Phase.Choice -> Choice(label, isFeature, attempts, onLeave, onAllow)
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
private fun Choice(label: String, isFeature: Boolean, attempts: Int, onLeave: () -> Unit, onAllow: (Int) -> Unit) {
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
            if (isFeature) "Do you really want to start scrolling?" else "Do you still want to open it?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(if (isFeature) "No, take me out of $label" else "No, I don't want to open $label")
        }
        Spacer(Modifier.height(8.dp))
        Text(if (isFeature) "Or watch for" else "Or open it for", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PauseRules.ALLOWANCE_MINUTES.forEach { minutes ->
                OutlinedButton(onClick = { onAllow(minutes) }) { Text("$minutes min") }
            }
        }
    }
}

@Composable
private fun TimesUp(label: String, isFeature: Boolean, onLeave: () -> Unit, onMore: () -> Unit) {
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
            Text(if (isFeature) "Leave $label" else "Close $label")
        }
        TextButton(onClick = onMore) { Text("I need a bit more time") }
    }
}

private const val BREATH_MS = 4_000
