package lk.ridewise.live

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import lk.ridewise.domain.rate.*

enum class RideProvider(val title: String, val appPackage: String) {
    UBER("Uber Driver", "com.ubercab.driver"),
    PICKME("PickMe Driver", "com.pickme.driver.byod");
    companion object { fun fromPackage(pkg: String) = entries.firstOrNull { it.appPackage == pkg } }
}
data class LiveHire(val key: String, val provider: String, val result: HireRateResult, val receivedAt: Long = System.currentTimeMillis(), val demo: Boolean = false,
    val sourceDescription: String = "notification estimate")
data class LiveAssistantState(
    val enabled: Boolean = false,
    val uber: Boolean = true,
    val pickme: Boolean = true,
    val listenerConnected: Boolean = false,
    val notificationAccess: Boolean = false,
    val overlayAccess: Boolean = false,
    val notificationsVisible: Boolean = false,
    val latest: LiveHire? = null,
    val message: String? = null,
) {
    val ready get() = notificationAccess && overlayAccess && notificationsVisible && (uber || pickme)
    val active get() = enabled && ready && listenerConnected
}

/** Only opt-in/source switches persist. Notification text and parsed rides remain in memory. */
class LiveAssistantStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("live_assistant", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(LiveAssistantState(enabled = prefs.getBoolean("enabled", false),
        uber = prefs.getBoolean("uber", true), pickme = prefs.getBoolean("pickme", true)))
    val state = mutable.asStateFlow()
    init { refreshPermissions() }
    fun refreshPermissions() {
        val manager = context.getSystemService(NotificationManager::class.java)
        mutable.update { it.copy(
            notificationAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
            overlayAccess = Settings.canDrawOverlays(context),
            notificationsVisible = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                manager.getNotificationChannel(LiveAssistantNotifications.CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE) }
        if (!state.value.ready && state.value.enabled) setEnabled(false)
    }
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
        mutable.update { it.copy(enabled = enabled, message = null) }
    }
    fun setSources(uber: Boolean, pickme: Boolean) {
        prefs.edit().putBoolean("uber", uber).putBoolean("pickme", pickme).apply()
        mutable.update { it.copy(uber = uber, pickme = pickme, latest = null) }
        if (!uber && !pickme) setEnabled(false)
    }
    fun accepts(provider: RideProvider) = if (provider == RideProvider.UBER) state.value.uber else state.value.pickme
    fun connected(value: Boolean) { mutable.update { it.copy(listenerConnected = value) } }
    fun publish(hire: LiveHire) { mutable.update { it.copy(latest = hire, message = null) } }
    fun clear(key: String? = null) { mutable.update { if (key == null || it.latest?.key == key) it.copy(latest = null) else it } }
    fun message(message: String) { mutable.update { it.copy(message = message) } }
    companion object { fun component(context: Context) = ComponentName(context, RideNotificationListener::class.java) }
}
