package com.experiment.argus

import android.content.Context
import java.net.URLDecoder
import java.net.URLEncoder

/** Compact backwards-compatible envelope identifying the home phone. */
object DeviceMessage {
    private const val PREFIX = "argus:v1"
    const val LEGACY_DEVICE_ID = "legacy-home"
    const val LEGACY_DEVICE_NAME = "Home phone"

    data class Decoded(
        val deviceId: String,
        val deviceName: String,
        val body: String
    )

    fun forThisPhone(context: Context, body: String): String = encode(
        RoleStore.deviceId(context),
        RoleStore.deviceName(context),
        body
    )

    fun encode(deviceId: String, deviceName: String, body: String): String {
        val encodedName = URLEncoder.encode(deviceName, Charsets.UTF_8.name())
        return "$PREFIX|$deviceId|$encodedName|$body"
    }

    fun decode(message: String): Decoded? {
        val parts = message.split('|', limit = 4)
        if (parts.size != 4 || parts[0] != PREFIX) return null
        val id = parts[1]
        if (!id.matches(Regex("[A-Za-z0-9_-]{4,80}"))) return null
        val name = runCatching {
            URLDecoder.decode(parts[2], Charsets.UTF_8.name())
        }.getOrNull()?.let(RoleStore::normalizeDeviceName) ?: return null
        return Decoded(id, name, parts[3])
    }

    fun decodeOrLegacy(message: String): Decoded = decode(message) ?: Decoded(
        LEGACY_DEVICE_ID,
        LEGACY_DEVICE_NAME,
        message
    )
}
