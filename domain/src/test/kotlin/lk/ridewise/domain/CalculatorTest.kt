package lk.ridewise.domain

import lk.ridewise.domain.calculator.*
import lk.ridewise.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.*

class CalculatorTest {
    private val calculator = RideProfitabilityCalculator()
    private val settings = VehicleCostSettings.Demo
    private val ride = RideRequest(sourceType = SourceType.MANUAL, currency = "LKR", expectedDriverPayout = bd("1800"),
        pickupDistanceKm = bd("1.5"), passengerTripDistanceKm = bd("8.5"), estimatedPickupMinutes = bd("5"),
        estimatedTripMinutes = bd("25"), userReviewed = true)
    private fun bd(value: String) = value.toBigDecimal()
    private fun equal(expected: String, actual: BigDecimal?) { assertNotNull(actual); assertEquals(0, bd(expected).compareTo(actual)) }

    @Test fun `fuel cost uses total distance`() { equal("250", calculator.calculate(ride, settings).fuelCost) }
    @Test fun `operating cost combines maintenance and depreciation`() { equal("130", calculator.calculate(ride, settings).vehicleOperatingCost) }
    @Test fun `net deducts every configured cost`() {
        val result = calculator.calculate(ride.copy(additionalTripCosts = bd("20")), settings.copy(otherCostsPerTrip = bd("30")))
        equal("1370", result.netEarnings); equal("50", result.otherTripCosts)
    }
    @Test fun `per kilometre uses same full distance`() { equal("142", calculator.calculate(ride, settings).netPerKm) }
    @Test fun `hourly uses pickup plus passenger time`() { equal("2840", calculator.calculate(ride, settings).netPerHour) }
    @Test fun `complete reviewed request is complete`() { assertEquals(AnalysisStatus.COMPLETE, calculator.calculate(ride, settings).status) }
    @Test fun `return distance affects all distance based costs and metrics`() {
        val result = calculator.calculate(ride.copy(emptyReturnDistanceKm = bd("2")), settings)
        equal("12", result.totalDistanceKm); equal("300", result.fuelCost); equal("156", result.vehicleOperatingCost)
        equal("1344", result.netEarnings); equal("112", result.netPerKm)
        assertTrue(result.assumptions.any { it.contains("return time") })
    }
    @Test fun `explicit zero return overrides vehicle default`() { equal("10", calculator.calculate(ride.copy(emptyReturnDistanceKm = bd("0")), settings.copy(defaultEmptyReturnKm = bd("10"))).totalDistanceKm) }
    @Test fun `missing return is disclosed`() {
        val r = calculator.calculate(ride, settings.copy(defaultEmptyReturnKm = null))
        assertTrue(r.assumptions.any { it.contains("Empty return is excluded") }); equal("10", r.totalDistanceKm)
    }
    @Test fun `no time means no hourly metric`() { assertNull(calculator.calculate(ride.copy(estimatedTripMinutes = null), settings).netPerHour) }
    @Test fun `zero time never divides by zero`() { assertNull(calculator.calculate(ride.copy(estimatedTripMinutes = bd("0"), estimatedPickupMinutes = bd("0")), settings).netPerHour) }
    @Test fun `zero distance never divides by zero`() {
        val r = calculator.calculate(ride.copy(pickupDistanceKm = bd("0"), passengerTripDistanceKm = bd("0")), settings)
        assertNull(r.netPerKm); assertNull(r.netEarnings); assertEquals(AnalysisStatus.INCOMPLETE, r.status)
    }
    @Test fun `invalid economy excludes fuel and marks provisional`() {
        listOf("0", "-1").forEach { val r = calculator.calculate(ride, settings.copy(fuelEconomyKmPerLitre = bd(it)))
            assertNull(r.fuelCost); assertEquals(AnalysisStatus.PROVISIONAL, r.status); assertTrue(r.issues.any { it.contains("Fuel cost excluded") }) }
    }
    @Test fun `negative distance is incomplete`() { assertNull(calculator.calculate(ride.copy(pickupDistanceKm = bd("-1")), settings).netEarnings) }
    @Test fun `negative return is not silently zero`() { assertNull(calculator.calculate(ride.copy(emptyReturnDistanceKm = bd("-1")), settings).totalDistanceKm) }
    @Test fun `negative payout is invalid`() { assertNull(calculator.calculate(ride.copy(expectedDriverPayout = bd("-1")), settings).netEarnings) }
    @Test fun `negative cost is excluded with issue`() {
        val r = calculator.calculate(ride, settings.copy(maintenancePerKm = bd("-1")))
        assertEquals(AnalysisStatus.PROVISIONAL, r.status); assertTrue(r.issues.any { it.contains("cannot be negative") })
    }
    @Test fun `customer fare never substitutes for payout`() {
        val r = calculator.calculate(ride.copy(expectedDriverPayout = null, customerFare = bd("2500")), settings)
        assertNull(r.netEarnings); assertEquals(AnalysisStatus.INCOMPLETE, r.status)
    }
    @Test fun `missing distances cannot produce partial distance`() { assertNull(calculator.calculate(ride.copy(pickupDistanceKm = null), settings).totalDistanceKm) }
    @Test fun `unknown costs yield disclosed partial net`() {
        val r = calculator.calculate(ride, VehicleCostSettings())
        equal("1800", r.netEarnings); assertNull(r.fuelCost); assertNull(r.vehicleOperatingCost)
        assertEquals(AnalysisStatus.PROVISIONAL, r.status); assertTrue(r.issues.size >= 4)
    }
    @Test fun `unknown currency never produces LKR earnings`() { assertNull(calculator.calculate(ride.copy(currency = "USD"), settings).netEarnings) }
    @Test fun `unreviewed input is provisional`() { assertEquals(AnalysisStatus.PROVISIONAL, calculator.calculate(ride.copy(userReviewed = false), settings).status) }
    @Test fun `negative net remains negative`() {
        val r = calculator.calculate(ride.copy(expectedDriverPayout = bd("100")), settings)
        equal("-280", r.netEarnings); equal("-28", r.netPerKm); equal("-560", r.netPerHour)
    }
    @Test fun `decimal arithmetic retains cents without floating point loss`() {
        val r = calculator.calculate(ride.copy(expectedDriverPayout = bd("1800.10")), settings.copy(otherCostsPerTrip = bd("0.20")))
        equal("1419.90", r.netEarnings)
    }
    @Test fun `dashboard excludes demo provisional and different days`() {
        val day = LocalDate.of(2026, 10, 19); val zone = ZoneId.of("Asia/Colombo")
        fun analysis(r: RideRequest, date: LocalDate = day) = RideAnalysis(analyzedAt = date.atStartOfDay(zone).toInstant().toEpochMilli(), ride = r, settings = settings, result = calculator.calculate(r, settings))
        val summary = summarizeDay(listOf(analysis(ride), analysis(ride.copy(sourceType = SourceType.DEMO)), analysis(ride.copy(userReviewed = false)), analysis(ride, day.minusDays(1))), day, zone)
        equal("1420", summary.net); equal("142", summary.perKm); equal("2840", summary.perHour)
        assertEquals(2, summary.analyzedCount); assertEquals(1, summary.completeCount)
    }
    @Test fun `empty dashboard shows unknown instead of invented earnings`() { assertNull(summarizeDay(emptyList(), LocalDate.now(), ZoneId.systemDefault()).net) }
}
