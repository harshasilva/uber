package lk.ridewise.live

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import lk.ridewise.domain.rate.HireVerdict
import lk.ridewise.domain.util.Decimals
import kotlin.math.abs

/** Small, draggable, time-limited overlay. Never receives focus or touches outside its bounds. */
class HireOverlay(private val context: Context, private val onFailure: (String) -> Unit) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var key: String? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastX: Int? = null
    private var lastY: Int? = null
    private val dismiss = Runnable { hide() }
    private fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    fun show(hire: LiveHire, dismissAfterMs: Long? = 12_000) {
        hide()
        if (!Settings.canDrawOverlays(context)) { onFailure("Allow display over other apps to show the floating result."); return }
        val color = when (hire.result.verdict) { HireVerdict.GOOD -> Color.rgb(22, 105, 79); HireVerdict.BAD -> Color.rgb(160, 43, 43); HireVerdict.CHECK_DETAILS -> Color.rgb(112, 82, 0) }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply { setColor(color); cornerRadius = dp(18).toFloat() }
            elevation = dp(8).toFloat()
            contentDescription = "RideWise floating hire assessment"
        }
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun text(value: String, size: Float, bold: Boolean = false) = TextView(context).apply {
            this.text = value; textSize = size; setTextColor(Color.WHITE)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
        val verdict = when (hire.result.verdict) { HireVerdict.GOOD -> "GOOD"; HireVerdict.BAD -> "BAD"; HireVerdict.CHECK_DETAILS -> "CHECK DETAILS" }
        val rate = hire.result.pricePerKm?.let { " · LKR ${Decimals.number(it)}/km" }.orEmpty()
        val title = text(verdict + rate, 18f, true)
        top.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(text("×", 26f).apply {
            gravity = Gravity.CENTER; contentDescription = "Dismiss hire result"
            setOnClickListener { hide() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(top)
        root.addView(text(if (hire.demo) "DEMO · synthetic request" else "${hire.provider} · ${hire.sourceDescription}", 12f))
        root.addView(text(if (hire.result.verdict == HireVerdict.CHECK_DETAILS) "Missing or unclear price / pickup / trip km"
            else "${Decimals.money(hire.result.price)} ÷ ${Decimals.number(hire.result.totalKm)} km (pickup + trip)", 12f))
        root.addView(text("Your >40 rule · gross rate, not net profit · drag to move", 10f))
        val metrics = context.resources.displayMetrics
        val layout = WindowManager.LayoutParams(minOf(dp(330), metrics.widthPixels - dp(24)), ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (lastX ?: dp(12)).coerceIn(0, (metrics.widthPixels - width).coerceAtLeast(0))
            y = (lastY ?: dp(72)).coerceIn(dp(28), (metrics.heightPixels - dp(190)).coerceAtLeast(dp(28)))
        }
        params = layout
        var startX = 0; var startY = 0; var rawX = 0f; var rawY = 0f; var dragged = false
        title.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = layout.x; startY = layout.y; rawX = event.rawX; rawY = event.rawY; dragged = false; true }
                MotionEvent.ACTION_MOVE -> {
                    dragged = dragged || abs(event.rawX - rawX) > dp(4) || abs(event.rawY - rawY) > dp(4)
                    layout.x = (startX + event.rawX - rawX).toInt().coerceIn(0, (metrics.widthPixels - layout.width).coerceAtLeast(0))
                    layout.y = (startY + event.rawY - rawY).toInt().coerceIn(dp(28), (metrics.heightPixels - dp(190)).coerceAtLeast(dp(28)))
                    lastX = layout.x; lastY = layout.y
                    runCatching { windows.updateViewLayout(root, layout) }; true
                }
                MotionEvent.ACTION_UP -> { if (!dragged) title.performClick(); true }
                else -> false
            }
        }
        try { windows.addView(root, layout); view = root; key = hire.key; dismissAfterMs?.let { handler.postDelayed(dismiss, it) } }
        catch (_: Exception) { onFailure("Android could not show the floating result. Check display-over-apps access; the latest assessment is available in RideWise.") }
    }
    fun hide(onlyKey: String? = null) {
        if (onlyKey != null && key != onlyKey) return
        handler.removeCallbacks(dismiss)
        view?.let { runCatching { windows.removeViewImmediate(it) } }
        view = null; key = null; params = null
    }
}
