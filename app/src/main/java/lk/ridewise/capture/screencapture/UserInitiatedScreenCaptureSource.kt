package lk.ridewise.capture.screencapture

import android.graphics.Bitmap
import lk.ridewise.capture.ocr.OnDeviceOcr
import lk.ridewise.domain.capture.RideRequestSource
import lk.ridewise.domain.model.RideRequest
import lk.ridewise.domain.parser.RideTextParser

/** The provider can deliver exactly one frame, after an explicit user action. */
class UserInitiatedScreenCaptureSource(
    private val frame: suspend () -> Bitmap,
    private val ocr: OnDeviceOcr,
    private val parser: RideTextParser,
) : RideRequestSource {
    override suspend fun capture(): RideRequest {
        val bitmap = frame()
        return try {
            val text = ocr.recognize(bitmap)
            check(text.isNotBlank()) { "No text was found. The screen may be blank, protected, or unreadable. Use manual entry or try another permitted screen." }
            parser.parse(text)
        } finally { bitmap.recycle() }
    }
}
