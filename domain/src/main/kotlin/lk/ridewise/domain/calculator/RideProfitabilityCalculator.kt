package lk.ridewise.domain.calculator

import lk.ridewise.domain.model.*
import java.math.BigDecimal
import java.math.RoundingMode

/** Pure decimal arithmetic. Retains precision until display; a missing cost is never a zero. */
class RideProfitabilityCalculator {
    fun calculate(ride: RideRequest, settings: VehicleCostSettings): ProfitabilityResult {
        val issues = mutableListOf<String>()
        val assumptions = mutableListOf<String>()
        fun nonNegative(value: BigDecimal?, label: String): BigDecimal? {
            if (value == null) return null
            if (value.signum() < 0) { issues += "$label cannot be negative."; return null }
            return value
        }
        val pickup = nonNegative(ride.pickupDistanceKm, "Pickup distance")
        val trip = nonNegative(ride.passengerTripDistanceKm, "Passenger trip distance")
        if (pickup == null) issues += "Pickup distance is required for a consistent distance estimate."
        if (trip == null) issues += "Passenger trip distance is required for a consistent distance estimate."
        val returnInput = ride.emptyReturnDistanceKm ?: settings.defaultEmptyReturnKm
        val returnDistance = nonNegative(returnInput, "Empty-return distance")
        if (returnInput == null) assumptions += "Empty return is excluded; no return distance was supplied."
        val distance = if (pickup != null && trip != null && (returnInput == null || returnDistance != null)) {
            (pickup + trip + (returnDistance ?: BigDecimal.ZERO)).takeIf { it.signum() > 0 }
                .also { if (it == null) issues += "Total distance must be greater than zero." }
        } else null
        assumptions += "All per-kilometre metrics use pickup + passenger trip + included empty return."
        val pickupTime = nonNegative(ride.estimatedPickupMinutes, "Pickup time")
        val tripTime = nonNegative(ride.estimatedTripMinutes, "Trip time")
        val minutes = if (pickupTime != null && tripTime != null) {
            (pickupTime + tripTime).takeIf { it.signum() > 0 }
        } else null
        if (minutes == null) issues += "Hourly estimate unavailable: enter positive total pickup and trip time."
        if (returnDistance != null && returnDistance.signum() > 0) {
            assumptions += "Hourly estimate includes return costs but excludes unprovided return time."
        }
        val payout = nonNegative(ride.expectedDriverPayout, "Driver payout")
        if (payout == null) issues += "Confirm the expected driver payout; customer fare is never substituted."
        if (ride.currency != "LKR") issues += "Confirm LKR currency. This MVP does not convert other currencies."
        if (!ride.userReviewed) issues += "Driver review is required before relying on these estimates."

        val price = nonNegative(settings.fuelPricePerLitre, "Fuel price")
        val economy = settings.fuelEconomyKmPerLitre?.takeIf { it.signum() > 0 }
        if (price == null || economy == null) issues += "Fuel cost excluded: enter a non-negative fuel price and fuel economy greater than zero."
        val fuel = if (distance != null && price != null && economy != null)
            distance.multiply(price).divide(economy, 12, RoundingMode.HALF_UP) else null
        val maintenance = nonNegative(settings.maintenancePerKm, "Maintenance cost")
        val depreciation = nonNegative(settings.depreciationPerKm, "Depreciation cost")
        if (maintenance == null) issues += "Maintenance cost is excluded until configured (enter 0 if none)."
        if (depreciation == null) issues += "Depreciation cost is excluded until configured (enter 0 if none)."
        val operating = if (distance != null && (maintenance != null || depreciation != null))
            distance * ((maintenance ?: BigDecimal.ZERO) + (depreciation ?: BigDecimal.ZERO)) else null
        val defaultOther = nonNegative(settings.otherCostsPerTrip, "Default trip costs")
        if (defaultOther == null) issues += "Default trip costs are excluded until configured (enter 0 if none)."
        val rideOther = nonNegative(ride.additionalTripCosts, "Additional trip costs")
        val other = if (defaultOther != null || rideOther != null)
            (defaultOther ?: BigDecimal.ZERO) + (rideOther ?: BigDecimal.ZERO) else null
        assumptions += "Additional trip costs are added to default trip costs; do not enter fees already deducted from payout."
        val net = if (payout != null && distance != null && ride.currency == "LKR")
            payout - (fuel ?: BigDecimal.ZERO) - (operating ?: BigDecimal.ZERO) - (other ?: BigDecimal.ZERO) else null
        val status = when {
            net == null -> AnalysisStatus.INCOMPLETE
            issues.isNotEmpty() -> AnalysisStatus.PROVISIONAL
            else -> AnalysisStatus.COMPLETE
        }
        if (ride.sourceType == SourceType.DEMO) assumptions += "Synthetic demo data and sample vehicle costs; no actual earnings are recorded."
        return ProfitabilityResult(status, payout, distance, minutes, fuel, operating, other, net,
            if (net != null && distance != null) net.divide(distance, 12, RoundingMode.HALF_UP) else null,
            if (net != null && minutes != null) net.multiply(BigDecimal("60")).divide(minutes, 12, RoundingMode.HALF_UP) else null,
            issues.distinct(), assumptions)
    }
}
