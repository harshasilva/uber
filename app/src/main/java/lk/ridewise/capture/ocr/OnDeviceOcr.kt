package lk.ridewise.capture.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

interface OnDeviceOcr { suspend fun recognize(bitmap: Bitmap): String }

class MlKitOnDeviceOcr : OnDeviceOcr {
    override suspend fun recognize(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try { recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text }
        finally { recognizer.close() }
    }
}
