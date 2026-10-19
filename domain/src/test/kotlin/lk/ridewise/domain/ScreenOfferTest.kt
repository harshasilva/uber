package lk.ridewise.domain

import lk.ridewise.domain.rate.*
import org.junit.Assert.*
import org.junit.Test

class ScreenOfferTest {
    private val parser = ScreenOfferParser()
    private val pickme = "219.04LKR\nBIKE\n6 min away (2.0 km)\nPickup\n7 min (2.97 km)\nDestination\nAccept trip"
    private val uber = "Moto\nLKR160\n4.86\n7 mins (2.8 km) away\nPickup\n5 mins (1.3 km) trip\nDestination\nMatch"
    @Test fun pickmeGrossRate() { val r = parser.parse(pickme)!!.result; assertEquals(HireVerdict.GOOD, r.verdict); assertEquals("44.07", r.pricePerKm!!.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()) }
    @Test fun uberGrossRate() { val r = parser.parse(uber)!!.result; assertEquals(HireVerdict.BAD, r.verdict); assertEquals("39.02", r.pricePerKm!!.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()) }
    @Test fun wrappedRouteRows() { assertEquals(HireVerdict.BAD, parser.parse(uber.replace("(2.8 km)", "(2.8\nkm)"))!!.result.verdict) }
    @Test fun absentTripNeverBecomesGood() { assertEquals(HireVerdict.CHECK_DETAILS, parser.parse(uber.replace("5 mins (1.3 km) trip", ""))!!.result.verdict) }
    @Test fun missingActionIsNotAnOffer() { assertNull(parser.parse(uber.replace("Match", ""))) }
    @Test fun ownBadgeDoesNotLookLikeAnOffer() { assertNull(parser.parse("GOOD · LKR 44.07/km\nLKR 219.04 ÷ 4.97 km (pickup + trip)")) }
    @Test fun badgeAboveCardDoesNotChangePrice() { assertEquals(HireVerdict.BAD, parser.parse("GOOD · LKR 44.07/km\nLKR 219.04 ÷ 4.97 km (pickup + trip)\n$uber")!!.result.verdict) }
    @Test fun multiplePricesNeedChecking() { assertEquals(HireVerdict.CHECK_DETAILS, parser.parse("LKR999\n$uber")!!.result.verdict) }
    @Test fun multipleTripsNeedChecking() { assertEquals(HireVerdict.CHECK_DETAILS, parser.parse("$uber\n8 min (9 km) trip")!!.result.verdict) }
    @Test fun exactlyFortyIsBad() { assertEquals(HireVerdict.BAD, parser.parse(uber.replace("LKR160", "LKR164"))!!.result.verdict) }
    @Test fun negativeDistanceIsInvalid() { assertEquals(HireVerdict.CHECK_DETAILS, parser.parse(uber.replace("2.8 km", "-2.8 km"))!!.result.verdict) }
    @Test fun noPrivateDetailsRetained() { assertFalse(parser.parse(uber)!!.toString().contains("Destination")) }
    @Test fun stableReadsAndChangedOffer() {
        val tracker = StableOfferTracker(); val good = parser.parse(pickme); val bad = parser.parse(uber)
        assertNull(tracker.observe(good)); assertEquals(good, tracker.observe(good))
        assertNull(tracker.observe(bad)); assertEquals(bad, tracker.observe(bad))
        assertEquals(bad, tracker.observe(null)); assertNull(tracker.observe(null))
        assertNull(tracker.observe(bad)); assertEquals(bad, tracker.observe(bad))
    }
}
