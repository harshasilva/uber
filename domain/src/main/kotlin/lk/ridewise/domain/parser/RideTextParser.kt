package lk.ridewise.domain.parser

import lk.ridewise.domain.model.*
import lk.ridewise.domain.util.Decimals
import java.math.BigDecimal

interface RideTextParser { fun parse(text: String, source: SourceType = SourceType.OCR): RideRequest }

/** Conservative, label-based English MVP parser. Unknown layouts stay editable and nullable. */
class LabelledRideTextParser : RideTextParser {
    private val number = "[+-]?(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?"
    private val money = Regex("(?:LKR|Rs\\.?|USD|EUR|GBP)\\s*($number)(?![0-9.,])", RegexOption.IGNORE_CASE)
    private val distance = Regex("(?<![0-9.,])($number)\\s*(km|kilomet(?:er|re)s?|m|met(?:er|re)s?)\\b", RegexOption.IGNORE_CASE)
    private val duration = Regex("(?<![0-9.,])($number)\\s*(minutes?|mins?|hours?|hrs?|h)\\b", RegexOption.IGNORE_CASE)
    private val payoutLabel = Regex("\\b(driver\\s*(?:payout|earnings)|your\\s*(?:earnings|payout)|estimated\\s*payout|payout)\\b", RegexOption.IGNORE_CASE)
    private val customerLabel = Regex("\\b(customer|passenger)\\s*(?:fare|price|pays)\\b", RegexOption.IGNORE_CASE)
    private val pickupLabel = Regex("\\b(pick[ -]?up|to\\s+pickup)\\b", RegexOption.IGNORE_CASE)
    private val tripLabel = Regex("\\b(passenger\\s*trip|trip\\s*(?:distance|duration|time)|ride\\s*(?:distance|duration|time))\\b", RegexOption.IGNORE_CASE)
    private val knownLabel = Regex("(?:payout|earnings|fare|pickup|pick-up|trip|destination|drop.off|total|fee|currency)", RegexOption.IGNORE_CASE)
    private val offerMoney = Regex("(?:LKR|Rs\\.?)\\s*($number)(?![0-9.,])|(?<![0-9.,])($number)\\s*(?:LKR|Rs\\.?)\\b", RegexOption.IGNORE_CASE)
    private val offerLeg = Regex("($number)\\s*(?:min(?:ute)?s?)\\s*(?:away\\s*)?\\(\\s*($number)\\s*(km|kilomet(?:er|re)s?|m|met(?:er|re)s?)\\s*\\)", RegexOption.IGNORE_CASE)

    private data class OfferCard(
        val amount: BigDecimal?,
        val pickupKm: BigDecimal?,
        val tripKm: BigDecimal?,
        val pickupMinutes: BigDecimal?,
        val tripMinutes: BigDecimal?,
    )

    /**
     * Uber Driver and PickMe may draw request cards over their own app instead of posting the
     * price and route as an Android notification. This recognises only the ordered, visible
     * offer-card wording: an amount plus an "away" leg followed by a trip leg. It is used for a
     * user-requested OCR capture, never for background screen monitoring.
     */
    private fun offerCard(lines: List<String>): OfferCard? {
        data class Leg(val km: BigDecimal, val minutes: BigDecimal, val index: Int, val text: String)
        fun moneyValues(line: String) = offerMoney.findAll(line).mapNotNull { match ->
            Decimals.parse(match.groupValues[1].ifBlank { match.groupValues[2] })
        }.toList()
        fun leg(index: Int, line: String): Leg? {
            val match = offerLeg.find(line) ?: return null
            val minutes = Decimals.parse(match.groupValues[1]) ?: return null
            val distance = Decimals.parse(match.groupValues[2]) ?: return null
            val unit = match.groupValues[3].lowercase()
            val km = if (unit == "m" || unit.startsWith("met")) distance.movePointLeft(3) else distance
            return Leg(km, minutes, index, line)
        }
        val amount = lines.flatMap(::moneyValues).map { it.stripTrailingZeros() }.distinct().singleOrNull()
        val legs = lines.mapIndexedNotNull(::leg)
        val pickup = legs.filter { Regex("\\baway\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }.singleOrNull()
            ?: return null
        val explicitTrip = legs.filter { it.index > pickup.index && Regex("\\btrip\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
        val following = legs.filter { it.index > pickup.index && it != pickup }
        // PickMe's card omits the word "trip" on the second leg; its position after "away" is
        // enough only when there is exactly one candidate.
        val trip = explicitTrip.singleOrNull() ?: following.singleOrNull() ?: return null
        if (amount == null) return null
        return OfferCard(amount, pickup.km, trip.km, pickup.minutes, trip.minutes)
    }

    override fun parse(text: String, source: SourceType): RideRequest {
        val lines = text.replace('\u00a0', ' ').lineSequence().map { it.trim().replace(Regex("[ \\t]+"), " ") }
            .filter { it.isNotBlank() }.take(250).toList()
        val offerCard = if (source == SourceType.OCR) offerCard(lines) else null
        val windows = lines.mapIndexed { index, line ->
            val next = lines.getOrNull(index + 1)
            if (knownLabel.containsMatchIn(line) && next != null && !knownLabel.containsMatchIn(next)) "$line $next" else line
        }
        val confidence = mutableMapOf<RideField, FieldConfidence>()
        val warnings = mutableListOf<String>()
        fun select(field: RideField, candidates: List<BigDecimal>, label: String): BigDecimal? {
            val distinct = candidates.map { it.stripTrailingZeros() }.distinct()
            val value = distinct.singleOrNull()
            confidence[field] = when {
                distinct.isEmpty() -> FieldConfidence(Confidence.MISSING, "$label was not clearly labelled.")
                distinct.size > 1 -> FieldConfidence(Confidence.LOW, "Conflicting $label values. Enter the correct value.")
                else -> FieldConfidence(Confidence.MEDIUM, "A labelled value was found; check it against the source.")
            }
            if (distinct.size > 1) warnings += "Conflicting $label values were left blank."
            return value
        }
        fun amounts(label: Regex): List<BigDecimal> = windows.filter { label.containsMatchIn(it) }.flatMap { line ->
            // Only inspect the labelled suffix, so a different earlier fare is never reused.
            val suffix = line.substring(label.find(line)!!.range.last + 1).trimStart(' ', ':', '=')
            val matches = money.findAll(suffix).mapNotNull { Decimals.parse(it.groupValues[1]) }.toList()
            if (matches.isNotEmpty()) matches else Regex("^($number)(?![0-9.,])(?:\\s*(?:LKR|Rs\\.?)\\b)?$", RegexOption.IGNORE_CASE)
                .matchEntire(suffix)?.groupValues?.get(1)?.let { listOfNotNull(Decimals.parse(it)) }.orEmpty()
        }
        val currencies = Regex("\\b(?:LKR|USD|EUR|GBP|INR)(?=\\s|[0-9]|$)|\\bRs\\.?(?=\\s|[0-9])", RegexOption.IGNORE_CASE)
            .findAll(text).map { if (it.value.startsWith("Rs", true)) "LKR" else it.value.uppercase() }.toSet()
        val currency = currencies.singleOrNull()
        confidence[RideField.CURRENCY] = FieldConfidence(if (currency == "LKR") Confidence.MEDIUM else Confidence.LOW,
            if (currency == "LKR") "Check that Rs means Sri Lankan rupees." else "Confirm currency; only LKR calculations are supported.")
        if (currencies.size > 1) warnings += "Multiple currencies found. No currency was selected."
        val payout = select(RideField.PAYOUT, amounts(payoutLabel), "driver payout")
        val customer = amounts(customerLabel).map { it.stripTrailingZeros() }.distinct().singleOrNull()
        val labelledDisplayed = windows.filter { !payoutLabel.containsMatchIn(it) && !customerLabel.containsMatchIn(it) }
            .flatMap { money.findAll(it).mapNotNull { m -> Decimals.parse(m.groupValues[1]) }.toList() }
            .map { it.stripTrailingZeros() }.distinct().singleOrNull()
        val displayed = labelledDisplayed ?: offerCard?.amount
        if (payout == null && (customer != null || displayed != null)) warnings += "A displayed fare is not a confirmed driver payout. Enter the payout separately."
        fun distances(label: Regex): List<BigDecimal> = windows.filter {
            label.containsMatchIn(it) && !Regex("\\b(total|including|combined)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it)
        }.flatMap { line -> distance.findAll(line).mapNotNull {
            Decimals.parse(it.groupValues[1])?.let { value ->
                if (it.groupValues[2].lowercase().let { unit -> unit == "m" || unit.startsWith("met") }) value.movePointLeft(3) else value
            }
        }.toList() }
        fun times(label: Regex): List<BigDecimal> = windows.filter {
            label.containsMatchIn(it) && !Regex("\\b(total|including|combined)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it)
        }.flatMap { line ->
            val matches = duration.findAll(line).toList()
            val hours = matches.count { it.groupValues[2].startsWith("h", true) }
            val minutes = matches.size - hours
            val values = matches.mapNotNull { match ->
                val value = Decimals.parse(match.groupValues[1]) ?: return@mapNotNull null
                if (match.groupValues[2].startsWith("h", true)) value * BigDecimal("60") else value
            }
            // "1 hour 20 minutes" is a duration; "4 min / 6 min" is a conflict.
            if (hours == 1 && minutes == 1) listOf(values.fold(BigDecimal.ZERO, BigDecimal::add)) else values
        }
        fun cardValue(field: RideField, value: BigDecimal?, label: String): BigDecimal? {
            if (value != null) confidence[field] = FieldConfidence(Confidence.LOW,
                "Recognised from an ordered offer card; confirm the $label on screen.")
            return value
        }
        val pickupDistance = select(RideField.PICKUP_DISTANCE, distances(pickupLabel), "pickup distance")
            ?: cardValue(RideField.PICKUP_DISTANCE, offerCard?.pickupKm, "pickup distance")
        val tripDistance = select(RideField.TRIP_DISTANCE, distances(tripLabel), "passenger trip distance")
            ?: cardValue(RideField.TRIP_DISTANCE, offerCard?.tripKm, "passenger trip distance")
        val pickupMinutes = select(RideField.PICKUP_TIME, times(pickupLabel), "pickup time")
            ?: cardValue(RideField.PICKUP_TIME, offerCard?.pickupMinutes, "pickup time")
        val tripMinutes = select(RideField.TRIP_TIME, times(tripLabel), "trip time")
            ?: cardValue(RideField.TRIP_TIME, offerCard?.tripMinutes, "trip time")
        if (offerCard != null) warnings += "Visible offer-card layout recognised. Confirm the displayed amount and route before using it."
        if (Regex("\\btotal\\s*(?:distance)?", RegexOption.IGNORE_CASE).containsMatchIn(text))
            warnings += "A total-distance label was ignored. Confirm pickup and passenger distances separately."
        fun location(field: RideField, pattern: String): String? {
            val regex = Regex("^(?:$pattern)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
            val matches = lines.mapNotNull { regex.find(it)?.groupValues?.get(1) }.filter {
                !distance.containsMatchIn(it) && !duration.containsMatchIn(it)
            }.distinct()
            confidence[field] = FieldConfidence(if (matches.size == 1) Confidence.MEDIUM else Confidence.MISSING,
                "Optional location label; remove any unrelated identifying details.")
            return matches.singleOrNull()?.take(160)
        }
        val feeLabel = Regex("additional\\s*driver\\s*(?:fee|cost)", RegexOption.IGNORE_CASE)
        val extra = select(RideField.ADDITIONAL_COSTS, amounts(feeLabel), "additional driver cost")
        if (Regex("\\b(?:fee|commission|toll)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) && extra == null)
            warnings += "Fees were not automatically deducted. Confirm whether they are already included in payout."
        return RideRequest(sourceType = source, currency = currency, expectedDriverPayout = payout,
            customerFare = customer, displayedFare = displayed,
            pickupLocation = location(RideField.PICKUP, "pickup(?: location)?|pick-up(?: location)?"),
            destination = location(RideField.DESTINATION, "destination|drop-off|dropoff"),
            pickupDistanceKm = pickupDistance, passengerTripDistanceKm = tripDistance,
            estimatedPickupMinutes = pickupMinutes, estimatedTripMinutes = tripMinutes,
            additionalTripCosts = extra, fieldConfidence = confidence, warnings = warnings.distinct())
    }
}
