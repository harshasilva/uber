package lk.ridewise.capture.automatic

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import lk.ridewise.MainActivity
import lk.ridewise.R
import lk.ridewise.RideWiseApplication
import lk.ridewise.domain.rate.*
import lk.ridewise.live.HireOverlay
import lk.ridewise.live.LiveHire

/** A visible, explicitly consented screen-sharing session. Never restarts itself or saves frames. */
class AutomaticScreenService : Service() {
    private val session get() = (application as RideWiseApplication).automaticSession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val parser = ScreenOfferParser()
    private val tracker = StableOfferTracker()
    private val overlay by lazy { HireOverlay(this) { stop(it) } }
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var reading = false
    private var stopping = false
    private var lastScan = 0L
    private var lastFrame = 0L
    private var shown: ScreenOffer? = null
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { stop("OFF · phone locked. Start a new session when ready.") }
    }
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { stop("OFF · Android stopped screen sharing. Start a new session when ready.") }
        override fun onCapturedContentResize(w: Int, h: Int) {
            if (!stopping && w > 0 && h > 0 && (w != width || h != height)) {
                val old = reader
                width = w; height = h
                reader = newReader(w, h)
                display?.resize(w, h, resources.configuration.densityDpi)
                display?.surface = reader!!.surface
                old?.close()
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) stop()
        else if (intent?.action == START && projection == null && !stopping) start(intent)
        else if (projection == null) stop()
        return START_NOT_STICKY
    }
    @Suppress("DEPRECATION")
    private fun start(intent: Intent) {
        val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(DATA, Intent::class.java) else intent.getParcelableExtra(DATA)
        if (data == null || intent.getIntExtra(CODE, 0) != Activity.RESULT_OK) { stop("Screen sharing permission is needed. Tap Start automatic assistant."); return }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Automatic screen assistant", NotificationManager.IMPORTANCE_LOW))
            ServiceCompat.startForeground(this, ID, notification(), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
            if (!permissionsReady()) { stop("Allow display over other apps and RideWise notifications, then start again."); return }
            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK, data)
            projection!!.registerCallback(callback, handler)
            val windows = getSystemService(WindowManager::class.java)
            if (Build.VERSION.SDK_INT >= 30) { val bounds = windows.maximumWindowMetrics.bounds; width = bounds.width(); height = bounds.height() }
            else { val metrics = DisplayMetrics(); windows.defaultDisplay.getRealMetrics(metrics); width = metrics.widthPixels; height = metrics.heightPixels }
            reader = newReader(width, height)
            // One display per permission token. Frames are sampled from this display for the session.
            display = projection!!.createVirtualDisplay("RideWise automatic offers", width, height, resources.configuration.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)
            session.start()
            lastFrame = SystemClock.elapsedRealtime()
            scope.launch {
                while (isActive && !stopping) {
                    delay(2_000)
                    if (!permissionsReady()) stop("OFF · required permission was removed.")
                    else if (!getSystemService(PowerManager::class.java).isInteractive) stop("OFF · screen is locked.")
                    else if (SystemClock.elapsedRealtime() - lastFrame > 15_000) stop("No screen frames received. Start a new session and choose Entire screen.")
                }
            }
        } catch (_: Exception) { stop("Could not start automatic screen sharing. Start again and approve Android’s screen-sharing prompt.") }
    }
    private fun permissionsReady() = Settings.canDrawOverlays(this) && NotificationManagerCompat.from(this).areNotificationsEnabled() &&
        getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    private fun newReader(w: Int, h: Int): ImageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2).apply {
        setOnImageAvailableListener({ current ->
            val image = runCatching { current.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            image.use {
                lastFrame = SystemClock.elapsedRealtime()
                if (stopping || reading || lastFrame - lastScan < 750) return@use
                lastScan = lastFrame
                var padded: Bitmap? = null
                try {
                    val plane = image.planes[0]
                    padded = Bitmap.createBitmap(plane.rowStride / plane.pixelStride, image.height, Bitmap.Config.ARGB_8888)
                    plane.buffer.rewind(); padded.copyPixelsFromBuffer(plane.buffer)
                    val bitmap = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                    if (bitmap !== padded) padded.recycle()
                    padded = null
                    read(bitmap)
                } catch (_: Exception) { padded?.recycle(); stop("Screen pixels could not be read. Restart sharing or use Share → RideWise.") }
            }
        }, handler)
    }
    private fun read(bitmap: Bitmap) {
        reading = true
        scope.launch {
            try {
                // Let the ML task finish before recycling its input even if Stop cancels this job.
                val text = withContext(NonCancellable) { recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text }
                ensureActive()
                if (stopping) return@launch
                val confirmed = tracker.observe(parser.parse(text))
                session.scanned(confirmed)
                if (confirmed != shown) {
                    shown = confirmed
                    if (confirmed == null) overlay.hide()
                    else overlay.show(LiveHire("screen-offer", confirmed.provider, confirmed.result, sourceDescription = "screen OCR · check numbers"), dismissAfterMs = null)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { stop("Text recognition failed. Start a new automatic session.") }
            finally { bitmap.recycle(); reading = false; if (stopping) recognizer.close() }
        }
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 140, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_live", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 141, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_capture)
            .setContentTitle("RideWise · automatic screen assistant ON")
            .setContentText("Reading visible ride cards locally · Tap Stop to end screen sharing")
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Stop automatic assistant", stop).build()
    }
    private fun stop(message: String = "OFF · automatic assistant stopped") {
        if (stopping) return
        stopping = true
        session.stop(message)
        overlay.hide()
        scope.cancel()
        stopSelf()
    }
    override fun onDestroy() {
        if (!stopping) session.stop()
        stopping = true
        scope.cancel(); overlay.hide()
        runCatching { unregisterReceiver(screenOff) }
        projection?.unregisterCallback(callback)
        display?.release(); display = null
        reader?.close(); reader = null
        projection?.stop(); projection = null
        if (!reading) recognizer.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        const val START = "lk.ridewise.auto.START"
        const val STOP = "lk.ridewise.auto.STOP"
        const val DATA = "screen_consent"
        const val CODE = "screen_result"
        const val CHANNEL = "ridewise_auto_screen"
        private const val ID = 42
    }
}
