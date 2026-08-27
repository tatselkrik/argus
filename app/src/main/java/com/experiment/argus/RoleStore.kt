package com.experiment.argus

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.security.SecureRandom
import java.util.UUID

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

    // explicit user control: background work runs only while this is enabled
    fun monitoringEnabled(context: Context): Boolean =
        prefs(context).getBoolean("monitoringEnabled", false)

    fun setMonitoringEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("monitoringEnabled", enabled).apply()

    // stable device identity used to distinguish multiple phones on one topic
    @Synchronized
    fun deviceId(context: Context): String {
        val existing = prefs(context).getString("deviceId", null)
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        prefs(context).edit().putString("deviceId", generated).apply()
        return generated
    }

    fun deviceName(context: Context): String =
        prefs(context).getString("deviceName", null)?.let(::normalizeDeviceName)
            ?: normalizeDeviceName(Build.MODEL)
            ?: "Android phone"

    fun setDeviceName(context: Context, name: String) =
        prefs(context).edit().putString("deviceName", name).apply()

    // topic (shared channel name)
    fun topic(context: Context): String = prefs(context).getString("topic", "") ?: ""
    fun setTopic(context: Context, topic: String) {
        val normalized = topic.trim()
        val editor = prefs(context).edit().putString("topic", normalized)
        if (normalized != this.topic(context)) {
            editor.remove("lastHbAt").remove("lastBatt").remove("lastPow")
            EventLogStore.clear(context)
            HomeDeviceStore.clear(context)
        }
        editor.apply()
    }

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

    fun normalizeDeviceName(input: String): String? {
        val name = input.trim()
        return if (name.isNotEmpty() && name.length <= 40 && name.none { it.isISOControl() }) {
            name
        } else {
            null
        }
    }
}
