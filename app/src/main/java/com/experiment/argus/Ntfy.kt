package com.experiment.argus

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal ntfy.sh client - free pub/sub over plain HTTPS, no account needed.
 * The topic name is the shared secret between your two phones.
 */
object Ntfy {

    private val sendClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val streamClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // streams must never read-timeout
        .build()

    /** Fire-and-forget publish. Returns (ok, detail). */
    fun send(topic: String, title: String, body: String, priority: Int? = null): Pair<Boolean, String> =
        try {
            val builder = Request.Builder()
                .url("https://ntfy.sh/" + topic)
                .post(body.toRequestBody(null))
                .header("Title", title)
            if (priority != null) builder.header("Priority", priority.toString())
            sendClient.newCall(builder.build()).execute().use { resp ->
                Pair(resp.isSuccessful, "HTTP " + resp.code)
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: e.javaClass.simpleName)
        }

    /**
     * Blocking JSON-stream reader for live feed. Runs on a background thread;
     * returns when the connection drops or onEvent returns false. Caller loops
     * to reconnect.
     */
    fun stream(
        topic: String,
        onCallReady: (Call) -> Unit = {},
        onEvent: (title: String, message: String, timeSec: Long) -> Boolean
    ) {
        try {
            val req = Request.Builder().url("https://ntfy.sh/" + topic + "/json").build()
            val call = streamClient.newCall(req)
            onCallReady(call)
            call.execute().use { resp ->
                if (!resp.isSuccessful) return
                val src = resp.body?.source() ?: return
                while (true) {
                    val line = src.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    try {
                        val obj = JSONObject(line)
                        if (obj.optString("event") == "message") {
                            val keep = onEvent(
                                obj.optString("title"),
                                obj.optString("message"),
                                obj.optLong("time")
                            )
                            if (!keep) return
                        }
                    } catch (_: Exception) {
                        // skip malformed lines
                    }
                }
            }
        } catch (_: Exception) {
            // network hiccup - caller reconnects
        }
    }
}
