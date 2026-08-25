package com.experiment.argus.sentinel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.experiment.argus.Ntfy
import com.experiment.argus.RoleStore
import com.experiment.argus.batterySummary

/**
 * Manifest-registered receiver for ACTION_POWER_CONNECTED / DISCONNECTED.
 * These broadcasts are exempt from Android's implicit-broadcast ban, so this
 * fires even if the system killed the app. This is the whole point of the app:
 * charger state = cheap, reliable power-cut detection.
 */
class PowerEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (RoleStore.role(context) != "sentinel") return
        val topic = RoleStore.topic(context)
        if (topic.isEmpty()) return

        val lost = intent.action == Intent.ACTION_POWER_DISCONNECTED
        val pending = goAsync()
        Thread {
            try {
                val title = if (lost) "[Power LOST]" else "[Power back]"
                val body = batterySummary(context)
                val priority = if (lost) 4 else 3
                val (ok) = Ntfy.send(topic, title, body, priority = priority)
                if (!ok) SentinelJobs.retryAlert(context, topic, title, body, priority)
                SentinelJobs.ensure(context)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}
