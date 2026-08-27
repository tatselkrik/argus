package com.experiment.argus.sentinel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.experiment.argus.EventTitles
import com.experiment.argus.RoleStore
import com.experiment.argus.push.EventStreamService

/** Resume only the monitoring mode that the user explicitly left started. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!RoleStore.monitoringEnabled(context)) return
        val topic = RoleStore.topic(context)
        if (topic.isEmpty()) return

        if (RoleStore.role(context) == "companion") {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, EventStreamService::class.java)
                )
            }
            return
        }
        if (RoleStore.role(context) != "sentinel") return

        runCatching { SentinelService.start(context) }
        SentinelJobs.ensure(context)
        val pending = goAsync()
        Thread {
            try {
                val title = EventTitles.REBOOTED
                val body = "Sentinel phone restarted and is back on duty."
                val priority = 3
                SentinelJobs.sendOrQueueAlert(context, topic, title, body, priority)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}
