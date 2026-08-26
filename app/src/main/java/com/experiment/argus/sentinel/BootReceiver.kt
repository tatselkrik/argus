package com.experiment.argus.sentinel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.experiment.argus.EventTitles
import com.experiment.argus.RoleStore

/** After a reboot/restart, resume heartbeats and tell the companion it happened. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (RoleStore.role(context) != "sentinel") return
        val topic = RoleStore.topic(context)
        if (topic.isEmpty()) return

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
