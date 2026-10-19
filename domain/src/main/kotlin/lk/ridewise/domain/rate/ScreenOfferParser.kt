package lk.ridewise.domain.rate

import lk.ridewise.domain.util.Decimals
import java.math.BigDecimal

data class ScreenOffer(val provider: String, val input: HireRateInput) {
    val result get() = HireRateRule.evaluate(input)
}

/** Reads visible offer text only. No addresses or raw OCR text leave the parser. */
class ScreenOfferParser {
    private val n = "[+-]?(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?"
    private val money = Regex("^(?:LKR\\s*($n)|($n)\\s*LKR)$", RegexOption.IGNORE_CASE)
    private val leg = Regex("(?<![0-9.,])($n)\\s*min(?:ute)?s?\\s*(away\\s*)?\\(\\s*($n)\\s*(km|m)\\s*\\)\\s*(away|trip)?", RegexOption.IGNORE_CASE)
    private val action = Regex("^(accept(?:\\s+trip)?|match)$", RegexOption.IGNORE_CASE)

    fun parse(text: String): ScreenOffer? {
        val lines = text.take(16_384).replace('\u00a0', ' ').lines().map { it.trim() }.filter { it.isNotEmpty() }
        val actionLines = lines.withIndex().filter { action.matches(it.value) }
        if (actionLines.isEmpty()) return null
        val legs = leg.findAll(lines.joinToString(" ")).toList()
        val away = legs.filter { it.groupValues[2].isNotBlank() || it.groupValues[5].equals("away", true) }
        if (away.isEmpty()) return null
        val prices = lines.mapNotNull { money.matchEntire(it) }.mapNotNull {
            Decimals.parse(it.groupValues[1].ifBlank { it.groupValues[2] })
        }.map { it.stripTrailingZeros() }.distinct()
        if (prices.isEmpty()) return null
        val issues = mutableListOf<String>()
        if (prices.size != 1 || actionLines.size != 1) issues += "More than one offer or price is visible. Check the card."
        val pickup = away.singleOrNull()
        // PickMe uses an unlabelled second duration/distance row. Accept it only if there are
        // exactly two route rows, with the away row first. Uber explicitly labels the second trip.
        val trip = if (legs.size == 2 && pickup === legs.first()) legs.last().takeIf {
            it.groupValues[2].isBlank() && !it.groupValues[5].equals("away", true)
        } else null
        if (legs.size > 2 || away.size > 1) issues += "Multiple route rows are visible."
        fun distance(m: MatchResult?): BigDecimal? = m?.let {
            Decimals.parse(it.groupValues[3])?.let { value -> if (it.groupValues[4].equals("m", true)) value.movePointLeft(3) else value }
        }
        return ScreenOffer(if (actionLines.singleOrNull()?.value.equals("Accept trip", true)) "PickMe card" else "Uber / ride card",
            HireRateInput(prices.singleOrNull(), "LKR", distance(pickup), distance(trip), issues, "Displayed offer · screen OCR"))
    }
}

/** Two matching reads before a verdict; a changed offer immediately invalidates the previous one. */
class StableOfferTracker {
    private var previous: ScreenOffer? = null
    private var repeats = 0
    private var misses = 0
    private var confirmed: ScreenOffer? = null
    fun observe(offer: ScreenOffer?): ScreenOffer? {
        if (offer == null) {
            previous = null; repeats = 0
            if (++misses >= 2) confirmed = null
        } else {
            misses = 0
            if (offer != previous) { previous = offer; repeats = 1; confirmed = null }
            else if (++repeats >= 2) confirmed = offer
        }
        return confirmed
    }
}
