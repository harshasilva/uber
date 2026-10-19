package lk.ridewise.domain

import lk.ridewise.domain.rate.*
import org.junit.Assert.*
import org.junit.Test

class HireRateRuleTest {
    private val parser = NotificationHireParser()
    private fun result(price: String, pickup: String = "2", trip: String = "8") = HireRateRule.evaluate(HireRateInput(price.toBigDecimal(), "LKR", pickup.toBigDecimal(), trip.toBigDecimal()))
    @Test fun aboveFortyIsGood() { assertEquals(HireVerdict.GOOD, result("600").verdict); assertEquals(0, "60".toBigDecimal().compareTo(result("600").pricePerKm)) }
    @Test fun exactlyFortyIsBad() { assertEquals(HireVerdict.BAD, result("400").verdict) }
    @Test fun belowFortyIsBad() { assertEquals(HireVerdict.BAD, result("350").verdict) }
    @Test fun comparisonUsesUnroundedNumbers() { assertEquals(HireVerdict.GOOD, result("400.0000001").verdict) }
    @Test fun pickupIsIncluded() { assertEquals(HireVerdict.BAD, result("400", "5", "5").verdict) }
    @Test fun zeroDistanceIsUnknown() { assertEquals(HireVerdict.CHECK_DETAILS, result("400", "0", "0").verdict) }
    @Test fun negativesAreUnknown() { assertEquals(HireVerdict.CHECK_DETAILS, result("-400").verdict); assertEquals(HireVerdict.CHECK_DETAILS, result("400", "-1").verdict) }
    @Test fun missingPickupIsNotAssumedZero() { assertEquals(HireVerdict.CHECK_DETAILS, HireRateRule.evaluate(HireRateInput("500".toBigDecimal(), "LKR", null, "8".toBigDecimal())).verdict) }
    @Test fun parsesSingleLineLabelledOffer() { val r = HireRateRule.evaluate(parser.parse("New ride request · Fare: LKR 600 · Pickup: 2 km · Trip: 8 km")); assertEquals(HireVerdict.GOOD, r.verdict) }
    @Test fun parsesMultilineDecimalsAndMetres() { val r = HireRateRule.evaluate(parser.parse("New trip\nPrice: Rs. 450.50\nPickup distance: 500 m\nPassenger trip distance: 9.5 km")); assertEquals(HireVerdict.GOOD, r.verdict); assertEquals(0, "10".toBigDecimal().compareTo(r.totalKm)) }
    @Test fun payoutIsNotAddedToCustomerFare() { val i = parser.parse("Customer fare: LKR 600\nDriver payout: LKR 300\nPickup: 2 km\nTrip distance: 8 km"); assertEquals(HireVerdict.BAD, HireRateRule.evaluate(i).verdict) }
    @Test fun feesAreNotMistakenForFare() { assertEquals(HireVerdict.CHECK_DETAILS, HireRateRule.evaluate(parser.parse("New ride request\nFee: LKR 80\nPickup: 2 km\nTrip: 8 km")).verdict) }
    @Test fun conflictingFaresRequireReview() { assertEquals(HireVerdict.CHECK_DETAILS, HireRateRule.evaluate(parser.parse("Fare: LKR 500\nPrice: LKR 600\nPickup: 2 km\nTrip: 8 km")).verdict) }
    @Test fun totalOnlyRequiresReview() { assertEquals(HireVerdict.CHECK_DETAILS, HireRateRule.evaluate(parser.parse("Fare: LKR 500\nTotal distance: 10 km")).verdict) }
    @Test fun conflictingTotalRequiresReview() { assertEquals(HireVerdict.CHECK_DETAILS, HireRateRule.evaluate(parser.parse("Fare: LKR 500\nPickup: 2 km\nTrip: 8 km\nTotal distance: 8 km")).verdict) }
    @Test fun foreignCurrencyDoesNotBecomeLkr() { assertEquals(HireVerdict.CHECK_DETAILS, HireRateRule.evaluate(parser.parse("Fare: USD 500\nPickup: 2 km\nTrip: 8 km")).verdict) }
    @Test fun onlineAndCompletedNotificationsAreIgnored() {
        assertFalse(parser.isRideCandidate("You are online")); assertFalse(parser.isRideCandidate("Trip completed Fare LKR 600 Pickup 2 km Trip 8 km"))
        assertTrue(parser.isRideCandidate("New ride request")); assertTrue(parser.isRideCandidate("Fare LKR 600 Pickup 2 km Trip 8 km"))
    }
}
