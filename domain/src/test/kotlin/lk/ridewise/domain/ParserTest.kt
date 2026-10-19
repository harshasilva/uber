package lk.ridewise.domain

import lk.ridewise.domain.parser.LabelledRideTextParser
import lk.ridewise.domain.model.*
import lk.ridewise.domain.demo.DemoRideRequestSource
import lk.ridewise.domain.util.Decimals
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ParserTest {
    private val parser = LabelledRideTextParser()
    private fun equal(expected: String, actual: BigDecimal?) { assertNotNull(actual); assertEquals(0, expected.toBigDecimal().compareTo(actual)) }
    @Test fun `labelled LKR payout with comma and decimal`() {
        val r = parser.parse("Driver payout: LKR 1,250.50")
        equal("1250.50", r.expectedDriverPayout); assertEquals("LKR", r.currency); assertFalse(r.userReviewed)
    }
    @Test fun `rupee symbol variant and spacing`() { equal("912.75", parser.parse("Your earnings : Rs.   912.75").expectedDriverPayout) }
    @Test fun `multiline payout`() { equal("1500.10", parser.parse("Estimated payout\nLKR 1,500.10").expectedDriverPayout) }
    @Test fun `missing fields stay null`() {
        val r = parser.parse("Request available")
        assertNull(r.expectedDriverPayout); assertNull(r.pickupDistanceKm); assertNull(r.passengerTripDistanceKm)
        assertNull(r.estimatedTripMinutes); assertNull(r.currency)
    }
    @Test fun `customer fare is distinct`() { val r = parser.parse("Customer fare: LKR 2,000.00"); equal("2000", r.customerFare); assertNull(r.expectedDriverPayout) }
    @Test fun `unlabelled fare stays ambiguous`() { val r = parser.parse("LKR 950.00"); assertNull(r.expectedDriverPayout); equal("950", r.displayedFare); assertTrue(r.warnings.isNotEmpty()) }
    @Test fun `PickMe visible offer card extracts the offered amount and both route legs`() {
        val r = parser.parse("""BIKE
            |219.04 LKR
            |6 min away (2.0 km)
            |Sudharmaratanarama Mawatha, Moratuwa
            |7 min (2.97 km)
            |Princes of Wales' College, Moratuwa
            |Accept trip""".trimMargin(), SourceType.OCR)
        assertNull(r.expectedDriverPayout); equal("219.04", r.displayedFare); assertEquals("LKR", r.currency)
        equal("2.0", r.pickupDistanceKm); equal("2.97", r.passengerTripDistanceKm)
        equal("6", r.estimatedPickupMinutes); equal("7", r.estimatedTripMinutes)
    }
    @Test fun `Uber visible offer card extracts the offered amount and both route legs`() {
        val r = parser.parse("""Moto
            |LKR160
            |7 mins (2.8 km) away
            |85 Ernest Pl, 10400
            |5 mins (1.3 km) trip
            |51 Veera Puran Appu Mawatha, 10400
            |Match""".trimMargin(), SourceType.OCR)
        assertNull(r.expectedDriverPayout); equal("160", r.displayedFare); assertEquals("LKR", r.currency)
        equal("2.8", r.pickupDistanceKm); equal("1.3", r.passengerTripDistanceKm)
        equal("7", r.estimatedPickupMinutes); equal("5", r.estimatedTripMinutes)
    }
    @Test fun `total distance is not passenger distance`() {
        val r = parser.parse("Total trip distance: 12.5 km\nPickup distance: 2 km")
        assertNull(r.passengerTripDistanceKm); equal("2", r.pickupDistanceKm)
    }
    @Test fun `distance and time retain context`() {
        val r = parser.parse("Pickup distance: 1.25 km\nPickup time: 4 min\nPassenger trip distance: 7.75 km\nTrip duration: 22.5 min")
        equal("1.25", r.pickupDistanceKm); equal("7.75", r.passengerTripDistanceKm)
        equal("4", r.estimatedPickupMinutes); equal("22.5", r.estimatedTripMinutes)
    }
    @Test fun `metres convert to kilometres`() { equal("0.65", parser.parse("Pickup distance: 650 m").pickupDistanceKm) }
    @Test fun `multiline trip distance`() { equal("8.5", parser.parse("Trip distance:\n8.5 km").passengerTripDistanceKm) }
    @Test fun `hours convert to minutes`() { equal("80", parser.parse("Trip duration: 1 hour 20 min").estimatedTripMinutes) }
    @Test fun `repeated minutes are ambiguous rather than added`() { assertNull(parser.parse("Pickup time: 4 min / 6 min").estimatedPickupMinutes) }
    @Test fun `combined time is not passenger time`() { assertNull(parser.parse("Total trip time: 30 min").estimatedTripMinutes) }
    @Test fun `conflicting payouts require confirmation`() {
        val r = parser.parse("Driver payout: LKR 1,000\nYour earnings: LKR 1,200")
        assertNull(r.expectedDriverPayout); assertEquals(Confidence.LOW, r.fieldConfidence[RideField.PAYOUT]?.level)
    }
    @Test fun `conflicting distance requires confirmation`() { assertNull(parser.parse("Trip distance: 8 km\nTrip distance: 10 km").passengerTripDistanceKm) }
    @Test fun `conflicting currency is unselected`() { assertNull(parser.parse("Driver payout: LKR 1,000\nCustomer fare: USD 10").currency) }
    @Test fun `location is optional and only explicitly labelled`() {
        val r = parser.parse("Pickup: Colombo town\nDestination: Nugegoda centre\nPassenger name: Synthetic Person")
        assertEquals("Colombo town", r.pickupLocation); assertEquals("Nugegoda centre", r.destination)
        assertFalse(r.toString().contains("Synthetic Person"))
    }
    @Test fun `generic fee never silently deducted`() { assertNull(parser.parse("Service fee: LKR 50").additionalTripCosts) }
    @Test fun `explicit additional driver cost extracted`() { equal("50.25", parser.parse("Additional driver cost: LKR 50.25").additionalTripCosts) }
    @Test fun `malformed comma amount not partially parsed`() { assertNull(parser.parse("Driver payout: LKR 1,25.00").expectedDriverPayout) }
    @Test fun `demo fixture produces expected fields`() {
        val r = parser.parse(DemoRideRequestSource.SAMPLE, SourceType.DEMO)
        equal("1800", r.expectedDriverPayout); equal("1.5", r.pickupDistanceKm); equal("8.5", r.passengerTripDistanceKm)
        assertEquals(SourceType.DEMO, r.sourceType)
    }
    @Test fun `decimal formatting uses LKR and half up rounding`() { assertEquals("LKR 1,234.57", Decimals.money("1234.565".toBigDecimal())) }
    @Test fun `numeric inputs reject malformed locale and non finite values`() {
        listOf("NaN", "Infinity", "1,20", "1.2.3", "1e9", "").forEach { assertNull(Decimals.parse(it)) }
        equal("1234.50", Decimals.parse("1,234.50"))
    }
    @Test fun `blank formatting never becomes zero`() { assertEquals("—", Decimals.money(null)) }
}
