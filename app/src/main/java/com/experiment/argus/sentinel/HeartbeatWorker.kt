package com.experiment.argus.sentinel

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.experiment.argus.Ntfy
import com.experiment.argus.RoleStore
import com.experiment.argus.batterySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hourly proof-of-life ping so the companion can detect a dead/silenced phone. */
class HeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (RoleStore.role(applicationContext) != "sentinel") return Result.success()
        val topic = RoleStore.topic(applicationContext)
        if (topic.isEmpty()) return Result.success()
        val summary = batterySummary(applicationContext)
        val (ok) = withContext(Dispatchers.IO) {
            Ntfy.send(topic, "[Heartbeat]", summary)
        }
        return if (ok) Result.success() else Result.retry()
    }
}
