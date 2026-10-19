package lk.ridewise.live

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import lk.ridewise.RideWiseApplication
import lk.ridewise.domain.rate.*

/**
 * Explicit opt-in Android notification access. Filter source BEFORE reading extras.
 * Never invokes PendingIntents, RemoteInputs, notification actions or cancelNotification.
 * Never inspects application UI, stores raw messages, or queries old active notifications.
 */
class RideNotificationListener : NotificationListenerService() {
    private val app get() = application as RideWiseApplication
    private val parser = NotificationHireParser()
    private var previousKey: String? = null
    private var previousResult: HireRateResult? = null
    private var previousPostTime: Long = 0
    override fun onListenerConnected() {
        app.liveStore.connected(true)
        app.liveAssistant.refreshPermissions()
        if (app.liveStore.state.value.enabled) LiveAssistantNotifications.show(this, true)
    }
    override fun onListenerDisconnected() {
        app.liveStore.connected(false)
        app.liveAssistant.refreshPermissions()
        app.liveAssistant.overlay.hide()
        if (app.liveStore.state.value.enabled) LiveAssistantNotifications.show(this, false)
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val provider = RideProvider.fromPackage(sbn.packageName) ?: return
        app.liveAssistant.refreshPermissions()
        val state = app.liveStore.state.value
        if (!state.enabled || !app.liveStore.accepts(provider)) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        // Ignore stale events and notifications hidden/redacted by Android. Never bypass redaction.
        if (System.currentTimeMillis() - sbn.postTime > 60_000) return
        val extras = sbn.notification.extras
        val lines = buildList {
            listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT).forEach { field ->
                extras.getCharSequence(field)?.toString()?.takeIf { it.isNotBlank() }?.let { add(it.take(4096)) }
            }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.take(20)?.forEach { add(it.toString().take(512)) }
        }.distinct().joinToString("\n").take(16_384)
        if (!parser.isRideCandidate(lines)) {
            app.liveAssistant.overlay.hide(sbn.key); app.liveStore.clear(sbn.key)
            if (previousKey == sbn.key) { previousKey = null; previousResult = null }
            return
        }
        val result = HireRateRule.evaluate(parser.parse(lines))
        if (sbn.key == previousKey && result == previousResult && sbn.postTime == previousPostTime) return
        previousKey = sbn.key; previousResult = result; previousPostTime = sbn.postTime
        val hire = LiveHire(sbn.key, provider.title, result, receivedAt = sbn.postTime)
        app.liveStore.publish(hire)
        if (state.active) app.liveAssistant.overlay.show(hire)
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (RideProvider.fromPackage(sbn.packageName) == null) return
        app.liveAssistant.overlay.hide(sbn.key); app.liveStore.clear(sbn.key)
        if (previousKey == sbn.key) { previousKey = null; previousResult = null }
    }
    override fun onDestroy() { app.liveStore.connected(false); app.liveAssistant.overlay.hide(); super.onDestroy() }
}
