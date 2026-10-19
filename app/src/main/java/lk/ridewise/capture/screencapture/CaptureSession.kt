package lk.ridewise.capture.screencapture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import lk.ridewise.domain.model.RideRequest

sealed interface CaptureState {
    data object Idle : CaptureState
    data object ReadyToCapture : CaptureState
    data object Capturing : CaptureState
    data object Processing : CaptureState
    data class Extracted(val ride: RideRequest) : CaptureState
    data class Error(val message: String) : CaptureState
}

/** Process-local handoff. Screenshots and full recognized screen text are never retained here. */
class CaptureSession {
    private val mutableState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state = mutableState.asStateFlow()
    fun update(state: CaptureState) { mutableState.value = state }
    fun permissionDenied() { update(CaptureState.Error("Screen access was cancelled or denied. Try demo or manual entry, or start a new session.")) }
    fun reset() { update(CaptureState.Idle) }
}
