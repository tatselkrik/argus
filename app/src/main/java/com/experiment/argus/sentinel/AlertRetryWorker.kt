package com.experiment.argus.sentinel

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.experiment.argus.RoleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Retries an alert that could not be delivered during a network outage. */
class AlertRetryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (RoleStore.role(applicationContext) != "sentinel" ||
            !RoleStore.monitoringEnabled(applicationContext)
        ) return Result.success()
        val empty = withContext(Dispatchers.IO) {
            PendingAlerts.flush(applicationContext)
        }
        return if (empty) Result.success() else Result.retry()
    }
}
