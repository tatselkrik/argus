package com.experiment.argus.sentinel

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.experiment.argus.Ntfy
import com.experiment.argus.RoleStore
import java.util.concurrent.TimeUnit

object SentinelJobs {
    private const val HEARTBEAT_WORK = "hourly-heartbeat"
    private const val ALERT_RETRY_WORK = "sentinel-alert-retry"

    private val connected = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun ensure(context: Context) {
        if (RoleStore.role(context) != "sentinel") {
            cancel(context)
            return
        }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HEARTBEAT_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<HeartbeatWorker>(1, TimeUnit.HOURS)
                .setConstraints(connected)
                .build()
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).run {
            cancelUniqueWork(HEARTBEAT_WORK)
            cancelUniqueWork(ALERT_RETRY_WORK)
        }
        PendingAlerts.clear(context)
    }

    /** Tries now, then persists the alert if the network is unavailable. */
    fun sendOrQueueAlert(
        context: Context,
        topic: String,
        title: String,
        body: String,
        priority: Int
    ) {
        if (RoleStore.role(context) != "sentinel") return
        val (ok) = Ntfy.send(topic, title, body, priority)
        if (ok) return

        PendingAlerts.enqueue(context, topic, title, body, priority)
        scheduleAlertFlush(context)
    }

    fun flushPendingNow(context: Context) {
        if (!PendingAlerts.flush(context)) scheduleAlertFlush(context)
    }

    private fun scheduleAlertFlush(context: Context) {
        val request = OneTimeWorkRequestBuilder<AlertRetryWorker>()
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ALERT_RETRY_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
