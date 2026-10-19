package lk.ridewise.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import lk.ridewise.capture.screencapture.CaptureSession
import lk.ridewise.data.local.SnapshotCodec
import lk.ridewise.data.repository.RideRepository
import lk.ridewise.domain.calculator.RideProfitabilityCalculator
import lk.ridewise.domain.demo.DemoRideRequestSource
import lk.ridewise.domain.model.*

data class RideWiseState(
    val history: List<RideAnalysis> = emptyList(),
    val settings: VehicleCostSettings = VehicleCostSettings(),
    val draft: RideRequest? = null,
    val analysis: RideAnalysis? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class RideWiseViewModel(
    private val repository: RideRepository,
    val capture: CaptureSession,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val calculator = RideProfitabilityCalculator()
    private var activeSettings = savedState.get<String>("activeSettings")?.let { runCatching { SnapshotCodec.settings(it) }.getOrNull() }
    private val mutableState = MutableStateFlow(RideWiseState(
        draft = savedState.get<String>("draft")?.let { runCatching { SnapshotCodec.ride(it) }.getOrNull() },
        analysis = savedState.get<String>("analysis")?.let { runCatching { SnapshotCodec.decode(it) }.getOrNull() }))
    val state = mutableState.asStateFlow()
    init {
        viewModelScope.launch {
            repository.history.catch { mutableState.update { it.copy(error = "Local history could not be loaded. Restart RideWise to retry.", loading = false) } }
                .collect { history -> mutableState.update { it.copy(history = history) } }
        }
        viewModelScope.launch {
            repository.settings.catch { mutableState.update { it.copy(error = "Vehicle settings could not be loaded.", loading = false) } }
                .collect { settings -> mutableState.update { it.copy(settings = settings, loading = false) } }
        }
    }
    private fun setDraft(ride: RideRequest, settings: VehicleCostSettings) {
        activeSettings = settings
        savedState["activeSettings"] = SnapshotCodec.settingsJson(settings)
        savedState["draft"] = SnapshotCodec.rideJson(ride)
        savedState.remove<String>("analysis")
        mutableState.update { it.copy(draft = ride, analysis = null, error = null) }
    }
    fun demo(onReady: () -> Unit) = operation {
        setDraft(DemoRideRequestSource().capture(), VehicleCostSettings.Demo)
        onReady()
    }
    fun manual() { setDraft(RideRequest(sourceType = SourceType.MANUAL, currency = "LKR"), state.value.settings) }
    fun captured(ride: RideRequest) { setDraft(ride, state.value.settings); capture.reset() }
    fun calculate(ride: RideRequest) {
        val settings = activeSettings ?: state.value.settings
        val result = RideAnalysis(ride = ride, settings = settings, result = calculator.calculate(ride, settings))
        savedState["draft"] = SnapshotCodec.rideJson(ride)
        savedState["analysis"] = SnapshotCodec.encode(result)
        mutableState.update { it.copy(draft = ride, analysis = result) }
    }
    fun openAnalysis(id: String) {
        state.value.history.firstOrNull { it.id == id }?.let { analysis ->
            activeSettings = analysis.settings
            savedState["activeSettings"] = SnapshotCodec.settingsJson(analysis.settings)
            savedState["analysis"] = SnapshotCodec.encode(analysis)
            savedState["draft"] = SnapshotCodec.rideJson(analysis.ride)
            mutableState.update { it.copy(analysis = analysis, draft = analysis.ride) }
        }
    }
    fun save(onSaved: () -> Unit) = operation {
        val analysis = state.value.analysis ?: return@operation
        if (analysis.ride.sourceType == SourceType.OCR && !analysis.ride.userReviewed) {
            mutableState.update { it.copy(error = "Review and confirm captured fields before saving them to history.") }
            return@operation
        }
        repository.save(analysis)
        mutableState.update { it.copy(message = "Analysis saved on this device") }
        onSaved()
    }
    fun delete(id: String, onDeleted: () -> Unit = {}) = operation {
        repository.delete(id); onDeleted()
        mutableState.update { it.copy(message = "Analysis deleted") }
    }
    fun clear() = operation { repository.clear(); mutableState.update { it.copy(message = "History cleared") } }
    fun saveSettings(settings: VehicleCostSettings, forAnalysis: Boolean, onSaved: () -> Unit) = operation {
        if (forAnalysis) {
            activeSettings = settings
            savedState["activeSettings"] = SnapshotCodec.settingsJson(settings)
            state.value.draft?.let { calculate(it) }
        } else repository.updateSettings(settings)
        onSaved()
    }
    fun clearMessage() { mutableState.update { it.copy(message = null, error = null) } }
    private fun operation(block: suspend () -> Unit) {
        if (state.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(error = "This action could not be completed. Please try again; existing data is still available.") } }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }
}
