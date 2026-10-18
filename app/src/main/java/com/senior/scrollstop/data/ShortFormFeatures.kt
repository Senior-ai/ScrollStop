package com.senior.scrollstop.data

/**
 * An endless short-video feed inside an otherwise useful app, such as Instagram Reels or
 * YouTube Shorts. Guarding a feature pauses only that feed; the rest of the app keeps working.
 *
 * Detection looks for the feed's full-screen player by view ID. Apps rename these from time to
 * time, so each feature lists several known IDs; add new ones here when an app update slips by.
 */
data class ShortFormFeature(
    val key: String,
    val label: String,
    val packageName: String,
    val playerViewIds: List<String>,
) {
    /** IDs in the form the accessibility API expects: `package:id/name`. */
    val fullViewIds: List<String> get() = playerViewIds.map { "$packageName:id/$it" }
}

object ShortFormFeatures {

    /** Guard keys for features share the allowance and counter storage with app package names. */
    private const val KEY_PREFIX = "feature:"

    val INSTAGRAM_REELS = ShortFormFeature(
        key = KEY_PREFIX + "instagram_reels",
        label = "Instagram Reels",
        packageName = "com.instagram.android",
        playerViewIds = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "clips_video_container",
        ),
    )

    val YOUTUBE_SHORTS = ShortFormFeature(
        key = KEY_PREFIX + "youtube_shorts",
        label = "YouTube Shorts",
        packageName = "com.google.android.youtube",
        playerViewIds = listOf(
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_player",
            "reel_progress_bar",
        ),
    )

    val ALL = listOf(INSTAGRAM_REELS, YOUTUBE_SHORTS)

    val ALL_KEYS: Set<String> = ALL.map { it.key }.toSet()

    val PACKAGES: Set<String> = ALL.map { it.packageName }.toSet()

    fun byKey(key: String): ShortFormFeature? = ALL.firstOrNull { it.key == key }

    fun isFeatureKey(key: String): Boolean = key.startsWith(KEY_PREFIX)

    /**
     * The enabled feature of [pkg] whose player is on screen, or null. [hasViewId] answers whether
     * a view with the given full ID exists in the current window.
     */
    fun detect(pkg: String, enabledKeys: Set<String>, hasViewId: (String) -> Boolean): ShortFormFeature? =
        ALL.firstOrNull { feature ->
            feature.packageName == pkg &&
                feature.key in enabledKeys &&
                feature.fullViewIds.any(hasViewId)
        }
}
