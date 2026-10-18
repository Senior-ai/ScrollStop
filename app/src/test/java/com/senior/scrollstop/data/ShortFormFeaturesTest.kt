package com.senior.scrollstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortFormFeaturesTest {

    private val all = ShortFormFeatures.ALL_KEYS

    @Test
    fun detectsReelsWhenPlayerIsOnScreen() {
        val onScreen = setOf("com.instagram.android:id/clips_viewer_view_pager")
        val found = ShortFormFeatures.detect("com.instagram.android", all) { it in onScreen }
        assertEquals(ShortFormFeatures.INSTAGRAM_REELS, found)
    }

    @Test
    fun detectsShortsByAnyKnownId() {
        val onScreen = setOf("com.google.android.youtube:id/reel_progress_bar")
        val found = ShortFormFeatures.detect("com.google.android.youtube", all) { it in onScreen }
        assertEquals(ShortFormFeatures.YOUTUBE_SHORTS, found)
    }

    @Test
    fun ignoresRestOfTheApp() {
        val onScreen = setOf("com.instagram.android:id/direct_inbox")
        assertNull(ShortFormFeatures.detect("com.instagram.android", all) { it in onScreen })
    }

    @Test
    fun ignoresDisabledFeature() {
        val onScreen = setOf("com.google.android.youtube:id/reel_recycler")
        val enabled = setOf(ShortFormFeatures.INSTAGRAM_REELS.key)
        assertNull(ShortFormFeatures.detect("com.google.android.youtube", enabled) { it in onScreen })
    }

    @Test
    fun idsFromAnotherPackageDoNotMatch() {
        val onScreen = setOf("com.google.android.youtube:id/reel_recycler")
        assertNull(ShortFormFeatures.detect("com.instagram.android", all) { it in onScreen })
    }

    @Test
    fun recognisesFeatureKeys() {
        assertTrue(ShortFormFeatures.isFeatureKey(ShortFormFeatures.YOUTUBE_SHORTS.key))
        assertFalse(ShortFormFeatures.isFeatureKey("com.google.android.youtube"))
    }
}
