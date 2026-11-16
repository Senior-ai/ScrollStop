package com.senior.scrollstop.ui

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * The pause screen for Reels/Shorts, drawn as an accessibility overlay on top of the app instead
 * of as a separate activity. The app underneath keeps running, so allowing a few minutes simply
 * removes the overlay and the user is still exactly where they were in the feed.
 *
 * Accessibility overlays need no extra permission; only the running accessibility service can
 * add one.
 */
class PauseOverlay(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val audioManager = service.getSystemService(AudioManager::class.java)
    private var root: FrameLayout? = null
    private var owner: OverlayOwner? = null

    /** Package of the app the overlay is covering, while it is showing. */
    var coveredPackage: String? = null
        private set

    val isShowing: Boolean get() = root != null

    /** Taking audio focus pauses the Reel or Short playing underneath. */
    private val audioFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    fun show(
        coveredPackage: String,
        label: String,
        attempts: Int,
        timesUp: Boolean,
        pauseSeconds: Int,
        onLeave: () -> Unit,
        onAllow: (minutes: Int) -> Unit,
    ) {
        hide()
        this.coveredPackage = coveredPackage
        val lifecycleOwner = OverlayOwner().also { owner = it }

        val compose = ComposeView(service).apply {
            setContent {
                ScrollStopTheme {
                    InterceptScreen(
                        label = label,
                        isFeature = true,
                        attempts = attempts,
                        timesUp = timesUp,
                        pauseSeconds = pauseSeconds,
                        onLeave = onLeave,
                        onAllow = onAllow,
                    )
                }
            }
        }
        val frame = BackAwareFrame(service, onBack = onLeave).apply {
            addView(compose)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        lifecycleOwner.start()
        windowManager.addView(frame, params)
        root = frame
        audioManager.requestAudioFocus(audioFocus)
    }

    fun hide() {
        val view = root ?: return
        root = null
        coveredPackage = null
        owner?.stop()
        owner = null
        runCatching { windowManager.removeView(view) }
        audioManager.abandonAudioFocusRequest(audioFocus)
    }

    /**
     * Treats Back like "No, take me out". ScrollStop doesn't opt into predictive back, so the
     * back gesture arrives here as a key event too.
     */
    private class BackAwareFrame(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    /** Minimal lifecycle so Compose can run outside an activity. */
    private class OverlayOwner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun start() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
