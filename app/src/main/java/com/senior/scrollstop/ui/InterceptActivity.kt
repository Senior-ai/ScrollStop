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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.senior.scrollstop.data.BlockerStore
import com.senior.scrollstop.data.InstalledApps
import com.senior.scrollstop.data.PauseRules
import com.senior.scrollstop.data.ShortFormFeatures
import com.senior.scrollstop.service.BlockerAccessibilityService

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
