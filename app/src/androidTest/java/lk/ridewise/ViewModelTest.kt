package lk.ridewise

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import lk.ridewise.capture.screencapture.*
import lk.ridewise.data.repository.RideRepository
import lk.ridewise.domain.model.*
import lk.ridewise.ui.RideWiseViewModel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private class FakeRepository : RideRepository {
        override val history = MutableStateFlow<List<RideAnalysis>>(emptyList())
        override val settings = MutableStateFlow(VehicleCostSettings())
        override suspend fun save(analysis: RideAnalysis) { history.value = history.value.filterNot { it.id == analysis.id } + analysis }
        override suspend fun delete(id: String) { history.value = history.value.filterNot { it.id == id } }
        override suspend fun clear() { history.value = emptyList() }
        override suspend fun updateSettings(settings: VehicleCostSettings) { this.settings.value = settings }
    }
    @Test fun demoReviewResultsSaveReopenAndDelete() = runTest {
        val repository = FakeRepository()
        val model = RideWiseViewModel(repository, CaptureSession(), SavedStateHandle())
        var ready = false
        model.demo { ready = true }; assertTrue(ready)
        model.calculate(model.state.value.draft!!.copy(userReviewed = true))
        assertEquals(AnalysisStatus.COMPLETE, model.state.value.analysis!!.result.status)
        assertEquals(0, "1420".toBigDecimal().compareTo(model.state.value.analysis!!.result.netEarnings))
        model.save {}; val id = repository.history.value.single().id
        model.openAnalysis(id); assertEquals(id, model.state.value.analysis!!.id)
        model.delete(id); assertTrue(repository.history.value.isEmpty())
        assertEquals(VehicleCostSettings(), repository.settings.value)
    }
    @Test fun manualEditingUsesSavedVehicleCosts() = runTest {
        val repository = FakeRepository(); repository.updateSettings(VehicleCostSettings.Demo)
        val model = RideWiseViewModel(repository, CaptureSession(), SavedStateHandle())
        model.manual()
        val draft = model.state.value.draft!!.copy(expectedDriverPayout = "1000".toBigDecimal(), pickupDistanceKm = "1".toBigDecimal(),
            passengerTripDistanceKm = "9".toBigDecimal(), estimatedPickupMinutes = "5".toBigDecimal(), estimatedTripMinutes = "25".toBigDecimal(), userReviewed = true)
        model.calculate(draft)
        assertEquals(0, "620".toBigDecimal().compareTo(model.state.value.analysis!!.result.netEarnings))
        assertEquals(SourceType.MANUAL, model.state.value.analysis!!.ride.sourceType)
    }
    @Test fun processRestorationRetainsReviewDraftAndAssumptions() = runTest {
        val repository = FakeRepository(); val state = SavedStateHandle()
        val model = RideWiseViewModel(repository, CaptureSession(), state)
        model.demo {}; model.calculate(model.state.value.draft!!.copy(userReviewed = true))
        val restored = RideWiseViewModel(repository, CaptureSession(), state)
        assertEquals(model.state.value.analysis, restored.state.value.analysis)
        assertEquals(model.state.value.draft, restored.state.value.draft)
    }
    @Test fun permissionDeniedStateIsActionableAndNoRideInvented() {
        val session = CaptureSession(); session.permissionDenied()
        assertTrue(session.state.value is CaptureState.Error)
        assertTrue((session.state.value as CaptureState.Error).message.contains("manual"))
        session.reset(); assertEquals(CaptureState.Idle, session.state.value)
    }
}
