package lk.ridewise.domain.calculator

import lk.ridewise.domain.model.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*

data class DashboardSummary(val analyzedCount: Int, val completeCount: Int, val net: BigDecimal?, val perKm: BigDecimal?, val perHour: BigDecimal?)
fun summarizeDay(analyses: List<RideAnalysis>, day: LocalDate, zone: ZoneId): DashboardSummary {
    val today = analyses.filter { it.ride.sourceType != SourceType.DEMO && Instant.ofEpochMilli(it.analyzedAt).atZone(zone).toLocalDate() == day }
    val complete = today.filter { it.result.status == AnalysisStatus.COMPLETE && it.result.netEarnings != null }
    if (complete.isEmpty()) return DashboardSummary(today.size, 0, null, null, null)
    val net = complete.fold(BigDecimal.ZERO) { a, b -> a + b.result.netEarnings!! }
    val km = complete.fold(BigDecimal.ZERO) { a, b -> a + (b.result.totalDistanceKm ?: BigDecimal.ZERO) }
    val timed = complete.filter { it.result.totalMinutes != null }
    val minutes = timed.fold(BigDecimal.ZERO) { a, b -> a + b.result.totalMinutes!! }
    val timedNet = timed.fold(BigDecimal.ZERO) { a, b -> a + b.result.netEarnings!! }
    return DashboardSummary(today.size, complete.size, net,
        if (km.signum() > 0) net.divide(km, 12, RoundingMode.HALF_UP) else null,
        if (minutes.signum() > 0) timedNet.multiply(BigDecimal("60")).divide(minutes, 12, RoundingMode.HALF_UP) else null)
}
