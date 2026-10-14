package com.senior.scrollstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseRulesTest {

    private val guarded = setOf("com.instagram.android")

    @Test
    fun interceptsGuardedAppWithoutAllowance() {
        assertTrue(PauseRules.shouldIntercept("com.instagram.android", guarded, 0L, 1_000L))
    }

    @Test
    fun ignoresAppsThatAreNotGuarded() {
        assertFalse(PauseRules.shouldIntercept("com.android.chrome", guarded, 0L, 1_000L))
    }

    @Test
    fun respectsActiveAllowanceUntilItExpires() {
        assertFalse(PauseRules.shouldIntercept("com.instagram.android", guarded, 5_000L, 4_999L))
        assertTrue(PauseRules.shouldIntercept("com.instagram.android", guarded, 5_000L, 5_000L))
    }

    @Test
    fun firstAttemptGetsBasePause() {
        assertEquals(6, PauseRules.pauseSeconds(6, progressive = true, attemptsToday = 1))
    }

    @Test
    fun progressivePauseGrowsWithAttemptsAndIsCapped() {
        assertEquals(10, PauseRules.pauseSeconds(6, progressive = true, attemptsToday = 3))
        assertEquals(PauseRules.MAX_PAUSE_SECONDS, PauseRules.pauseSeconds(6, progressive = true, attemptsToday = 50))
    }

    @Test
    fun nonProgressivePauseStaysFlat() {
        assertEquals(6, PauseRules.pauseSeconds(6, progressive = false, attemptsToday = 50))
    }

    @Test
    fun baseIsClampedToSaneRange() {
        assertEquals(PauseRules.MIN_PAUSE_SECONDS, PauseRules.pauseSeconds(0, progressive = false, attemptsToday = 1))
        assertEquals(PauseRules.MAX_PAUSE_SECONDS, PauseRules.pauseSeconds(99, progressive = false, attemptsToday = 1))
    }
}
