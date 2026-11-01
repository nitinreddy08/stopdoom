package app.unrot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import app.unrot.health.DiagnosticLog

/**
 * Screen off / on / unlock (SPEC §7.1 step 2). Registered while the service is connected.
 * Logs each change and reports screen off/on to [onScreen] (true = on); the Inspector records
 * them, later steps use them to end scroll sessions.
 */
class ScreenStateReceiver(
    private val log: DiagnosticLog,
    private val onScreen: (on: Boolean) -> Unit = {},
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            val action = intent?.action
            val event = when (action) {
                Intent.ACTION_SCREEN_OFF -> "screen off"
                Intent.ACTION_SCREEN_ON -> "screen on"
                Intent.ACTION_USER_PRESENT -> "unlocked"
                else -> return
            }
            log.i(TAG, event)
            when (action) {
                Intent.ACTION_SCREEN_OFF -> onScreen(false)
                Intent.ACTION_SCREEN_ON -> onScreen(true)
            }
        } catch (t: Throwable) {
            log.e(TAG, "onReceive failed: ${t.javaClass.name}")
        }
    }

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(this)
        } catch (e: IllegalArgumentException) {
            log.w(TAG, "receiver was not registered")
        }
    }

    private companion object {
        const val TAG = "Screen"
    }
}
