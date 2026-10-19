package lk.ridewise.domain.rate

import java.math.BigDecimal
import java.math.RoundingMode

enum class HireVerdict { GOOD, BAD, CHECK_DETAILS }

data class HireRateInput(
    val price: BigDecimal?,
    val currency: String?,
    val pickupKm: BigDecimal?,
    val passengerKm: BigDecimal?,
    val issues: List<String> = emptyList(),
    val priceDescription: String = "Displayed price",
)

data class HireRateResult(
    val verdict: HireVerdict,
    val price: BigDecimal?,
    val totalKm: BigDecimal?,
    val pricePerKm: BigDecimal?,
    val issues: List<String>,
    val priceDescription: String,
)

/** Driver-selected gross rate rule, entirely separate from the operating-cost calculator. */
object HireRateRule {
    val threshold = BigDecimal("40")
    fun evaluate(input: HireRateInput): HireRateResult {
        val issues = input.issues.toMutableList()
        if (input.currency != "LKR") issues += "LKR currency is missing or unclear."
        if (input.price == null || input.price.signum() < 0) issues += "A non-negative ride price is needed."
        if (input.pickupKm == null || input.pickupKm.signum() < 0) issues += "Pickup distance is needed."
        if (input.passengerKm == null || input.passengerKm.signum() < 0) issues += "Passenger trip distance is needed."
        val total = if (input.pickupKm != null && input.passengerKm != null && input.pickupKm.signum() >= 0 && input.passengerKm.signum() >= 0)
            input.pickupKm + input.passengerKm else null
        if (total != null && total.signum() <= 0) issues += "Total distance must be greater than zero."
        if (issues.isNotEmpty()) return HireRateResult(HireVerdict.CHECK_DETAILS, input.price, total, null, issues.distinct(), input.priceDescription)
        val rate = input.price!!.divide(total!!, 12, RoundingMode.HALF_UP)
        // Compare before rounding: exactly 40 is BAD, as requested by the driver.
        val good = input.price > total * threshold
        return HireRateResult(if (good) HireVerdict.GOOD else HireVerdict.BAD, input.price, total, rate, emptyList(), input.priceDescription)
    }
}
