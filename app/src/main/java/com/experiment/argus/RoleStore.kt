package com.experiment.argus

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

/** Tiny SharedPreferences wrapper holding role, topic and last-known status. */
object RoleStore {

    private const val FILE = "argus_prefs"
    private val secureRandom = SecureRandom()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // roles
    fun role(context: Context): String = prefs(context).getString("role", "none") ?: "none"
    fun setRole(context: Context, role: String) =
        prefs(context).edit().putString("role", role).apply()

    // topic (shared channel name)
    fun topic(context: Context): String = prefs(context).getString("topic", "") ?: ""
    fun setTopic(context: Context, topic: String) {
        val normalized = topic.trim()
        val editor = prefs(context).edit().putString("topic", normalized)
        if (normalized != this.topic(context)) {
            editor.remove("lastHbAt").remove("lastBatt").remove("lastPow")
        }
        editor.apply()
    }

    // companion-side last known state
    fun lastHeartbeatAt(context: Context): Long = prefs(context).getLong("lastHbAt", 0L)
    fun lastBatteryText(context: Context): String = prefs(context).getString("lastBatt", "") ?: ""
    fun lastPowerText(context: Context): String = prefs(context).getString("lastPow", "") ?: ""

    fun noteContact(context: Context) =
        prefs(context).edit().putLong("lastHbAt", System.currentTimeMillis()).apply()

    fun noteHeartbeat(context: Context, batteryText: String, powerText: String) =
        prefs(context).edit()
            .putLong("lastHbAt", System.currentTimeMillis())
            .putString("lastBatt", batteryText)
            .putString("lastPow", powerText)
            .apply()

    fun notePowerEvent(context: Context, summary: String) =
        prefs(context).edit()
            .putLong("lastHbAt", System.currentTimeMillis())
            .putString("lastPow", summary)
            .apply()

    /** Accepts a bare topic or a pasted https://ntfy.sh/<topic> URL. */
    fun normalizeTopic(input: String): String? {
        var t = input.trim()
        if (t.startsWith("http")) {
            val idx = t.lastIndexOf('/')
            if (idx >= 0) t = t.substring(idx + 1)
        }
        t = t.trim()
        return if (t.matches(Regex("[A-Za-z0-9_-]{4,80}"))) t else null
    }

    fun generateTopic(): String {
        val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
        return buildString {
            append("drawer-")
            repeat(16) {
                append(alphabet[secureRandom.nextInt(alphabet.length)])
            }
        }
    }
}
