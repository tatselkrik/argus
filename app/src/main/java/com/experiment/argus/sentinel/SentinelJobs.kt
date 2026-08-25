package com.experiment.argus.sentinel

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.experiment.argus.RoleStore
import java.util.concurrent.TimeUnit

object SentinelJobs {
    private const val HEARTBEAT_WORK = "hourly-heartbeat"
    private const val ALERT_RETRY_TAG = "sentinel-alert-retry"

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
            cancelAllWorkByTag(ALERT_RETRY_TAG)
        }
    }

    /** Persists a failed alert and retries it with exponential backoff. */
    fun retryAlert(
        context: Context,
        topic: String,
        title: String,
        body: String,
        priority: Int
    ) {
        if (RoleStore.role(context) != "sentinel") return
        val input = Data.Builder()
            .putString(AlertRetryWorker.KEY_TOPIC, topic)
            .putString(AlertRetryWorker.KEY_TITLE, title)
            .putString(AlertRetryWorker.KEY_BODY, body)
            .putInt(AlertRetryWorker.KEY_PRIORITY, priority)
            .build()
        val request = OneTimeWorkRequestBuilder<AlertRetryWorker>()
            .setInputData(input)
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(ALERT_RETRY_TAG)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
