package com.experiment.argus

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class BatteryState(val levelPct: Int, val tempC: Double?, val charging: Boolean)

fun readBattery(context: Context): BatteryState {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

    // temperature lives in the sticky ACTION_BATTERY_CHANGED broadcast, not in getIntProperty
    var tempC: Double? = null
    var charging = false
    runCatching {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent != null) {
            val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            if (rawTemp != Int.MIN_VALUE && rawTemp > 0) tempC = rawTemp / 10.0
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }
    }
    if (charging == false && bm != null) {
        runCatching { charging = bm.isCharging }
    }
    return BatteryState(level, tempC, charging)
}

fun batterySummary(context: Context, chargingOverride: Boolean? = null): String {
    val s = readBattery(context)
    val t = s.tempC?.toString() ?: "?"
    val charging = chargingOverride ?: s.charging
    return "battery " + s.levelPct + "% at " + t + "C, charging=" + charging
}

fun isCharging(context: Context): Boolean = readBattery(context).charging
