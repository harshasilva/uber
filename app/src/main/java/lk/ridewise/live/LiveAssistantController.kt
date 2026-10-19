package lk.ridewise.live

import android.content.Context
import android.service.notification.NotificationListenerService
import lk.ridewise.domain.rate.*

/** Platform-facing commands; Compose receives state and callbacks instead of window APIs. */
class LiveAssistantController(private val context: Context, val store: LiveAssistantStore) {
    val overlay = HireOverlay(context, store::message)
    fun start() {
        store.refreshPermissions()
        if (!store.state.value.ready) { store.message("Enable notification access, display-over-apps access and visible RideWise notifications, then select a driver app."); return }
        store.setEnabled(true)
        LiveAssistantNotifications.show(context, store.state.value.listenerConnected)
        if (!store.state.value.listenerConnected) NotificationListenerService.requestRebind(LiveAssistantStore.component(context))
    }
    fun stop() { store.setEnabled(false); overlay.hide(); store.clear(); LiveAssistantNotifications.hide(context) }
    fun selectSources(uber: Boolean, pickme: Boolean) {
        store.setSources(uber, pickme); overlay.hide()
        if (!store.state.value.enabled) LiveAssistantNotifications.hide(context)
    }
    fun refreshPermissions() {
        store.refreshPermissions()
        if (!store.state.value.enabled) { overlay.hide(); LiveAssistantNotifications.hide(context) }
    }
    fun demo(good: Boolean) {
        val result = HireRateRule.evaluate(HireRateInput(if (good) "600".toBigDecimal() else "350".toBigDecimal(), "LKR", "2".toBigDecimal(), "8".toBigDecimal()))
        val hire = LiveHire("demo-${System.nanoTime()}", "Demo", result, demo = true)
        store.publish(hire)
        overlay.show(hire)
    }
}
