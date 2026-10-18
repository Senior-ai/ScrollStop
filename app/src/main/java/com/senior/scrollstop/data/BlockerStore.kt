package com.senior.scrollstop.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** All persisted state: guarded apps, settings, active allowances and today's counters. */
class BlockerStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("scrollstop", Context.MODE_PRIVATE)

    var guardedPackages: Set<String>
        get() = prefs.getStringSet(KEY_GUARDED, emptySet())!!.toSet()
        set(value) = prefs.edit().putStringSet(KEY_GUARDED, value).apply()

    /** Reels/Shorts-style feeds to guard. On by default: they're the main reason to install this. */
    var enabledFeatures: Set<String>
        get() = prefs.getStringSet(KEY_FEATURES, ShortFormFeatures.ALL_KEYS)!!.toSet()
        set(value) = prefs.edit().putStringSet(KEY_FEATURES, value).apply()

    var basePauseSeconds: Int
        get() = prefs.getInt(KEY_PAUSE, DEFAULT_PAUSE_SECONDS)
        set(value) = prefs.edit().putInt(KEY_PAUSE, value).apply()

    var progressivePause: Boolean
        get() = prefs.getBoolean(KEY_PROGRESSIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_PROGRESSIVE, value).apply()

    fun setGuarded(pkg: String, guarded: Boolean) {
        guardedPackages = if (guarded) guardedPackages + pkg else guardedPackages - pkg
    }

    fun setFeatureEnabled(key: String, enabled: Boolean) {
        enabledFeatures = if (enabled) enabledFeatures + key else enabledFeatures - key
    }

    fun allowedUntil(pkg: String): Long = prefs.getLong(KEY_ALLOW + pkg, 0L)

    fun allow(pkg: String, untilMillis: Long) = prefs.edit().putLong(KEY_ALLOW + pkg, untilMillis).apply()

    fun revokeAllowance(pkg: String) = prefs.edit().remove(KEY_ALLOW + pkg).apply()

    /** Records an attempt to open [pkg] and returns today's attempt count including this one. */
    fun recordAttempt(pkg: String): Int = increment(KEY_ATTEMPTS, pkg)

    /** Records that the user chose not to open [pkg]. */
    fun recordResisted(pkg: String): Int = increment(KEY_RESISTED, pkg)

    fun attemptsToday(pkg: String): Int = countToday(KEY_ATTEMPTS, pkg)

    fun todayTotals(): DailyTotals {
        rollOverIfNewDay()
        var attempts = 0
        var resisted = 0
        val perApp = mutableMapOf<String, Int>()
        for ((key, value) in prefs.all) {
            val count = value as? Int ?: continue
            when {
                key.startsWith(KEY_ATTEMPTS) -> {
                    attempts += count
                    perApp[key.removePrefix(KEY_ATTEMPTS)] = count
                }
                key.startsWith(KEY_RESISTED) -> resisted += count
            }
        }
        return DailyTotals(attempts, resisted, perApp)
    }

    private fun increment(prefix: String, pkg: String): Int {
        rollOverIfNewDay()
        val next = prefs.getInt(prefix + pkg, 0) + 1
        prefs.edit().putInt(prefix + pkg, next).apply()
        return next
    }

    private fun countToday(prefix: String, pkg: String): Int {
        rollOverIfNewDay()
        return prefs.getInt(prefix + pkg, 0)
    }

    /** Counters are per day; wipe them the first time we touch them on a new date. */
    private fun rollOverIfNewDay() {
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_DAY, null) == today) return
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(KEY_ATTEMPTS) || it.startsWith(KEY_RESISTED) }
            .forEach(editor::remove)
        editor.putString(KEY_DAY, today).apply()
    }

    data class DailyTotals(val attempts: Int, val resisted: Int, val attemptsByPackage: Map<String, Int>)

    companion object {
        const val DEFAULT_PAUSE_SECONDS = 6
        private const val KEY_GUARDED = "guarded"
        private const val KEY_FEATURES = "features"
        private const val KEY_PAUSE = "pause_seconds"
        private const val KEY_PROGRESSIVE = "progressive"
        private const val KEY_DAY = "stats_day"
        private const val KEY_ALLOW = "allow|"
        private const val KEY_ATTEMPTS = "attempts|"
        private const val KEY_RESISTED = "resisted|"

        /** Popular time sinks, pre-sorted to the top of the picker. */
        val SUGGESTED_PACKAGES = setOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.google.android.youtube",
            "com.facebook.katana",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.snapchat.android",
            "com.pinterest",
            "com.linkedin.android",
            "tv.twitch.android.app",
            "com.netflix.mediaclient",
        )
    }
}
