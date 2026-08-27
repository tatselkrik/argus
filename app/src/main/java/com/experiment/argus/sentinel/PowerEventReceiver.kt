package com.experiment.argus.sentinel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.experiment.argus.EventTitles
import com.experiment.argus.RoleStore
import com.experiment.argus.SentinelBus
import com.experiment.argus.batterySummary

/**
 * Runtime receiver owned by SentinelService. The service keeps registration
 * active on Samsung/modern Android devices that suppress manifest delivery.
 */
class PowerEventReceiver(
    private val onPowerChanged: ((charging: Boolean) -> Unit)? = null
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (RoleStore.role(context) != "sentinel" ||
            !RoleStore.monitoringEnabled(context)
        ) return
        val topic = RoleStore.topic(context)
        if (topic.isEmpty()) return

        val lost = intent.action == Intent.ACTION_POWER_DISCONNECTED
        SentinelBus.charging.value = !lost
        onPowerChanged?.invoke(!lost)
        val pending = goAsync()
        Thread {
            try {
                val title = if (lost) EventTitles.POWER_LOST else EventTitles.POWER_BACK
                val body = batterySummary(context, chargingOverride = !lost)
                val priority = if (lost) 4 else 3
                SentinelJobs.sendOrQueueAlert(context, topic, title, body, priority)
                SentinelJobs.ensure(context)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}
