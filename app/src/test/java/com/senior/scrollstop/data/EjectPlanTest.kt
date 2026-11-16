package com.senior.scrollstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EjectPlanTest {

    private fun next(actions: Int, waits: Int = 0, inFront: Boolean = true, feed: Boolean = true, tab: Boolean = true) =
        EjectPlan.next(actions, waits, inFront, feed, tab)

    @Test
    fun waitsForThePauseScreenToClose() {
        assertEquals(EjectAction.Wait, next(0, inFront = false))
    }

    @Test
    fun givesUpWaitingIfTheUserWentElsewhere() {
        assertEquals(EjectAction.Done, next(0, waits = EjectPlan.MAX_WAITS, inFront = false))
    }

    @Test
    fun triesBackFirst() {
        assertEquals(EjectAction.Back, next(0))
    }

    @Test
    fun stopsAsSoonAsTheFeedIsGone() {
        assertEquals(EjectAction.Done, next(1, feed = false))
    }

    @Test
    fun thenTapsTheAppsHomeTab() {
        assertEquals(EjectAction.TapHomeTab, next(1))
    }

    @Test
    fun skipsTheTabWhenTheAppHasNone() {
        assertEquals(EjectAction.GoHome, next(1, tab = false))
    }

    @Test
    fun alwaysEndsOnThePhonesHomeScreenIfStillStuck() {
        assertEquals(EjectAction.GoHome, next(2))
    }

    @Test
    fun neverLoopsForever() {
        assertEquals(EjectAction.Done, next(3))
    }
}
