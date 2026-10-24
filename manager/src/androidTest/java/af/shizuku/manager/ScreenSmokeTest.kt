package af.shizuku.manager

import af.shizuku.manager.home.SystemHubActivity
import af.shizuku.manager.management.ApplicationManagementActivity
import af.shizuku.manager.settings.RootCompatibilityActivity
import af.shizuku.manager.settings.ServiceDoctorActivity
import af.shizuku.manager.settings.SettingsActivity
import af.shizuku.manager.settings.SettingsSearchEngine
import android.app.Activity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release smoke tests: every top-level screen must open and resume without crashing, with the
 * Shizuku server not running (the state a fresh install is in). A crash on launch fails the
 * instrumentation run.
 *
 * Also loads every icon the settings search can show, the same way the results list does;
 * #623/#624 were a search-result icon crash that only appeared when someone typed a query.
 */
@RunWith(AndroidJUnit4::class)
class ScreenSmokeTest {
    private fun <A : Activity> opens(cls: Class<A>) {
        ActivityScenario.launch(cls).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            // A screen may legitimately finish itself (e.g. onboarding redirect); crashing isn't.
            assertTrue(scenario.state == Lifecycle.State.RESUMED || scenario.state == Lifecycle.State.DESTROYED)
        }
    }

    @Test fun mainOpens() = opens(MainActivity::class.java)

    @Test fun appsOpens() = opens(ApplicationManagementActivity::class.java)

    @Test fun systemHubOpens() = opens(SystemHubActivity::class.java)

    @Test fun settingsOpens() = opens(SettingsActivity::class.java)

    @Test fun rootCompatibilityOpens() = opens(RootCompatibilityActivity::class.java)

    @Test fun serviceDoctorOpens() = opens(ServiceDoctorActivity::class.java)

    @Test
    fun everySettingsSearchResultIconLoads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val items =
            "aeiouy".flatMap { SettingsSearchEngine.search(context, it.toString()) }.distinct()
        assertTrue("search index looks empty", items.size > 20)
        items.filter { it.iconResId != 0 }.forEach {
            assertNotNull("icon for ${it.key} (${it.title})", ContextCompat.getDrawable(context, it.iconResId))
        }
    }
}
