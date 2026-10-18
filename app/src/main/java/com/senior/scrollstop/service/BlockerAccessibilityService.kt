package com.senior.scrollstop.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.senior.scrollstop.data.BlockerStore
import com.senior.scrollstop.data.PauseRules
import com.senior.scrollstop.data.ShortFormFeature
import com.senior.scrollstop.data.ShortFormFeatures
import com.senior.scrollstop.ui.InterceptActivity

/**
 * Watches which app comes to the foreground. When it is a guarded app without an active
 * allowance, it puts the pause screen on top. When an allowance runs out while the app is
 * still open, it shows the "time's up" screen.
 *
 * Inside Instagram and YouTube it also looks for the Reels/Shorts player and pauses just that
 * feed, so the rest of the app stays usable.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private lateinit var store: BlockerStore
    private val handler = Handler(Looper.getMainLooper())

    private var foregroundPackage: String? = null
    private var lastInterceptKey: String? = null
    private var lastInterceptAt = 0L

    /** Last time we inspected the window for a short-form player; content events are frequent. */
    private var lastFeatureScanAt = 0L

    /** Feature the user was allowed into, so its expiry shows "time's up" rather than a fresh pause. */
    private var allowedFeatureInUse: String? = null

    /** Feature we're currently backing the user out of after they chose not to watch. */
    private var ejectingFrom: ShortFormFeature? = null
    private var ejectSteps = 0

    private val expiryCheck = Runnable { checkForeground(timesUp = true) }
    private val featureExpiryCheck = Runnable { scanForFeature(force = true) }
    private val ejectStep = Runnable { continueEject() }

    override fun onServiceConnected() {
        store = BlockerStore(this)
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowChanged(pkg)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (pkg == foregroundPackage) scanForFeature(force = false)
        }
    }

    private fun onWindowChanged(pkg: String) {
        if (isOverlayPackage(pkg)) return
        if (pkg == foregroundPackage) {
            // Same app, new screen (e.g. tapping the Reels tab).
            scanForFeature(force = true)
            return
        }

        foregroundPackage = pkg
        handler.removeCallbacks(expiryCheck)
        // Our own pause screen is on top: keep the guarded app as the logical foreground.
        if (pkg == packageName) return
        handler.removeCallbacks(featureExpiryCheck)
        allowedFeatureInUse = null
        // A different app came forward, so reopening the last intercepted app is a fresh attempt.
        if (pkg != lastInterceptKey) lastInterceptKey = null
        checkForeground(timesUp = false)
        scanForFeature(force = true)
    }

    private fun checkForeground(timesUp: Boolean) {
        val pkg = foregroundPackage ?: return
        if (pkg == packageName) return
        val now = System.currentTimeMillis()
        val allowedUntil = store.allowedUntil(pkg)

        if (!PauseRules.shouldIntercept(pkg, store.guardedPackages, allowedUntil, now)) {
            if (pkg in store.guardedPackages) {
                // Allowed for now; come back when the allowance ends.
                handler.postDelayed(expiryCheck, allowedUntil - now + 250)
            }
            return
        }
        intercept(pkg, timesUp)
    }

    private fun scanForFeature(force: Boolean) {
        val pkg = foregroundPackage ?: return
        if (pkg !in ShortFormFeatures.PACKAGES || InterceptActivity.isShowing) return
        val enabled = store.enabledFeatures
        if (enabled.isEmpty()) return
        val elapsed = SystemClock.elapsedRealtime()
        if (!force && elapsed - lastFeatureScanAt < FEATURE_SCAN_INTERVAL_MS) return
        lastFeatureScanAt = elapsed

        val feature = detectFeature(pkg, enabled) ?: return
        if (feature == ejectingFrom) return

        val now = System.currentTimeMillis()
        val allowedUntil = store.allowedUntil(feature.key)
        if (!PauseRules.shouldIntercept(feature.key, enabled, allowedUntil, now)) {
            allowedFeatureInUse = feature.key
            handler.removeCallbacks(featureExpiryCheck)
            handler.postDelayed(featureExpiryCheck, allowedUntil - now + 250)
            return
        }
        val timesUp = allowedFeatureInUse == feature.key
        allowedFeatureInUse = null
        intercept(feature.key, timesUp)
    }

    private fun detectFeature(pkg: String, enabled: Set<String>): ShortFormFeature? {
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() != pkg) return null
        return ShortFormFeatures.detect(pkg, enabled) { id ->
            root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser }
        }
    }

    /** Shows the pause screen for an app package or a feature key. */
    private fun intercept(key: String, timesUp: Boolean) {
        // Window events can fire several times while one screen opens; don't stack pause screens.
        val elapsed = SystemClock.elapsedRealtime()
        if (key == lastInterceptKey && elapsed - lastInterceptAt < DEBOUNCE_MS) return
        lastInterceptKey = key
        lastInterceptAt = elapsed

        val attempts = if (timesUp) store.attemptsToday(key) else store.recordAttempt(key)
        startActivity(InterceptActivity.intent(this, key, attempts, timesUp))
    }

    /**
     * Called when the user declines a feature: press Back until its player is gone, which lands
     * them on the app's normal screens (feed, DMs, search) instead of closing the whole app.
     */
    fun ejectFrom(featureKey: String) {
        ejectingFrom = ShortFormFeatures.byKey(featureKey) ?: return
        ejectSteps = 0
        handler.removeCallbacks(ejectStep)
        handler.postDelayed(ejectStep, EJECT_STEP_MS)
    }

    private fun continueEject() {
        val feature = ejectingFrom ?: return
        if (ejectSteps++ >= MAX_EJECT_STEPS) {
            ejectingFrom = null
            return
        }
        val inApp = rootInActiveWindow?.packageName?.toString() == feature.packageName
        if (inApp) {
            if (detectFeature(feature.packageName, setOf(feature.key)) == null) {
                ejectingFrom = null
                return
            }
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        // Not in the app yet means the pause screen is still closing; check again shortly.
        handler.postDelayed(ejectStep, EJECT_STEP_MS)
    }

    /** System UI and keyboards fire window events on top of apps without the app actually changing. */
    private fun isOverlayPackage(pkg: String): Boolean {
        if (pkg == "com.android.systemui" || pkg == "android") return true
        val imm = getSystemService(InputMethodManager::class.java)
        return imm.enabledInputMethodList.any { it.packageName == pkg }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val DEBOUNCE_MS = 1_500L
        private const val FEATURE_SCAN_INTERVAL_MS = 400L
        private const val EJECT_STEP_MS = 350L
        private const val MAX_EJECT_STEPS = 6

        /** The running service, so the pause screen can ask it to back out of a feed. */
        var instance: BlockerAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, BlockerAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }

        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
