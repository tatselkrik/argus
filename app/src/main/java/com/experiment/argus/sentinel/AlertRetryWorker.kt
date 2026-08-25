package com.experiment.argus.sentinel

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.experiment.argus.Ntfy
import com.experiment.argus.RoleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Retries an alert that could not be delivered during a network outage. */
class AlertRetryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (RoleStore.role(applicationContext) != "sentinel") return Result.success()

        val topic = inputData.getString(KEY_TOPIC) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        val priority = inputData.getInt(KEY_PRIORITY, 3)

        // Do not leak an old event into a newly configured channel.
        if (RoleStore.topic(applicationContext) != topic) return Result.success()

        val (ok) = withContext(Dispatchers.IO) {
            Ntfy.send(topic, title, body, priority)
        }
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        const val KEY_TOPIC = "topic"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_PRIORITY = "priority"
    }
}
