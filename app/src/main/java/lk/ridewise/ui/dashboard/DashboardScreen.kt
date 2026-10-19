package lk.ridewise.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lk.ridewise.domain.calculator.summarizeDay
import lk.ridewise.domain.util.Decimals
import lk.ridewise.ui.RideWiseState
import lk.ridewise.ui.components.*
import lk.ridewise.ui.theme.*
import java.time.*

@Composable fun DashboardScreen(state: RideWiseState, onCapture: () -> Unit, onManual: () -> Unit,
    onDemo: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit, onAnalysis: (String) -> Unit, onLive: () -> Unit) {
    val day = LocalDate.now()
    val summary = summarizeDay(state.history, day, ZoneId.systemDefault())
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(day.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text("Make every ride count.", style = MaterialTheme.typography.headlineLarge)
            }
        }
        Card(onClick = onLive, shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Live GOOD / BAD assistant", style = MaterialTheme.typography.titleMedium)
                    Text("Price ÷ (pickup + trip km) · LKR 40 rule", style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
            }
        }
        Surface(shape = RoundedCornerShape(26.dp), color = Forest, contentColor = Color.White) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.TrendingUp, null, tint = Lime, modifier = Modifier.size(20.dp))
                    Text("TODAY’S ESTIMATED NET", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCEE3D6))
                }
                Text(Decimals.money(summary.net), style = MaterialTheme.typography.displaySmall, color = Lime)
                Text("After configured operating costs", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE1E9E2))
                HorizontalDivider(color = Color(0xFF3E6051))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${summary.analyzedCount} analyzed rides", style = MaterialTheme.typography.bodyMedium)
                    Text("${summary.completeCount} complete estimates", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFCEE3D6))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Est. net / km", Decimals.money(summary.perKm), Modifier.weight(1f), Icons.Outlined.Route)
            Metric("Est. net / hour", Decimals.money(summary.perHour), Modifier.weight(1f), Icons.Outlined.Schedule)
        }
        Text("Totals use complete saved estimates today. Demo and provisional results are excluded. Analyses do not record completed trips or actual income.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onCapture, Modifier.fillMaxWidth().heightIn(min = 58.dp), shape = RoundedCornerShape(16.dp), enabled = !state.loading) {
                Icon(Icons.Outlined.CropFree, null); Spacer(Modifier.width(10.dp)); Text("Capture Ride Request")
            }
            OutlinedButton(onManual, Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp), enabled = !state.loading) {
                Icon(Icons.Outlined.EditNote, null); Spacer(Modifier.width(10.dp)); Text("Enter Ride Manually")
            }
        }
        Card(onClick = onDemo, enabled = !state.busy && !state.loading, shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Try Demo Ride", style = MaterialTheme.typography.titleMedium)
                    Text("A sample ride. A clearer picture.", style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
            }
        }
        if (state.settings.fuelPricePerLitre == null) Notice("Make it yours: add your vehicle costs before analyzing a real ride.")
        SectionTitle("Recent analyses", "View history", onHistory)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (state.history.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Your next ride starts here", style = MaterialTheme.typography.titleMedium)
                    Text("Review a demo or enter a ride, then save your analysis to see it here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else state.history.take(3).forEach { AnalysisCard(it) { onAnalysis(it.id) } }
        TextButton(onSettings, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Tune, null); Spacer(Modifier.width(8.dp)); Text("Vehicle & cost settings") }
        Text("Review only when safely parked.", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(4.dp))
    }
}
