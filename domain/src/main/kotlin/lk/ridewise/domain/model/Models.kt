package lk.ridewise.domain.model

import java.math.BigDecimal
import java.util.UUID

enum class SourceType { DEMO, MANUAL, OCR }
enum class Confidence { HIGH, MEDIUM, LOW, MISSING }
enum class RideField { PAYOUT, CURRENCY, PICKUP_DISTANCE, TRIP_DISTANCE, PICKUP_TIME, TRIP_TIME, PICKUP, DESTINATION, ADDITIONAL_COSTS }

data class FieldConfidence(val level: Confidence, val explanation: String)

data class RideRequest(
    val id: String = UUID.randomUUID().toString(),
    val capturedAt: Long = System.currentTimeMillis(),
    val sourceType: SourceType,
    val currency: String? = null,
    val expectedDriverPayout: BigDecimal? = null,
    val customerFare: BigDecimal? = null,
    val displayedFare: BigDecimal? = null,
    val pickupLocation: String? = null,
    val destination: String? = null,
    val pickupDistanceKm: BigDecimal? = null,
    val passengerTripDistanceKm: BigDecimal? = null,
    val estimatedPickupMinutes: BigDecimal? = null,
    val estimatedTripMinutes: BigDecimal? = null,
    val emptyReturnDistanceKm: BigDecimal? = null,
    val additionalTripCosts: BigDecimal? = null,
    val fieldConfidence: Map<RideField, FieldConfidence> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val userReviewed: Boolean = false,
)

/** Null means unconfigured; zero means the driver explicitly chose no cost. */
data class VehicleCostSettings(
    val fuelPricePerLitre: BigDecimal? = null,
    val fuelEconomyKmPerLitre: BigDecimal? = null,
    val maintenancePerKm: BigDecimal? = null,
    val depreciationPerKm: BigDecimal? = null,
    val otherCostsPerTrip: BigDecimal? = null,
    val defaultEmptyReturnKm: BigDecimal? = null,
) {
    companion object {
        /** Synthetic illustration only, never silently installed as a driver's settings. */
        val Demo = VehicleCostSettings("350".toBigDecimal(), "14".toBigDecimal(),
            "8".toBigDecimal(), "5".toBigDecimal(), BigDecimal.ZERO, BigDecimal.ZERO)
    }
}

enum class AnalysisStatus { COMPLETE, PROVISIONAL, INCOMPLETE }

data class ProfitabilityResult(
    val status: AnalysisStatus,
    val expectedDriverPayout: BigDecimal?,
    val totalDistanceKm: BigDecimal?,
    val totalMinutes: BigDecimal?,
    val fuelCost: BigDecimal?,
    val vehicleOperatingCost: BigDecimal?,
    val otherTripCosts: BigDecimal?,
    val netEarnings: BigDecimal?,
    val netPerKm: BigDecimal?,
    val netPerHour: BigDecimal?,
    val issues: List<String>,
    val assumptions: List<String>,
)

data class RideAnalysis(
    val id: String = UUID.randomUUID().toString(),
    val analyzedAt: Long = System.currentTimeMillis(),
    val ride: RideRequest,
    val settings: VehicleCostSettings,
    val result: ProfitabilityResult,
)
