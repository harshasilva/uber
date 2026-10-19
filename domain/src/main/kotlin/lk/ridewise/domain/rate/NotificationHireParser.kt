package lk.ridewise.domain.rate

import lk.ridewise.domain.util.Decimals
import java.math.BigDecimal

/** Only notification text delivered by Android is inspected. No screen or app-tree access. */
class NotificationHireParser {
    private val options = setOf(RegexOption.IGNORE_CASE)
    private val number = "[+-]?(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?"
    private val money = Regex("\\b(?:LKR|Rs\\.?)\\s*($number)(?![0-9.,])|(?<![0-9.,])($number)\\s*(?:LKR|Rs\\.?)\\b", options)
    private val km = Regex("(?<![0-9.,])($number)\\s*(km|kilomet(?:er|re)s?|m|met(?:er|re)s?)\\b", options)
    private val labels = Regex("\\b(?:driver payout|your earnings|payout|earnings|total fare|estimated fare|customer fare|fare|price|pick[ -]?up(?: distance)?|passenger trip(?: distance)?|trip distance|trip|ride distance|total distance|commission|fee|toll)\\b", options)
    private val request = Regex("\\b(?:new (?:ride|trip|hire)|(?:ride|trip|hire) request|incoming (?:ride|trip)|ride offer)\\b", options)
    private val finished = Regex("\\b(?:completed|cancelled|canceled|expired|missed|receipt|payment received|earnings summary|weekly summary)\\b", options)

    fun isRideCandidate(text: String): Boolean = !finished.containsMatchIn(text) &&
        (request.containsMatchIn(text) || (money.containsMatchIn(text) && km.containsMatchIn(text)))

    fun parse(text: String): HireRateInput {
        val clean = text.take(16_384).replace('\u00a0', ' ')
        // Segment labels even when a notification puts multiple fields on one line.
        val matches = labels.findAll(clean).toList()
        val segments = matches.mapIndexed { i, m -> m.value.lowercase() to clean.substring(m.range.last + 1,
            matches.getOrNull(i + 1)?.range?.first ?: clean.length) }
        val issues = mutableListOf<String>()
        fun unique(values: List<BigDecimal>, name: String): BigDecimal? {
            val distinct = values.map { it.stripTrailingZeros() }.distinct()
            if (distinct.size > 1) issues += "Conflicting $name values; check the request."
            return distinct.singleOrNull()
        }
        fun amounts(value: String): List<BigDecimal> = money.findAll(value).mapNotNull {
            Decimals.parse(it.groupValues[1].ifBlank { it.groupValues[2] })
        }.toList()
        fun distances(value: String): List<BigDecimal> = km.findAll(value).mapNotNull {
            Decimals.parse(it.groupValues[1])?.let { n -> if (it.groupValues[2].lowercase().let { unit -> unit == "m" || unit.startsWith("met") }) n.movePointLeft(3) else n }
        }.toList()
        val payouts = segments.filter { it.first in setOf("driver payout", "your earnings", "payout", "earnings") }.flatMap { amounts(it.second) }
        val fares = segments.filter { it.first in setOf("total fare", "estimated fare", "customer fare", "fare", "price") }.flatMap { amounts(it.second) }
        val price = unique(if (payouts.isNotEmpty()) payouts else fares, "ride price")
        if (price == null && money.containsMatchIn(clean)) issues += "The amount is not clearly labelled as a ride price or payout."
        val currencies = Regex("\\b(?:LKR|USD|EUR|GBP|INR)\\b|\\bRs\\.?(?=\\s|[0-9])", options)
            .findAll(clean).map { if (it.value.startsWith("Rs", true)) "LKR" else it.value.uppercase() }.toSet()
        val pickup = unique(segments.filter { it.first.startsWith("pickup") || it.first.startsWith("pick-up") || it.first.startsWith("pick up") }
            .flatMap { distances(it.second) }, "pickup distance")
        val trip = unique(segments.filter { it.first.startsWith("passenger trip") || it.first in setOf("trip", "trip distance", "ride distance") }
            .flatMap { distances(it.second) }, "passenger distance")
        val total = unique(segments.filter { it.first == "total distance" }.flatMap { distances(it.second) }, "total distance")
        if (total != null && pickup != null && trip != null && total.compareTo(pickup + trip) != 0)
            issues += "Displayed total conflicts with pickup plus passenger distance."
        if (total != null && (pickup == null || trip == null))
            issues += "A total label alone does not confirm that pickup is included; check both distances."
        return HireRateInput(price, currencies.singleOrNull(), pickup, trip, issues.distinct(),
            if (payouts.isNotEmpty()) "Expected driver payout" else "Displayed fare / price")
    }
}
