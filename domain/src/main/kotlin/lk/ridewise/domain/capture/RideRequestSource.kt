package lk.ridewise.domain.capture

import lk.ridewise.domain.model.RideRequest

/** A future official integration implements this contract, with separate authorization and tests. */
interface RideRequestSource { suspend fun capture(): RideRequest }
