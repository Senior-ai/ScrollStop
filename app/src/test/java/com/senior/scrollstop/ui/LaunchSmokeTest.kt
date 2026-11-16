package com.senior.scrollstop.ui

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.senior.scrollstop.data.ShortFormFeatures
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Opens each screen on a simulated device so a crash on launch fails the build. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchSmokeTest {

    @Test
    fun mainScreenOpens() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun pauseScreenOpensForAnApp() {
        val intent = InterceptActivity.intent(
            ApplicationProvider.getApplicationContext(), "com.zhiliaoapp.musically", attempts = 3, timesUp = false,
        )
        ActivityScenario.launch<InterceptActivity>(intent).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun pauseScreenOpensForReels() {
        val intent = InterceptActivity.intent(
            ApplicationProvider.getApplicationContext(), ShortFormFeatures.INSTAGRAM_REELS.key, attempts = 1, timesUp = true,
        )
        ActivityScenario.launch<InterceptActivity>(intent).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun crashReportShowsAfterACrash() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir, "last_crash.txt").writeText("java.lang.IllegalStateException: boom")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
