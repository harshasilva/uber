package lk.ridewise

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import lk.ridewise.capture.ocr.MlKitOnDeviceOcr
import lk.ridewise.capture.screencapture.UserInitiatedScreenCaptureSource
import lk.ridewise.domain.parser.LabelledRideTextParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnDeviceOcrTest {
    @Test fun bundledOcrReadsSyntheticFrameAndRecyclesPixels() = runBlocking {
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f; typeface = Typeface.DEFAULT }
        listOf("Driver payout: LKR 1,800.00", "Pickup distance: 1.5 km", "Pickup time: 5 min",
            "Passenger trip distance: 8.5 km", "Trip duration: 25 min").forEachIndexed { i, line ->
            canvas.drawText(line, 48f, 90f + i * 110f, paint)
        }
        val source = UserInitiatedScreenCaptureSource({ bitmap }, MlKitOnDeviceOcr(), LabelledRideTextParser())
        val ride = source.capture()
        assertEquals(0, "1800".toBigDecimal().compareTo(ride.expectedDriverPayout))
        assertEquals(0, "1.5".toBigDecimal().compareTo(ride.pickupDistanceKm))
        assertEquals(0, "8.5".toBigDecimal().compareTo(ride.passengerTripDistanceKm))
        assertEquals("LKR", ride.currency)
        assertFalse(ride.userReviewed)
        assertTrue(bitmap.isRecycled)
    }
}
