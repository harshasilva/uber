package lk.ridewise

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import lk.ridewise.capture.ocr.MlKitOnDeviceOcr
import lk.ridewise.capture.share.SharedImageRideRequestSource
import lk.ridewise.capture.screencapture.*
import lk.ridewise.domain.parser.LabelledRideTextParser
import lk.ridewise.ui.RideWiseViewModel
import lk.ridewise.ui.navigation.RideWiseApp
import lk.ridewise.ui.theme.RideWiseTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as RideWiseApplication
    private var openCapture by mutableStateOf(false)
    private var openLive by mutableStateOf(false)
    private val liveNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        app.liveAssistant.refreshPermissions()
    }
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                // Only start the foreground service after fresh explicit MediaProjection consent.
                ContextCompat.startForegroundService(this, Intent(this, ScreenCaptureService::class.java)
                    .setAction(ScreenCaptureService.ACTION_START)
                    .putExtra(ScreenCaptureService.EXTRA_CODE, result.resultCode)
                    .putExtra(ScreenCaptureService.EXTRA_DATA, result.data))
            } catch (_: Exception) { app.captureSession.update(CaptureState.Error("Android could not start the capture service. Try a new session or manual entry.")) }
        } else app.captureSession.permissionDenied()
    }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCapture()
        else app.captureSession.update(CaptureState.Error("Notifications were denied. RideWise needs visible Capture once and Stop controls for screen access. Enable notifications in settings or use demo/manual entry."))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openCapture = intent.getBooleanExtra("open_capture", false)
        openLive = intent.getBooleanExtra("open_live", false)
        setContent {
            RideWiseTheme {
                val model: RideWiseViewModel = viewModel(factory = viewModelFactory {
                    initializer { RideWiseViewModel(app.repository, app.captureSession, createSavedStateHandle()) }
                })
                RideWiseApp(model, app.liveStore, openLive, { openLive = false },
                    { openSystemSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    { openSystemSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) },
                    ::requestLiveNotifications, { app.liveAssistant.start() }, { app.liveAssistant.stop() },
                    { uber, pickme -> app.liveAssistant.selectSources(uber, pickme) }, { app.liveAssistant.demo(it) },
                    openCapture, { openCapture = false }, ::startCapture,
                    { command(ScreenCaptureService.ACTION_CAPTURE) }, { command(ScreenCaptureService.ACTION_STOP) },
                    { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) })
            }
        }
        handleSharedImage(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        openCapture = intent.getBooleanExtra("open_capture", false) || isSharedImage(intent)
        openLive = intent.getBooleanExtra("open_live", false)
        handleSharedImage(intent)
    }
    override fun onResume() { super.onResume(); app.liveAssistant.refreshPermissions() }
    private fun openSystemSettings(intent: Intent) {
        try { startActivity(intent) }
        catch (_: Exception) { app.liveStore.message("This settings screen is unavailable. Open Android Settings → Apps → RideWise and review special app access.") }
    }
    private fun requestLiveNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            liveNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else openSystemSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }
    private fun startCapture() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (!NotificationManagerCompat.from(this).areNotificationsEnabled() ||
            getSystemService(NotificationManager::class.java).getNotificationChannel(ScreenCaptureService.CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE)
            app.captureSession.update(CaptureState.Error("Enable RideWise notifications in Android settings to access the Capture once and Stop controls, or use manual entry."))
        else launchConsent()
    }
    private fun launchConsent() {
        try { consent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent()) }
        catch (_: Exception) { app.captureSession.update(CaptureState.Error("Screen sharing is unavailable on this device. Demo and manual entry still work.")) }
    }
    private fun command(action: String) {
        // STOP is safe even when Android already destroyed the session.
        try { startService(Intent(this, ScreenCaptureService::class.java).setAction(action)) }
        catch (_: Exception) { app.captureSession.update(CaptureState.Error("Screen session is unavailable. Start a fresh session.")) }
    }
    private fun isSharedImage(intent: Intent) = intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true
    @Suppress("DEPRECATION")
    private fun handleSharedImage(intent: Intent) {
        if (!isSharedImage(intent)) return
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (uri == null) {
            app.captureSession.update(CaptureState.Error("No image was shared. Take a screenshot of the ride card and share that image to RideWise."))
            return
        }
        openCapture = true
        app.captureSession.update(CaptureState.Processing)
        lifecycleScope.launch {
            try {
                val ride = SharedImageRideRequestSource(contentResolver, uri, MlKitOnDeviceOcr(), LabelledRideTextParser()).capture()
                app.captureSession.update(CaptureState.Extracted(ride))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                app.captureSession.update(CaptureState.Error("RideWise could not read that image. Share a clear ride card or enter the price and distances manually."))
            }
        }
    }
}
