package lk.ridewise.ui.capture

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lk.ridewise.capture.screencapture.CaptureState
import lk.ridewise.ui.components.*

@Composable fun CaptureScreen(state: CaptureState, busy: Boolean, onDemo: () -> Unit, onManual: () -> Unit,
    onStart: () -> Unit, onCaptureOnce: () -> Unit, onStop: () -> Unit, onNotificationSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("A little context.\nA better estimate.", style = MaterialTheme.typography.headlineLarge)
        Text("Choose how to bring in your ride details. You’ll review everything before calculating.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        CaptureOption("Try Demo Ride", "Synthetic request · works offline · sample costs", Icons.Outlined.AutoAwesome, onDemo, !busy)
        CaptureOption("Enter Ride Manually", "Enter only the information you need", Icons.Outlined.EditNote, onManual, !busy)
        SectionTitle("Fastest for a visible ride card")
        Notice("Take a normal phone screenshot of the visible Uber/PickMe card. In the screenshot preview, tap Share and choose RideWise. The image is read on this device, then discarded; the review page opens with GOOD or BAD.")
        SectionTitle("One screen, with your permission")
        Notice("Screen access lets RideWise read one screen you explicitly request. Android asks for consent each session. Text recognition stays on this device; screenshots are held briefly in memory and never saved.")
        Text("For an Uber or PickMe offer card, keep the price and both route rows visible: the row ending in ‘away’ is pickup, and the following route row is the passenger trip. The result opens for you to check.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("1. Start a session and choose an app or screen in Android’s prompt.\n2. Open the permitted ride screen yourself.\n3. In the RideWise notification, tap Capture once and close the shade. Capture happens after 2 seconds.\n4. Return here to review the extracted fields.",
            style = MaterialTheme.typography.bodyLarge)
        Text("Choose the entire screen if a permitted floating popup sits outside the selected app. Protected screens may appear blank. RideWise does not bypass those protections.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (state) {
            is CaptureState.ReadyToCapture -> {
                Notice("Screen session active. Use the notification to capture the visible screen. This session expires after 2 minutes.")
                Button(onCaptureOnce, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Capture once in 2 seconds") }
            }
            is CaptureState.Capturing -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Capturing one screen…") }
            is CaptureState.Processing -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Recognizing text on your device…") }
            is CaptureState.Error -> Notice(state.message, warning = true)
            is CaptureState.Extracted -> Text("Text extracted. Opening review…")
            else -> Unit
        }
        if (state is CaptureState.ReadyToCapture || state is CaptureState.Capturing || state is CaptureState.Processing) {
            OutlinedButton(onStop, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Stop screen session") }
        } else {
            Button(onStart, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Icon(Icons.Outlined.CropFree, null); Spacer(Modifier.width(8.dp)); Text("Start screen session") }
        }
        TextButton(onNotificationSettings, Modifier.fillMaxWidth()) { Text("Screen capture notification settings") }
        Notice("Only capture content you’re permitted to use. No live driver-app integration has been tested or authorized. RideWise never accepts or rejects requests.")
        Spacer(Modifier.height(8.dp))
    }
}
@Composable private fun CaptureOption(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, enabled: Boolean) {
    Card(onClick, Modifier.fillMaxWidth(), enabled = enabled, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}
