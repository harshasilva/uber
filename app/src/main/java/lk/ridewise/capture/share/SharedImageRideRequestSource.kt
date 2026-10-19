package lk.ridewise.capture.share

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lk.ridewise.capture.ocr.OnDeviceOcr
import lk.ridewise.domain.capture.RideRequestSource
import lk.ridewise.domain.model.RideRequest
import lk.ridewise.domain.parser.RideTextParser

/** Handles one image explicitly shared to RideWise. The image and its OCR text are not persisted. */
class SharedImageRideRequestSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val ocr: OnDeviceOcr,
    private val parser: RideTextParser,
) : RideRequestSource {
    override suspend fun capture(): RideRequest {
        val bitmap = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } ?: throw IllegalArgumentException("RideWise could not open the shared image.")
        return try {
            val text = ocr.recognize(bitmap)
            check(text.isNotBlank()) { "No readable text was found in that image. Share a clear ride card or enter it manually." }
            parser.parse(text)
        } finally {
            bitmap.recycle()
        }
    }
}
