package lk.ridewise.capture.automatic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import lk.ridewise.domain.rate.ScreenOffer

data class AutomaticState(val active: Boolean = false, val message: String = "OFF", val scans: Int = 0, val offer: ScreenOffer? = null)

/** Session status and numerical results only. No persistent opt-in, pixels, or recognized text. */
class AutomaticSession {
    private val mutable = MutableStateFlow(AutomaticState())
    val state = mutable.asStateFlow()
    fun start() { mutable.value = AutomaticState(true, "ON · looking for a visible ride card") }
    fun scanned(offer: ScreenOffer?) { mutable.update { it.copy(scans = it.scans + 1, offer = offer,
        message = if (offer == null) "ON · looking for a visible ride card" else "ON · ${offer.result.verdict.name.replace('_', ' ')} · check the numbers") } }
    fun stop(message: String = "OFF · screen sharing stopped") { mutable.update { it.copy(active = false, message = message, offer = null) } }
}
