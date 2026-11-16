package com.senior.scrollstop.ui

import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import com.senior.scrollstop.service.BlockerAccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PauseOverlayTest {

    private val service = Robolectric.buildService(BlockerAccessibilityService::class.java).create().get()
    private val windowManager = service.getSystemService(WindowManager::class.java)

    private fun overlayViews() = Shadow.extract<ShadowWindowManagerImpl>(windowManager).views

    private fun showOverlay(onLeave: () -> Unit = {}, onAllow: (Int) -> Unit = {}): PauseOverlay =
        PauseOverlay(service).apply {
            show("com.instagram.android", "Instagram Reels", attempts = 2, timesUp = false, pauseSeconds = 6, onLeave, onAllow)
            shadowOf(Looper.getMainLooper()).idle()
        }

    @Test
    fun showsOverTheAppAndHidesCleanly() {
        val overlay = showOverlay()
        assertTrue(overlay.isShowing)
        assertEquals("com.instagram.android", overlay.coveredPackage)
        assertEquals(1, overlayViews().size)

        overlay.hide()
        assertFalse(overlay.isShowing)
        assertEquals(0, overlayViews().size)
    }

    @Test
    fun backCountsAsNo() {
        var left = 0
        showOverlay(onLeave = { left++ })
        val view = overlayViews().single()
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        assertEquals(1, left)
    }

    @Test
    fun showingAgainReplacesThePreviousOverlay() {
        val overlay = showOverlay()
        overlay.show("com.instagram.android", "Instagram Reels", 3, false, 8, {}, {})
        assertEquals(1, overlayViews().size)
        overlay.hide()
    }
}
