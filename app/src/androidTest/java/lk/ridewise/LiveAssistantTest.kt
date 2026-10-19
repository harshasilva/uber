package lk.ridewise

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import lk.ridewise.domain.rate.HireVerdict
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Own-app synthetic values only. Overlay access is granted in the test emulator setup. */
@RunWith(AndroidJUnit4::class)
class LiveAssistantTest {
    private val app get() = ApplicationProvider.getApplicationContext<Context>() as RideWiseApplication
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    @Before fun prepare() { main { app.liveAssistant.stop(); app.liveAssistant.selectSources(true, true) } }
    @After fun clean() { main { app.liveAssistant.stop() } }
    @Test fun startRequiresAllExplicitPermissions() {
        main {
            app.liveStore.refreshPermissions()
            if (!app.liveStore.state.value.ready) {
                app.liveAssistant.start()
                assertFalse(app.liveStore.state.value.enabled)
                assertNotNull(app.liveStore.state.value.message)
            } else {
                app.liveAssistant.selectSources(false, false)
                app.liveAssistant.start()
                assertFalse(app.liveStore.state.value.enabled)
            }
        }
    }
    @Test fun demoGoodUsesPickupPlusTripAndDoesNotStartListening() {
        Assume.assumeTrue(Settings.canDrawOverlays(app))
        main {
            app.liveAssistant.demo(true)
            assertEquals(HireVerdict.GOOD, app.liveStore.state.value.latest!!.result.verdict)
            assertEquals(0, "60".toBigDecimal().compareTo(app.liveStore.state.value.latest!!.result.pricePerKm))
            assertFalse(app.liveStore.state.value.enabled)
        }
    }
    @Test fun demoBadAndStopClearTheOverlayAndState() {
        Assume.assumeTrue(Settings.canDrawOverlays(app))
        main {
            app.liveAssistant.demo(false)
            assertEquals(HireVerdict.BAD, app.liveStore.state.value.latest!!.result.verdict)
            app.liveAssistant.stop()
            assertNull(app.liveStore.state.value.latest)
            assertFalse(app.liveStore.state.value.enabled)
            assertFalse(app.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == 41 })
        }
    }
    @Test fun disablingBothProvidersDisarmsTheAssistant() {
        main {
            app.liveStore.setEnabled(true)
            app.liveAssistant.selectSources(false, false)
            assertFalse(app.liveStore.state.value.enabled)
            assertFalse(app.liveStore.state.value.uber)
            assertFalse(app.liveStore.state.value.pickme)
        }
    }
}
