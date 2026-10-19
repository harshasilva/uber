package lk.ridewise.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lk.ridewise.domain.model.VehicleCostSettings
import lk.ridewise.domain.util.Decimals
import lk.ridewise.ui.components.*
import lk.ridewise.ui.review.NumberInput

@Composable fun SettingsScreen(settings: VehicleCostSettings, forAnalysis: Boolean, busy: Boolean, onSave: (VehicleCostSettings) -> Unit) {
    val key = settings.toString()
    var fuel by rememberSaveable(key) { mutableStateOf(Decimals.editable(settings.fuelPricePerLitre)) }
    var economy by rememberSaveable(key) { mutableStateOf(Decimals.editable(settings.fuelEconomyKmPerLitre)) }
    var maintenance by rememberSaveable(key) { mutableStateOf(Decimals.editable(settings.maintenancePerKm)) }
    var depreciation by rememberSaveable(key) { mutableStateOf(Decimals.editable(settings.depreciationPerKm)) }
    var other by rememberSaveable(key) { mutableStateOf(Decimals.editable(settings.otherCostsPerTrip)) }
    var empty by rememberSaveable(key) { mutableStateOf(Decimals.editable(settings.defaultEmptyReturnKm)) }
    var reset by rememberSaveable { mutableStateOf(false) }
    val invalid = listOf(fuel, maintenance, depreciation, other, empty).any { it.isNotBlank() && (Decimals.parse(it)?.signum() ?: -1) < 0 } ||
        (economy.isNotBlank() && (Decimals.parse(economy)?.signum() ?: 0) <= 0)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (forAnalysis) "Refine this estimate." else "Your vehicle.\nYour real costs.", style = MaterialTheme.typography.headlineLarge)
        Text(if (forAnalysis) "Changes apply to this analysis only. Save the revised result to history afterwards."
            else "Set the costs that match your vehicle. They’ll be used for new manual and captured rides.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Notice("No financial defaults are assumed. Blank means unknown and excludes that cost with a warning. Enter 0 only if you intend no cost.")
        SectionTitle("Fuel")
        NumberInput("Fuel price (LKR / litre)", fuel, { fuel = it })
        NumberInput("Fuel economy (km / litre)", economy, { economy = it }, strictlyPositive = true)
        SectionTitle("Vehicle operating costs")
        NumberInput("Maintenance (LKR / km)", maintenance, { maintenance = it })
        NumberInput("Depreciation / operating cost (LKR / km)", depreciation, { depreciation = it })
        SectionTitle("Per-ride assumptions")
        NumberInput("Other default trip costs (LKR)", other, { other = it }, "Do not include costs already deducted from the driver payout.")
        NumberInput("Default empty return (km)", empty, { empty = it }, "Optional. A ride-specific distance overrides this default.")
        Notice("Fuel and operating costs use the same total distance, including pickup and any included empty return. Revisit these inputs when your costs change.")
        Button(onClick = { onSave(VehicleCostSettings(Decimals.parse(fuel), Decimals.parse(economy), Decimals.parse(maintenance),
            Decimals.parse(depreciation), Decimals.parse(other), Decimals.parse(empty))) }, enabled = !invalid && !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (forAnalysis) "Save & recalculate" else "Save vehicle settings") }
        OutlinedButton({ reset = true }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Reset to defaults") }
        Text("Default state: all costs unconfigured. Demo samples use clearly labelled illustrative values.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("Reset cost inputs?") },
        text = { Text("All fields will be cleared. Save to apply the unconfigured defaults.") },
        confirmButton = { TextButton({ fuel = ""; economy = ""; maintenance = ""; depreciation = ""; other = ""; empty = ""; reset = false }) { Text("Reset inputs") } },
        dismissButton = { TextButton({ reset = false }) { Text("Cancel") } })
}
