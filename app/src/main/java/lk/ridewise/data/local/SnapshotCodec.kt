package lk.ridewise.data.local

import lk.ridewise.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/** Decimal strings preserve exact values; no raw OCR text or screenshots enter persistence. */
object SnapshotCodec {
    private fun JSONObject.decimal(key: String): BigDecimal? = if (isNull(key)) null else getString(key).toBigDecimalOrNull()
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.strings(key: String): List<String> = getJSONArray(key).let { a -> List(a.length()) { a.getString(it) } }
    private fun JSONObject.putDecimal(key: String, value: BigDecimal?): JSONObject = put(key, value?.toString() ?: JSONObject.NULL)
    fun settingsJson(s: VehicleCostSettings): String = JSONObject()
        .putDecimal("fuel", s.fuelPricePerLitre).putDecimal("economy", s.fuelEconomyKmPerLitre)
        .putDecimal("maintenance", s.maintenancePerKm).putDecimal("depreciation", s.depreciationPerKm)
        .putDecimal("other", s.otherCostsPerTrip).putDecimal("return", s.defaultEmptyReturnKm).toString()
    fun settings(text: String): VehicleCostSettings = JSONObject(text).let {
        VehicleCostSettings(it.decimal("fuel"), it.decimal("economy"), it.decimal("maintenance"),
            it.decimal("depreciation"), it.decimal("other"), it.decimal("return"))
    }
    fun rideJson(r: RideRequest): String = JSONObject()
        .put("id", r.id).put("capturedAt", r.capturedAt).put("source", r.sourceType.name)
        .put("currency", r.currency ?: JSONObject.NULL).putDecimal("payout", r.expectedDriverPayout)
        .putDecimal("customerFare", r.customerFare).putDecimal("displayedFare", r.displayedFare)
        .put("pickup", r.pickupLocation ?: JSONObject.NULL).put("destination", r.destination ?: JSONObject.NULL)
        .putDecimal("pickupKm", r.pickupDistanceKm).putDecimal("tripKm", r.passengerTripDistanceKm)
        .putDecimal("pickupMin", r.estimatedPickupMinutes).putDecimal("tripMin", r.estimatedTripMinutes)
        .putDecimal("returnKm", r.emptyReturnDistanceKm).putDecimal("extra", r.additionalTripCosts)
        .put("reviewed", r.userReviewed).put("warnings", JSONArray(r.warnings))
        .put("confidence", JSONObject().apply { r.fieldConfidence.forEach { (field, confidence) ->
            put(field.name, JSONObject().put("level", confidence.level.name).put("explanation", confidence.explanation))
        } }).toString()
    fun ride(text: String): RideRequest = JSONObject(text).let { j ->
        val c = j.getJSONObject("confidence")
        RideRequest(j.getString("id"), j.getLong("capturedAt"), SourceType.valueOf(j.getString("source")),
            j.stringOrNull("currency"), j.decimal("payout"), j.decimal("customerFare"), j.decimal("displayedFare"),
            j.stringOrNull("pickup"), j.stringOrNull("destination"), j.decimal("pickupKm"), j.decimal("tripKm"),
            j.decimal("pickupMin"), j.decimal("tripMin"), j.decimal("returnKm"), j.decimal("extra"),
            c.keys().asSequence().associate { key -> RideField.valueOf(key) to c.getJSONObject(key).let {
                FieldConfidence(Confidence.valueOf(it.getString("level")), it.getString("explanation"))
            } }, j.strings("warnings"), j.getBoolean("reviewed"))
    }
    fun encode(a: RideAnalysis): String = JSONObject().put("version", 1).put("id", a.id).put("at", a.analyzedAt)
        .put("ride", JSONObject(rideJson(a.ride))).put("settings", JSONObject(settingsJson(a.settings)))
        .put("result", a.result.let { r -> JSONObject().put("status", r.status.name)
            .putDecimal("payout", r.expectedDriverPayout).putDecimal("km", r.totalDistanceKm)
            .putDecimal("minutes", r.totalMinutes).putDecimal("fuel", r.fuelCost)
            .putDecimal("operating", r.vehicleOperatingCost).putDecimal("other", r.otherTripCosts)
            .putDecimal("net", r.netEarnings).putDecimal("perKm", r.netPerKm).putDecimal("perHour", r.netPerHour)
            .put("issues", JSONArray(r.issues)).put("assumptions", JSONArray(r.assumptions)) }).toString()
    fun decode(text: String): RideAnalysis = JSONObject(text).let { j ->
        val r = j.getJSONObject("result")
        RideAnalysis(j.getString("id"), j.getLong("at"), ride(j.getJSONObject("ride").toString()),
            settings(j.getJSONObject("settings").toString()), ProfitabilityResult(AnalysisStatus.valueOf(r.getString("status")),
                r.decimal("payout"), r.decimal("km"), r.decimal("minutes"), r.decimal("fuel"), r.decimal("operating"),
                r.decimal("other"), r.decimal("net"), r.decimal("perKm"), r.decimal("perHour"),
                r.strings("issues"), r.strings("assumptions")))
    }
}
