package lk.ridewise.capture.screencapture

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import lk.ridewise.MainActivity
import lk.ridewise.R
import lk.ridewise.RideWiseApplication
import lk.ridewise.capture.ocr.MlKitOnDeviceOcr
import lk.ridewise.domain.parser.LabelledRideTextParser

/**
 * Consent-sensitive single-shot capture. Only ACTION_START with fresh system consent can
 * create a session. ACTION_CAPTURE (in our UI/notification) requests one frame after 2 s.
 * No virtual display is created while waiting. No overlay, polling or token reuse.
 */
class ScreenCaptureService : Service() {
    private val session get() = (application as RideWiseApplication).captureSession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var frame: CompletableDeferred<Bitmap>? = null
    private var captureJob: Job? = null
    private var intentionalStop = false
    private var width = 0
    private var height = 0
    private val expiry = Runnable { fail("Capture session expired after two minutes. Start a new session when ready.") }
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            if (!intentionalStop) {
                session.update(CaptureState.Error("Screen sharing stopped by Android or the user. Start a new session to capture again."))
                stopSelf()
            }
            releaseDisplay()
        }
        override fun onCapturedContentResize(w: Int, h: Int) {
            if (reader != null && (w != width || h != height) && w > 0 && h > 0) {
                val old = reader
                width = w; height = h
                reader = newReader(w, h)
                display?.resize(w, h, resources.configuration.densityDpi)
                display?.surface = reader!!.surface
                old?.close()
            }
        }
    }
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession(intent)
            ACTION_CAPTURE -> requestFrame()
            ACTION_STOP -> { session.reset(); stopSelf() }
            else -> stopSelf()
        }
        // Consent is deliberately never recovered from a sticky service restart.
        return START_NOT_STICKY
    }
    @Suppress("DEPRECATION")
    private fun startSession(intent: Intent) {
        if (projection != null) return
        val consent = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else intent.getParcelableExtra(EXTRA_DATA)
        if (intent.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED) != Activity.RESULT_OK || consent == null) {
            fail("Screen consent is missing. Start capture again from RideWise."); return
        }
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "User-controlled screen capture", NotificationManager.IMPORTANCE_LOW))
            ServiceCompat.startForeground(this, NOTIFICATION, notification("Ready · capture one screen when safe", true),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
            projection = getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(Activity.RESULT_OK, consent)
            projection!!.registerCallback(callback, handler) // Must precede createVirtualDisplay on Android 14+.
            session.update(CaptureState.ReadyToCapture)
            handler.postDelayed(expiry, 120_000)
        } catch (_: Exception) { fail("Android could not start screen sharing. Grant system consent again or use manual entry.") }
    }
    private fun requestFrame() {
        if (projection == null) {
            fail("The screen session is no longer active. Start a fresh session before capturing.")
            return
        }
        if (session.state.value != CaptureState.ReadyToCapture) {
            session.update(CaptureState.Error("Capture is not ready. Start a new screen session and try again."))
            return
        }
        session.update(CaptureState.Capturing)
        notify("Capturing once in 2 seconds · close notification shade", false)
        captureJob = scope.launch {
            try {
                // Gives the user time to dismiss the shade; the requested screen stays visible.
                delay(2_000)
                frame = CompletableDeferred()
                createDisplay()
                val source = UserInitiatedScreenCaptureSource(
                    frame = {
                        val bitmap = withTimeout(12_000) { frame!!.await() }
                        stopProjection()
                        session.update(CaptureState.Processing)
                        notify("Recognizing text on this device…", false)
                        bitmap
                    }, MlKitOnDeviceOcr(), LabelledRideTextParser())
                val ride = withTimeout(30_000) { source.capture() }
                session.update(CaptureState.Extracted(ride))
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) session.update(CaptureState.Error("Screen capture timed out. Use a visible, permitted screen and try again."))
                else throw e
            } catch (e: Exception) {
                session.update(CaptureState.Error(if (e is IllegalStateException) e.message ?: "Capture failed. Try manual entry." else "Capture or on-device text recognition failed. Try again or enter the ride manually."))
            } finally { stopProjection(); stopSelf() }
        }
    }
    @Suppress("DEPRECATION")
    private fun createDisplay() {
        val manager = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = manager.maximumWindowMetrics.bounds
            width = bounds.width(); height = bounds.height()
        } else {
            val metrics = DisplayMetrics()
            manager.defaultDisplay.getRealMetrics(metrics)
            width = metrics.widthPixels; height = metrics.heightPixels
        }
        reader = newReader(width, height)
        // A fresh consent token is used for this single virtual display, once per session.
        display = projection!!.createVirtualDisplay("RideWise one-shot", width, height,
            resources.configuration.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler)
    }
    private fun newReader(w: Int, h: Int): ImageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2).apply {
        setOnImageAvailableListener({ current ->
            val image = current.acquireLatestImage() ?: return@setOnImageAvailableListener
            image.use {
                val target = frame
                if (target == null || target.isCompleted) return@use
                var padded: Bitmap? = null
                try {
                    val plane = image.planes[0]
                    val strideWidth = plane.rowStride / plane.pixelStride
                    padded = Bitmap.createBitmap(strideWidth, image.height, Bitmap.Config.ARGB_8888)
                    plane.buffer.rewind()
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                    if (cropped !== padded) padded.recycle()
                    padded = null
                    if (!target.complete(cropped)) cropped.recycle()
                } catch (_: Exception) { padded?.recycle(); target.completeExceptionally(IllegalStateException("Unable to read this screen. It may be protected. Use manual entry.")) }
            }
        }, handler)
    }
    private fun notification(message: String, capture: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .putExtra("open_capture", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(name: String, code: Int) = PendingIntent.getService(this, code,
            Intent(this, ScreenCaptureService::class.java).setAction(name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_capture)
            .setContentTitle("RideWise · screen access active").setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)).setContentIntent(open)
            .setOngoing(true).setSilent(true).setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply { if (capture) addAction(0, "Capture once", action(ACTION_CAPTURE, 1)) }
            .addAction(0, "Stop", action(ACTION_STOP, 2)).build()
    }
    private fun notify(message: String, capture: Boolean) = getSystemService(NotificationManager::class.java)
        .notify(NOTIFICATION, notification(message, capture))
    private fun releaseDisplay() { display?.release(); display = null; reader?.close(); reader = null }
    private fun stopProjection() {
        intentionalStop = true
        handler.removeCallbacks(expiry)
        releaseDisplay()
        projection?.unregisterCallback(callback)
        projection?.stop(); projection = null
    }
    private fun fail(message: String) { session.update(CaptureState.Error(message)); stopSelf() }
    override fun onDestroy() {
        captureJob?.cancel(); scope.cancel(); handler.removeCallbacksAndMessages(null)
        // Recycle an in-memory frame if cancellation happened before the OCR source acquired it.
        frame?.let { pending -> if (pending.isCompleted && !pending.isCancelled && session.state.value is CaptureState.Capturing) {
            @OptIn(ExperimentalCoroutinesApi::class) val bitmap = runCatching { pending.getCompleted() }.getOrNull()
            bitmap?.recycle()
        } }
        stopProjection()
        if (session.state.value is CaptureState.ReadyToCapture || session.state.value is CaptureState.Capturing || session.state.value is CaptureState.Processing) session.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        const val ACTION_START = "lk.ridewise.capture.START"
        const val ACTION_CAPTURE = "lk.ridewise.capture.ONCE"
        const val ACTION_STOP = "lk.ridewise.capture.STOP"
        const val EXTRA_DATA = "consent_data"
        const val EXTRA_CODE = "consent_code"
        const val CHANNEL = "ridewise_capture"
        private const val NOTIFICATION = 40
    }
}
