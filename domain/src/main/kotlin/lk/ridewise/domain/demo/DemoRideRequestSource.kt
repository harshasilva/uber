package lk.ridewise.domain.demo

import lk.ridewise.domain.capture.RideRequestSource
import lk.ridewise.domain.model.*
import lk.ridewise.domain.parser.LabelledRideTextParser

class DemoRideRequestSource : RideRequestSource {
    override suspend fun capture(): RideRequest = LabelledRideTextParser().parse(SAMPLE, SourceType.DEMO)
        .copy(emptyReturnDistanceKm = java.math.BigDecimal.ZERO)

    companion object {
        const val SAMPLE = """SYNTHETIC RIDE REQUEST • RideWise demo
Driver payout: LKR 1,800.00
Pickup: Town Hall, Colombo
Pickup distance: 1.5 km
Pickup time: 5 min
Destination: Nugegoda town centre
Passenger trip distance: 8.5 km
Trip duration: 25 min"""
    }
}
