package lk.ridewise

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import lk.ridewise.data.local.*
import lk.ridewise.data.repository.RoomRideRepository
import lk.ridewise.domain.calculator.RideProfitabilityCalculator
import lk.ridewise.domain.demo.DemoRideRequestSource
import lk.ridewise.domain.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class LocalPersistenceTest {
    private lateinit var db: RideWiseDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RideWiseDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() { db.close() }
    private suspend fun analysis(): RideAnalysis {
        val ride = DemoRideRequestSource().capture().copy(userReviewed = true)
        return RideAnalysis(ride = ride, settings = VehicleCostSettings.Demo,
            result = RideProfitabilityCalculator().calculate(ride, VehicleCostSettings.Demo))
    }
    @Test fun saveReopenDeleteAndClearPreserveSnapshots() = runTest {
        val repository = RoomRideRepository(db.dao())
        val a = analysis()
        repository.save(a)
        assertEquals(a, repository.history.first().single())
        repository.save(a) // Idempotent save.
        assertEquals(1, repository.history.first().size)
        val b = a.copy(id = "second")
        repository.save(b); repository.delete(a.id)
        assertEquals(b, repository.history.first().single())
        repository.clear(); assertTrue(repository.history.first().isEmpty())
    }
    @Test fun settingsPersistExactDecimalsAndResetNullableInputs() = runTest {
        val repository = RoomRideRepository(db.dao())
        assertEquals(VehicleCostSettings(), repository.settings.first())
        val settings = VehicleCostSettings.Demo.copy(fuelPricePerLitre = "350.123456".toBigDecimal())
        repository.updateSettings(settings)
        assertEquals(settings, repository.settings.first())
        repository.updateSettings(VehicleCostSettings())
        assertEquals(VehicleCostSettings(), repository.settings.first())
    }
    @Test fun codecPreservesConfidenceNullableFieldsAndExcludesRawImageData() = runTest {
        val a = analysis()
        val encoded = SnapshotCodec.encode(a)
        assertEquals(a, SnapshotCodec.decode(encoded))
        assertFalse(encoded.contains("SYNTHETIC RIDE REQUEST"))
        assertFalse(encoded.contains("bitmap"))
    }
}
