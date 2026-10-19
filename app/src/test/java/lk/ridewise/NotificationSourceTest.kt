package lk.ridewise

import lk.ridewise.live.RideProvider
import org.junit.Assert.*
import org.junit.Test

class NotificationSourceTest {
    @Test fun driverPackagesAreExplicitlyAllowed() {
        assertEquals(RideProvider.UBER, RideProvider.fromPackage("com.ubercab.driver"))
        assertEquals(RideProvider.PICKME, RideProvider.fromPackage("com.pickme.driver.byod"))
    }
    @Test fun otherAppsAndRiderAppsAreIgnored() {
        listOf("com.ubercab", "lk.ridewise", "com.whatsapp", "com.android.shell", "com.ubercab.driver.fake").forEach {
            assertNull(RideProvider.fromPackage(it))
        }
    }
}
