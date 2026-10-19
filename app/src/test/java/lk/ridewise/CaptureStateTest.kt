package lk.ridewise

import lk.ridewise.capture.screencapture.*
import org.junit.Assert.*
import org.junit.Test

class CaptureStateTest {
    @Test fun `denied permission leaves an actionable error`() {
        val session = CaptureSession()
        session.permissionDenied()
        assertTrue(session.state.value is CaptureState.Error)
        assertTrue((session.state.value as CaptureState.Error).message.contains("manual"))
    }
    @Test fun `stopping or consuming a session resets it`() {
        val session = CaptureSession()
        session.update(CaptureState.ReadyToCapture)
        session.reset()
        assertEquals(CaptureState.Idle, session.state.value)
    }
}
