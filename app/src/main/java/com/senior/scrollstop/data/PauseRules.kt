package com.senior.scrollstop.data

/** Pure decision logic, kept free of Android types so it can be unit tested on the JVM. */
object PauseRules {
    const val MIN_PAUSE_SECONDS = 3
    const val MAX_PAUSE_SECONDS = 30

    /** Extra seconds added for every repeat attempt today when progressive pauses are on. */
    const val PROGRESSIVE_STEP_SECONDS = 2

    /** Most questions a quiz pause ever asks for. */
    const val MAX_QUESTIONS = 3

    /** Repeat opens of the same thing today before a progressive quiz asks one more question. */
    const val OPENS_PER_EXTRA_QUESTION = 3

    /** Choices offered once the pause ends, in minutes. */
    val ALLOWANCE_MINUTES = listOf(1, 5, 15)

    /**
     * True when opening [pkg] should show the pause screen: it is guarded and there is no
     * unexpired allowance for it.
     */
    fun shouldIntercept(pkg: String, guarded: Set<String>, allowedUntilMillis: Long, nowMillis: Long): Boolean =
        pkg in guarded && nowMillis >= allowedUntilMillis

    /**
     * How long to make the user breathe before deciding. [attemptsToday] includes the current
     * attempt, so the first open of the day gets exactly [baseSeconds].
     */
    fun pauseSeconds(baseSeconds: Int, progressive: Boolean, attemptsToday: Int): Int {
        val base = baseSeconds.coerceIn(MIN_PAUSE_SECONDS, MAX_PAUSE_SECONDS)
        if (!progressive) return base
        val extra = (attemptsToday - 1).coerceAtLeast(0) * PROGRESSIVE_STEP_SECONDS
        return (base + extra).coerceAtMost(MAX_PAUSE_SECONDS)
    }

    /**
     * How many right answers a quiz pause needs. With progressive pauses on, every few repeat
     * opens today adds one, up to [MAX_QUESTIONS].
     */
    fun questionsRequired(progressive: Boolean, attemptsToday: Int): Int {
        if (!progressive) return 1
        val extra = (attemptsToday - 1).coerceAtLeast(0) / OPENS_PER_EXTRA_QUESTION
        return (1 + extra).coerceAtMost(MAX_QUESTIONS)
    }
}

/** What the pause asks of you before the choice. */
enum class PauseMode { BREATHE, QUIZ }
