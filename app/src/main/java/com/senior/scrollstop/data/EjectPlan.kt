package com.senior.scrollstop.data

/** What to do next while getting the user out of a feed they declined. */
enum class EjectAction { Wait, Back, TapHomeTab, GoHome, Done }

/**
 * Escalating exit from Reels/Shorts. Each step is only taken if the feed is still on screen:
 * Back (returns to where the user came from), then the app's own Home tab, then the phone's
 * home screen, which always works. Pure logic so it can be unit tested.
 */
object EjectPlan {
    /** How many checks to wait for the pause screen to close before giving up. */
    const val MAX_WAITS = 8

    /**
     * [actionsTaken] counts Back/tab/home actions already performed; [waits] counts checks where
     * the app wasn't in front yet.
     */
    fun next(actionsTaken: Int, waits: Int, appInFront: Boolean, feedOnScreen: Boolean, hasHomeTab: Boolean): EjectAction =
        when {
            !appInFront -> if (waits < MAX_WAITS) EjectAction.Wait else EjectAction.Done
            !feedOnScreen -> EjectAction.Done
            actionsTaken == 0 -> EjectAction.Back
            actionsTaken == 1 && hasHomeTab -> EjectAction.TapHomeTab
            actionsTaken <= 2 -> EjectAction.GoHome
            else -> EjectAction.Done
        }
}
