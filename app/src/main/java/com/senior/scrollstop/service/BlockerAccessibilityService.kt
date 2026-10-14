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
import com.senior.scrollstop.ui.InterceptActivity

/**
 * Watches which app comes to the foreground. When it is a guarded app without an active
 * allowance, it puts the pause screen on top. When an allowance runs out while the app is
 * still open, it shows the "time's up" screen.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private lateinit var store: BlockerStore
    private val handler = Handler(Looper.getMainLooper())

    private var foregroundPackage: String? = null
    private var lastInterceptPackage: String? = null
    private var lastInterceptAt = 0L

    private val expiryCheck = Runnable { checkForeground(timesUp = true) }

    override fun onServiceConnected() {
        store = BlockerStore(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (isOverlayPackage(pkg)) return
        if (pkg == foregroundPackage) return

        foregroundPackage = pkg
        handler.removeCallbacks(expiryCheck)
        // Our own pause screen is on top: keep the guarded app as the logical foreground.
        if (pkg == packageName) return
        // A different app came forward, so reopening the last intercepted app is a fresh attempt.
        if (pkg != lastInterceptPackage) lastInterceptPackage = null
        checkForeground(timesUp = false)
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

        // Window events can fire several times while one app opens; don't stack pause screens.
        val elapsed = SystemClock.elapsedRealtime()
        if (pkg == lastInterceptPackage && elapsed - lastInterceptAt < DEBOUNCE_MS) return
        lastInterceptPackage = pkg
        lastInterceptAt = elapsed

        val attempts = if (timesUp) store.attemptsToday(pkg) else store.recordAttempt(pkg)
        startActivity(InterceptActivity.intent(this, pkg, attempts, timesUp))
    }

    /** System UI and keyboards fire window events on top of apps without the app actually changing. */
    private fun isOverlayPackage(pkg: String): Boolean {
        if (pkg == "com.android.systemui" || pkg == "android") return true
        val imm = getSystemService(InputMethodManager::class.java)
        return imm.enabledInputMethodList.any { it.packageName == pkg }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(expiryCheck)
        super.onDestroy()
    }

    companion object {
        private const val DEBOUNCE_MS = 1_500L

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
