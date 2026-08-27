package com.experiment.argus

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HomeDeviceStatus(
    val id: String,
    val name: String,
    val lastContactAt: Long,
    val lastBattery: String,
    val lastPower: String,
    val monitored: Boolean
)

/** Companion-side per-home-phone status and monitoring selection. */
object HomeDeviceStore {
    private const val FILE = "argus_home_devices"
    private const val KEY_DEVICES = "devices"
    private const val KEY_MONITOR_ALL = "monitorAll"
    private const val KEY_SELECTED = "selectedIds"
    private const val MAX_DEVICES = 20

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    fun load(context: Context): List<HomeDeviceStatus> {
        val monitorAll = monitorAll(context)
        val selected = selectedIds(context)
        return loadRaw(context).map { device ->
            device.copy(monitored = monitorAll || device.id in selected)
        }
    }

    @Synchronized
    fun noteContact(
        context: Context,
        source: DeviceMessage.Decoded,
        title: String,
        receivedAt: Long = System.currentTimeMillis()
    ) {
        val devices = loadRaw(context).toMutableList()
        val index = devices.indexOfFirst { it.id == source.deviceId }
        val old = devices.getOrNull(index)
        val updated = HomeDeviceStatus(
            id = source.deviceId,
            name = source.deviceName,
            lastContactAt = receivedAt,
            lastBattery = if (title == EventTitles.HEARTBEAT) source.body else old?.lastBattery.orEmpty(),
            lastPower = when {
                EventTitles.isPower(title) -> "$title  ${source.body}"
                EventTitles.isReboot(title) -> title
                else -> old?.lastPower.orEmpty()
            },
            monitored = false
        )
        if (index >= 0) devices[index] = updated else devices.add(0, updated)
        saveRaw(context, devices.sortedByDescending { it.lastContactAt }.take(MAX_DEVICES))
    }

    fun monitorAll(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MONITOR_ALL, true)

    @Synchronized
    fun setMonitorAll(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MONITOR_ALL, enabled).apply()
    }

    @Synchronized
    fun setMonitored(context: Context, deviceId: String, monitored: Boolean) {
        val selected = selectedIds(context).toMutableSet()
        if (monitored) selected += deviceId else selected -= deviceId
        prefs(context).edit().putStringSet(KEY_SELECTED, selected).apply()
    }

    fun isMonitored(context: Context, deviceId: String): Boolean =
        monitorAll(context) || deviceId in selectedIds(context)

    @Synchronized
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun selectedIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SELECTED, emptySet())?.toSet().orEmpty()

    private fun loadRaw(context: Context): List<HomeDeviceStatus> {
        val raw = prefs(context).getString(KEY_DEVICES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val name = item.optString("name")
                    if (id.isEmpty() || name.isEmpty()) continue
                    add(
                        HomeDeviceStatus(
                            id = id,
                            name = name,
                            lastContactAt = item.optLong("lastContactAt"),
                            lastBattery = item.optString("lastBattery"),
                            lastPower = item.optString("lastPower"),
                            monitored = false
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveRaw(context: Context, devices: List<HomeDeviceStatus>) {
        val array = JSONArray()
        devices.forEach { device ->
            array.put(JSONObject().apply {
                put("id", device.id)
                put("name", device.name)
                put("lastContactAt", device.lastContactAt)
                put("lastBattery", device.lastBattery)
                put("lastPower", device.lastPower)
            })
        }
        prefs(context).edit().putString(KEY_DEVICES, array.toString()).apply()
    }
}
