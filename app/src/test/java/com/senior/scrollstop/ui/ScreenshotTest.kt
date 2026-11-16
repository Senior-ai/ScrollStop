package com.senior.scrollstop.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each screen in light and dark mode to `app/build/screenshots/` so the UI can be
 * reviewed without a phone. These tests only fail if a screen can't be drawn.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun installSomeApps() {
        listOf(
            Triple("com.instagram.android", "Instagram", 0xFFD62976),
            Triple("com.zhiliaoapp.musically", "TikTok", 0xFF111111),
            Triple("com.google.android.youtube", "YouTube", 0xFFFF0000),
            Triple("com.whatsapp", "WhatsApp", 0xFF25D366),
            Triple("com.spotify.music", "Spotify", 0xFF1DB954),
        ).forEach { (pkg, label, color) -> fakeLauncherApp(pkg, label, color.toInt()) }
    }

    @Test fun mainLight() = capture("main-light", night = false) { mainIntent() }
    @Test fun mainDark() = capture("main-dark", night = true) { mainIntent() }

    @Test @Config(qualifiers = "+night")
    fun pauseChoiceDark() = capturePause("pause-choice-dark", Phase.Choice, "Instagram Reels", isFeature = true)

    @Test @Config(qualifiers = "+notnight")
    fun pauseChoiceLight() = capturePause("pause-choice-light", Phase.Choice, "Instagram Reels", isFeature = true)

    @Test @Config(qualifiers = "+night")
    fun pauseBreathingDark() = capturePause("pause-breathing-dark", Phase.Breathing, "TikTok", isFeature = false)

    @Test @Config(qualifiers = "+night")
    fun pauseTimesUpDark() = capturePause("pause-timesup-dark", Phase.TimesUp, "YouTube Shorts", isFeature = true)

    @Test @Config(qualifiers = "+notnight")
    fun pauseAppChoiceLight() = capturePause("pause-app-choice-light", Phase.Choice, "TikTok", isFeature = false)

    private fun capturePause(name: String, phase: Phase, label: String, isFeature: Boolean) {
        compose.setContent {
            ScrollStopTheme {
                InterceptScreen(
                    label = label,
                    isFeature = isFeature,
                    attempts = 4,
                    timesUp = phase == Phase.TimesUp,
                    pauseSeconds = 12,
                    onLeave = {},
                    onAllow = {},
                    startPhase = phase,
                )
            }
        }
        compose.waitForIdle()
        save(name, compose.activity.window.decorView)
    }

    private fun mainIntent() = Intent(context, MainActivity::class.java)

    private fun capture(name: String, night: Boolean, intent: () -> Intent) {
        RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
        ActivityScenario.launch<android.app.Activity>(intent()).use { scenario ->
            val looper = shadowOf(Looper.getMainLooper())
            looper.idle()
            // Let the background app list load and recompose.
            repeat(5) { Thread.sleep(50); looper.idle() }
            scenario.onActivity { activity -> save(name, activity.window.decorView) }
        }
    }

    private fun save(name: String, view: android.view.View) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val out = File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun fakeLauncherApp(pkg: String, label: String, color: Int) {
        val app = ApplicationInfo().apply {
            packageName = pkg
            nonLocalizedLabel = label
            name = label
        }
        val pm = shadowOf(context.packageManager)
        pm.installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = app })
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.addResolveInfoForIntent(
            launch,
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = pkg
                    name = "$pkg.Main"
                    applicationInfo = app
                }
            },
        )
        pm.addActivityIfNotPresent(ComponentName(pkg, "$pkg.Main"))
        pm.setApplicationIcon(
            pkg,
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(color)
                setSize(96, 96)
            },
        )
    }
}
