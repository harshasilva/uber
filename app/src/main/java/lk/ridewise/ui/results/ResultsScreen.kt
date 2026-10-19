package lk.ridewise.ui.results

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lk.ridewise.domain.model.*
import lk.ridewise.domain.util.Decimals
import lk.ridewise.ui.components.*
import lk.ridewise.ui.live.HireRateCard
import lk.ridewise.domain.rate.*

@Composable fun ResultsScreen(analysis: RideAnalysis, saved: Boolean, busy: Boolean, onSave: () -> Unit, onHome: () -> Unit,
    onEdit: () -> Unit, onAssumptions: () -> Unit, onDelete: () -> Unit) {
    val result = analysis.result
    var delete by rememberSaveable(analysis.id) { mutableStateOf(false) }
    val complete = result.status == AnalysisStatus.COMPLETE
    val color = when { result.netEarnings?.signum() == -1 -> MaterialTheme.colorScheme.error
        !complete -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SourceBadge(analysis.ride.sourceType)
            Text(if (saved) "Saved locally" else "Unsaved estimate", style = MaterialTheme.typography.labelSmall)
        }
        Text("The numbers\nbehind the ride.", style = MaterialTheme.typography.headlineLarge)
        HireRateCard(HireRateRule.evaluate(HireRateInput(analysis.ride.expectedDriverPayout, analysis.ride.currency,
            analysis.ride.pickupDistanceKm, analysis.ride.passengerTripDistanceKm,
            if (analysis.ride.userReviewed) emptyList() else listOf("Confirm the ride fields before using this label."), "Expected driver payout")))
        Surface(shape = RoundedCornerShape(24.dp), color = if (complete) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (complete) "ESTIMATED NET EARNINGS" else "PARTIAL NET ESTIMATE", style = MaterialTheme.typography.labelSmall)
                Text(Decimals.money(result.netEarnings), style = MaterialTheme.typography.displaySmall, color = color)
                Text(when (result.status) {
                    AnalysisStatus.COMPLETE -> "Complete estimate · based on your reviewed inputs"
                    AnalysisStatus.PROVISIONAL -> "Provisional · check unconfirmed inputs and excluded costs"
                    AnalysisStatus.INCOMPLETE -> "Incomplete · essential inputs are missing or invalid"
                }, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("Estimated values are not guaranteed profit. Driver payout is an expected input, not a record of actual income.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (analysis.ride.sourceType == SourceType.DEMO) Notice("Synthetic demo. This analysis uses illustrative costs and is excluded from dashboard totals.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Estimated net / km", Decimals.money(result.netPerKm), Modifier.weight(1f))
            Metric("Estimated net / hour", Decimals.money(result.netPerHour), Modifier.weight(1f))
        }
        SectionTitle("Cost breakdown", "Edit ride", onEdit)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp)) {
                DetailRow("Expected driver payout · entered", Decimals.money(result.expectedDriverPayout))
                HorizontalDivider()
                DetailRow("Fuel · calculated", result.fuelCost?.let { Decimals.money(it) } ?: "Excluded · unknown")
                DetailRow("Vehicle operating · calculated", result.vehicleOperatingCost?.let { Decimals.money(it) } ?: "Excluded · unknown")
                DetailRow("Other trip costs · entered totals", result.otherTripCosts?.let { Decimals.money(it) } ?: "Excluded · unknown")
                HorizontalDivider()
                DetailRow(if (complete) "Estimated net" else "Partial net estimate", Decimals.money(result.netEarnings), true)
            }
        }
        DetailRow("Total estimated distance", "${Decimals.number(result.totalDistanceKm)} km")
        DetailRow("Pickup + passenger time", result.totalMinutes?.let { "${Decimals.number(it, 1)} minutes" } ?: "Unavailable")
        DetailRow("Pickup", analysis.ride.pickupLocation ?: "Not supplied")
        DetailRow("Destination", analysis.ride.destination ?: "Not supplied")
        SectionTitle("Data & confidence")
        Notice(if (analysis.ride.userReviewed) "You marked the entered fields as reviewed. Estimates still depend on those inputs."
            else "The entered fields have not been confirmed by the driver. Review them before relying on this estimate.", warning = !analysis.ride.userReviewed)
        if (analysis.ride.sourceType == SourceType.OCR) {
            Text("OCR extraction confidence", style = MaterialTheme.typography.titleMedium)
            analysis.ride.fieldConfidence.forEach { (field, c) ->
                DetailRow(field.name.lowercase().replace('_', ' '), c.level.name.lowercase())
            }
        }
        result.issues.forEach { Notice(it, warning = true) }
        analysis.ride.warnings.forEach { Text("Source note: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        SectionTitle("Calculation assumptions", "Edit costs", onAssumptions)
        DetailRow("Fuel price / litre", Decimals.money(analysis.settings.fuelPricePerLitre))
        DetailRow("Fuel economy", analysis.settings.fuelEconomyKmPerLitre?.let { "${Decimals.number(it)} km/L" } ?: "Unconfigured")
        DetailRow("Maintenance / km", Decimals.money(analysis.settings.maintenancePerKm))
        DetailRow("Depreciation / km", Decimals.money(analysis.settings.depreciationPerKm))
        DetailRow("Default trip costs", Decimals.money(analysis.settings.otherCostsPerTrip))
        DetailRow("Included empty return", (analysis.ride.emptyReturnDistanceKm ?: analysis.settings.defaultEmptyReturnKm)?.let { "${Decimals.number(it)} km" } ?: "Not included")
        result.assumptions.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (analysis.ride.sourceType == SourceType.OCR && !analysis.ride.userReviewed) Notice("Edit the ride and confirm the captured fields before saving them to history.", warning = true)
        Button(if (saved) onHome else onSave, Modifier.fillMaxWidth().heightIn(min = 56.dp),
            enabled = !busy && (saved || analysis.ride.sourceType != SourceType.OCR || analysis.ride.userReviewed)) {
            Icon(if (saved) Icons.Outlined.Home else Icons.Outlined.Save, null)
            Spacer(Modifier.width(8.dp)); Text(if (saved) "Saved · return to dashboard" else "Save analysis & return")
        }
        if (saved) OutlinedButton({ delete = true }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !busy) { Text("Delete this analysis") }
        Spacer(Modifier.height(16.dp))
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete this analysis?") },
        text = { Text("The saved ride details and calculation will be removed from this device.") },
        confirmButton = { TextButton({ delete = false; onDelete() }) { Text("Delete analysis") } },
        dismissButton = { TextButton({ delete = false }) { Text("Cancel") } })
}
