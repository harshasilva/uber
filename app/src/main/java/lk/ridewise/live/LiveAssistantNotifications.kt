package lk.ridewise.live

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import lk.ridewise.MainActivity
import lk.ridewise.R
import lk.ridewise.RideWiseApplication

object LiveAssistantNotifications {
    const val CHANNEL = "ridewise_live_assistant"
    private const val ID = 41
    fun show(context: Context, connected: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Live ride assistant", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 41, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_live", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getBroadcast(context, 42, Intent(context, StopLiveAssistantReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_capture)
            .setContentTitle("RideWise · LKR 40/km assistant is ON")
            .setContentText(if (connected) "Selected driver notifications only · tap to manage" else "Waiting for Android notification listener connection")
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .addAction(0, "Stop assistant", stop).build()
        // The explicit Start action checks notification permission before this call.
        try { manager.notify(ID, notification) } catch (_: SecurityException) {
            (context.applicationContext as RideWiseApplication).liveStore.setEnabled(false)
        }
    }
    fun hide(context: Context) { context.getSystemService(NotificationManager::class.java).cancel(ID) }
}

class StopLiveAssistantReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        (context.applicationContext as RideWiseApplication).liveAssistant.stop()
    }
}
