package com.experiment.argus

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Durable Companion feed containing user-visible events, newest first. */
object EventLogStore {
    private const val FILE = "argus_event_log"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 100

    @Synchronized
    fun append(context: Context, event: FeedEvent) {
        save(context, (listOf(event) + load(context)).take(MAX_EVENTS))
    }

    @Synchronized
    fun load(context: Context): List<FeedEvent> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_EVENTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val title = item.optString("title")
                    if (title.isEmpty()) continue
                    add(
                        FeedEvent(
                            id = item.optString("id").ifEmpty {
                                "legacy-$i-${item.optLong("timeSec")}-${title.hashCode()}-${item.optString("message").hashCode()}"
                            },
                            title = title,
                            message = item.optString("message"),
                            timeSec = item.optLong("timeSec")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_EVENTS)
            .apply()
    }

    @Synchronized
    fun remove(context: Context, eventId: String) {
        save(context, load(context).filterNot { it.id == eventId })
    }

    private fun save(context: Context, events: List<FeedEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(JSONObject().apply {
                put("id", event.id)
                put("title", event.title)
                put("message", event.message)
                put("timeSec", event.timeSec)
            })
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EVENTS, array.toString())
            .apply()
    }
}
