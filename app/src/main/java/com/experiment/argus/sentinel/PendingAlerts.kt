package com.experiment.argus.sentinel

import android.content.Context
import com.experiment.argus.Ntfy
import com.experiment.argus.RoleStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Small durable FIFO for power/reboot alerts that occur while home is offline. */
object PendingAlerts {
    private const val FILE = "argus_pending_alerts"
    private const val KEY_QUEUE = "queue"
    private const val MAX_PENDING = 50

    private data class Alert(
        val id: String,
        val topic: String,
        val title: String,
        val body: String,
        val priority: Int
    )

    @Synchronized
    fun enqueue(context: Context, topic: String, title: String, body: String, priority: Int) {
        val alerts = load(context).toMutableList()
        alerts += Alert(UUID.randomUUID().toString(), topic, title, body, priority)
        save(context, alerts.takeLast(MAX_PENDING))
    }

    /** Returns true only when no deliverable alerts remain. */
    @Synchronized
    fun flush(context: Context): Boolean {
        if (RoleStore.role(context) != "sentinel" ||
            !RoleStore.monitoringEnabled(context)
        ) {
            clear(context)
            return true
        }

        while (true) {
            val alerts = load(context)
            val first = alerts.firstOrNull() ?: return true

            // A channel change invalidates alerts addressed to the old private topic.
            if (first.topic != RoleStore.topic(context)) {
                save(context, alerts.drop(1))
                continue
            }

            val (ok) = Ntfy.send(first.topic, first.title, first.body, first.priority)
            if (!ok) return false
            save(context, alerts.drop(1))
        }
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_QUEUE)
            .apply()
    }

    private fun load(context: Context): List<Alert> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_QUEUE, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val topic = item.optString("topic")
                    val title = item.optString("title")
                    if (id.isEmpty() || topic.isEmpty() || title.isEmpty()) continue
                    add(Alert(id, topic, title, item.optString("body"), item.optInt("priority", 3)))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, alerts: List<Alert>) {
        val array = JSONArray()
        alerts.forEach { alert ->
            array.put(JSONObject().apply {
                put("id", alert.id)
                put("topic", alert.topic)
                put("title", alert.title)
                put("body", alert.body)
                put("priority", alert.priority)
            })
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUEUE, array.toString())
            .apply()
    }
}
