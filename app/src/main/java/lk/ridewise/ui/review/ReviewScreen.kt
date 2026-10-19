package lk.ridewise.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import lk.ridewise.domain.model.*
import lk.ridewise.domain.rate.HireRateInput
import lk.ridewise.domain.rate.HireRateRule
import lk.ridewise.domain.util.Decimals
import lk.ridewise.ui.components.*
import lk.ridewise.ui.live.HireRateCard
import java.math.BigDecimal

@Composable fun ReviewScreen(ride: RideRequest, onCalculate: (RideRequest) -> Unit) {
    var payout by rememberSaveable(ride.id) { mutableStateOf(Decimals.editable(ride.expectedDriverPayout)) }
    var currency by rememberSaveable(ride.id) { mutableStateOf(ride.currency.orEmpty()) }
    var pickupKm by rememberSaveable(ride.id) { mutableStateOf(Decimals.editable(ride.pickupDistanceKm)) }
    var tripKm by rememberSaveable(ride.id) { mutableStateOf(Decimals.editable(ride.passengerTripDistanceKm)) }
    var pickupMin by rememberSaveable(ride.id) { mutableStateOf(Decimals.editable(ride.estimatedPickupMinutes)) }
    var tripMin by rememberSaveable(ride.id) { mutableStateOf(Decimals.editable(ride.estimatedTripMinutes)) }
    var returnKm by rememberSaveable(ride.id) { mutableStateOf(Decimals.editable(ride.emptyReturnDistanceKm)) }
    var extra by rememberSaveable(ride.id) { mutableStateOf(Decimals.editable(ride.additionalTripCosts)) }
    var pickup by rememberSaveable(ride.id) { mutableStateOf(ride.pickupLocation.orEmpty()) }
    var destination by rememberSaveable(ride.id) { mutableStateOf(ride.destination.orEmpty()) }
    var confirmed by rememberSaveable(ride.id) { mutableStateOf(ride.userReviewed) }
    fun valid(text: String) = text.isBlank() || Decimals.parse(text)?.let { it.signum() >= 0 } == true
    val fieldsValid = listOf(payout, pickupKm, tripKm, pickupMin, tripMin, returnKm, extra).all(::valid)
    val missing = buildList {
        if (payout.isBlank()) add("expected driver payout")
        if (currency.trim().uppercase() != "LKR") add("confirmed LKR currency")
        if (pickupKm.isBlank()) add("pickup distance")
        if (tripKm.isBlank()) add("passenger trip distance")
    }
    val distancePositive = ((Decimals.parse(pickupKm) ?: BigDecimal.ZERO) + (Decimals.parse(tripKm) ?: BigDecimal.ZERO) + (Decimals.parse(returnKm) ?: BigDecimal.ZERO)).signum() > 0
    fun hint(field: RideField): String? = if (ride.sourceType == SourceType.MANUAL) null else ride.fieldConfidence[field]?.let {
        "${it.level.name.lowercase().replaceFirstChar(Char::uppercase)} extraction confidence · ${it.explanation}"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SourceBadge(ride.sourceType)
        Text("Check the details.", style = MaterialTheme.typography.headlineLarge)
        Text("Edit any field below. Amounts entered here are expected values; costs and net earnings are calculated estimates.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ride.sourceType == SourceType.DEMO) Notice("Synthetic demo · uses sample costs: fuel LKR 350/L, 14 km/L, maintenance LKR 8/km, depreciation LKR 5/km. These are not saved to your vehicle settings.")
        if (ride.sourceType == SourceType.OCR) Notice("OCR can misread text and labels. Confidence describes the parser’s interpretation, not a guarantee. Check every extracted field.", warning = true)
        ride.warnings.forEach { Notice(it, warning = true) }
        if (ride.customerFare != null || ride.displayedFare != null) Notice("Detected customer/unspecified fare: ${Decimals.money(ride.customerFare ?: ride.displayedFare)}. This is not used as driver payout.", warning = true)
        val offerAmount = ride.expectedDriverPayout ?: ride.displayedFare
        if (offerAmount != null) {
            SectionTitle("Offer rate")
            HireRateCard(HireRateRule.evaluate(HireRateInput(offerAmount, ride.currency, ride.pickupDistanceKm,
                ride.passengerTripDistanceKm, priceDescription = if (ride.expectedDriverPayout != null) "Expected driver payout" else "Displayed offer amount")),
                if (ride.expectedDriverPayout != null) "Expected payout rate" else "Displayed offer rate")
        }
        SectionTitle("Expected payout")
        NumberInput("Driver payout (LKR)", payout, { payout = it; confirmed = false }, hint(RideField.PAYOUT))
        OutlinedTextField(currency, { currency = it.take(6); confirmed = false }, Modifier.fillMaxWidth(), label = { Text("Currency") },
            supportingText = { Text("Only LKR is supported. Confirm the currency from the source.") }, singleLine = true,
            isError = currency.isNotBlank() && currency.trim().uppercase() != "LKR")
        SectionTitle("Distance & time")
        NumberInput("Pickup distance (km)", pickupKm, { pickupKm = it; confirmed = false }, hint(RideField.PICKUP_DISTANCE))
        NumberInput("Passenger trip distance (km)", tripKm, { tripKm = it; confirmed = false }, hint(RideField.TRIP_DISTANCE))
        NumberInput("Pickup time (minutes)", pickupMin, { pickupMin = it; confirmed = false }, hint(RideField.PICKUP_TIME))
        NumberInput("Trip time (minutes)", tripMin, { tripMin = it; confirmed = false }, hint(RideField.TRIP_TIME))
        NumberInput("Empty-return distance (km)", returnKm, { returnKm = it; confirmed = false }, "Optional. Blank uses your default, if configured; otherwise return is excluded.")
        NumberInput("Additional trip costs (LKR)", extra, { extra = it; confirmed = false }, "Optional. Added to default trip costs. Exclude fees already deducted from payout.")
        SectionTitle("Route · optional")
        OutlinedTextField(pickup, { pickup = it.take(160); confirmed = false }, Modifier.fillMaxWidth(), label = { Text("Pickup location") }, maxLines = 2)
        OutlinedTextField(destination, { destination = it.take(160); confirmed = false }, Modifier.fillMaxWidth(), label = { Text("Destination") }, maxLines = 2)
        Text("Keep locations general. Don’t enter passenger names, phone numbers or exact residential addresses.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            Checkbox(confirmed, { confirmed = it })
            Text("I checked the currency, driver payout, distances and other entered fields.", Modifier.padding(top = 12.dp))
        }
        if (missing.isNotEmpty()) Notice("Needed to calculate: ${missing.joinToString() }.", warning = true)
        if (!fieldsValid) Notice("Use non-negative decimal values with a decimal point. Check the highlighted fields.", warning = true)
        if (missing.isEmpty() && !distancePositive) Notice("Total estimated distance must be greater than zero.", warning = true)
        if (pickupMin.isBlank() || tripMin.isBlank()) Notice("Without both pickup and trip time, earnings per hour will be unavailable.")
        if (!confirmed) Text("Unconfirmed data produces a provisional estimate.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = {
            onCalculate(ride.copy(currency = currency.trim().uppercase(), expectedDriverPayout = Decimals.parse(payout),
                pickupDistanceKm = Decimals.parse(pickupKm), passengerTripDistanceKm = Decimals.parse(tripKm),
                estimatedPickupMinutes = Decimals.parse(pickupMin), estimatedTripMinutes = Decimals.parse(tripMin),
                emptyReturnDistanceKm = Decimals.parse(returnKm), additionalTripCosts = Decimals.parse(extra),
                pickupLocation = pickup.trim().ifBlank { null }, destination = destination.trim().ifBlank { null }, userReviewed = confirmed))
        }, enabled = fieldsValid && missing.isEmpty() && distancePositive, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Calculate Profitability") }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable fun NumberInput(label: String, value: String, onChange: (String) -> Unit, hint: String? = null, strictlyPositive: Boolean = false) {
    val number = Decimals.parse(value)
    val invalid = value.isNotBlank() && (number == null || number.signum() < 0 || (strictlyPositive && number.signum() == 0))
    OutlinedTextField(value, { onChange(it.take(24)) }, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = invalid,
        supportingText = if (invalid || hint != null) { { Text(if (invalid) if (strictlyPositive) "Enter a decimal value greater than zero." else "Enter a non-negative decimal value." else hint.orEmpty()) } } else null)
}
