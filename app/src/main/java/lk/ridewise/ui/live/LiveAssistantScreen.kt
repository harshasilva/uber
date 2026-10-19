package lk.ridewise.ui.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import lk.ridewise.domain.rate.*
import lk.ridewise.domain.util.Decimals
import lk.ridewise.live.LiveAssistantState
import lk.ridewise.ui.components.*

@Composable fun HireRateCard(result: HireRateResult, label: String = "Your LKR 40/km rule") {
    val background = when (result.verdict) { HireVerdict.GOOD -> Color(0xFF17694F); HireVerdict.BAD -> Color(0xFFA02B2B); HireVerdict.CHECK_DETAILS -> Color(0xFF705200) }
    Surface(shape = RoundedCornerShape(22.dp), color = background, contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(when (result.verdict) { HireVerdict.GOOD -> "GOOD"; HireVerdict.BAD -> "BAD"; HireVerdict.CHECK_DETAILS -> "CHECK DETAILS" }, style = MaterialTheme.typography.headlineLarge)
            result.pricePerKm?.let { Text("LKR ${Decimals.number(it)} / km", style = MaterialTheme.typography.headlineSmall) }
            if (result.pricePerKm != null) Text("${Decimals.money(result.price)} ÷ ${Decimals.number(result.totalKm)} km · pickup + passenger trip")
            result.issues.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text("Above 40 = GOOD · 40 or below = BAD\nGross rate only; fuel, commission and other costs are not deducted.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable fun LiveAssistantScreen(state: LiveAssistantState, onNotificationAccess: () -> Unit,
    onOverlayAccess: () -> Unit, onNotifications: () -> Unit, onStart: () -> Unit, onStop: () -> Unit,
    onSources: (Boolean, Boolean) -> Unit, onDemo: (Boolean) -> Unit) {
    var consent by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("A quick answer.\nYour 40/km rule.", style = MaterialTheme.typography.headlineLarge)
        Notice("Price ÷ (pickup km + passenger trip km). Above LKR 40/km shows GOOD; LKR 40/km or below shows BAD. Missing or unclear data shows CHECK DETAILS.")
        Text("After you enable it, RideWise can show a small floating badge when a selected driver app posts a ride notification. It cannot see a popup that exists only inside that app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("Driver notification sources")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Uber Driver", Modifier.padding(top = 14.dp)); Switch(state.uber, { onSources(it, state.pickme) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("PickMe Driver", Modifier.padding(top = 14.dp)); Switch(state.pickme, { onSources(state.uber, it) })
        }
        SectionTitle("One-time Android setup")
        Text("Android’s notification-access permission is broad. RideWise filters out all other apps before reading notification text. Only fare/distance numbers are kept in memory; raw messages and passenger details are not saved.", style = MaterialTheme.typography.bodyMedium)
        PermissionButton("1. Allow notification access", state.notificationAccess, onNotificationAccess)
        Text("Android may require you to enable ‘Allow restricted settings’ in RideWise’s App info first when installed from an APK. Follow the device’s own prompts; availability varies.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PermissionButton("2. Allow display over other apps", state.overlayAccess, onOverlayAccess)
        Text("The badge is draggable, has a close button and disappears after 12 seconds. Move it away from driver-app controls. It never accepts, rejects or opens a ride.", style = MaterialTheme.typography.bodyMedium)
        PermissionButton("3. Allow RideWise notifications", state.notificationsVisible, onNotifications)
        Text("A visible RideWise notification shows when the assistant is on and includes Stop assistant. It processes only new notifications; screen capture stays off.", style = MaterialTheme.typography.bodyMedium)
        SectionTitle("Live assistant")
        Text(when { state.active -> "ON · listening for selected ride notifications"
            state.enabled -> "ON · waiting for Android to connect the listener"
            else -> "OFF · not processing driver notifications" }, style = MaterialTheme.typography.titleMedium)
        state.message?.let { Notice(it, warning = true) }
        if (state.enabled) Button(onStop, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Stop live assistant") }
        else Button({ consent = true }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = state.ready) { Text("Start live assistant") }
        if (!state.ready) Text("Complete the three permission steps and select at least one source to start.", style = MaterialTheme.typography.bodyMedium)
        SectionTitle("Test the floating badge")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ onDemo(true) }, Modifier.weight(1f).heightIn(min = 52.dp), enabled = state.overlayAccess) { Text("Demo GOOD") }
            OutlinedButton({ onDemo(false) }, Modifier.weight(1f).heightIn(min = 52.dp), enabled = state.overlayAccess) { Text("Demo BAD") }
        }
        Text("Synthetic demos: LKR 600 ÷ 10 km = GOOD; LKR 350 ÷ 10 km = BAD. These do not enable notification listening.", style = MaterialTheme.typography.bodyMedium)
        state.latest?.let { hire ->
            SectionTitle(if (hire.demo) "Latest synthetic demo" else "Latest notification estimate")
            HireRateCard(hire.result, if (hire.demo) "DEMO · not a real request" else hire.provider)
        }
        Notice("Live reads Android notification text only. A ride card drawn inside Uber or PickMe is not a notification and cannot be monitored automatically. Use Capture for a visible offer card; it recognises the ordered price, away distance and trip distance shown on supported cards. This rate rule is not a guarantee of profit.", warning = true)
        Spacer(Modifier.height(16.dp))
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Start the notification assistant?") },
        text = { Text("While ON, RideWise reads new ride notifications from the selected driver apps and displays your gross-price-per-km label. Only use this with notifications you are permitted to process. Stop from this screen or the RideWise notification.") },
        confirmButton = { TextButton({ consent = false; onStart() }) { Text("Enable assistant") } },
        dismissButton = { TextButton({ consent = false }) { Text("Cancel") } })
}
@Composable private fun PermissionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp)) {
        Text(if (enabled) "$label · Allowed" else label)
    }
}
