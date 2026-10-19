package lk.ridewise.domain.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Decimals {
    // Accept decimal point and well-formed comma thousands separators, never silently swap locales.
    private val numeric = Regex("[+-]?(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?")
    fun parse(value: String): BigDecimal? {
        val text = value.trim()
        return if (text.length <= 24 && numeric.matches(text)) text.replace(",", "").toBigDecimalOrNull() else null
    }
    fun number(value: BigDecimal?, decimals: Int = 2): String {
        if (value == null) return "—"
        val formatter = DecimalFormat("#,##0" + if (decimals > 0) "." + "0".repeat(decimals) else "",
            DecimalFormatSymbols(Locale.US))
        formatter.roundingMode = RoundingMode.HALF_UP
        return formatter.format(value)
    }
    fun money(value: BigDecimal?) = if (value == null) "—" else "LKR ${number(value)}"
    fun editable(value: BigDecimal?) = value?.stripTrailingZeros()?.toPlainString().orEmpty()
}
