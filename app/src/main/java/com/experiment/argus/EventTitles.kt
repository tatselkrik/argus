package com.experiment.argus

/** User-facing event names shared by the Sentinel and Companion. */
object EventTitles {
    const val POWER_LOST = "Power lost at home"
    const val POWER_BACK = "Power is back"
    const val REBOOTED = "Phone has rebooted"
    const val OFFLINE = "Home phone is offline"
    const val HEARTBEAT = "[Heartbeat]"
    const val PAUSED = "[Monitoring stopped]"
    const val TEST = "[Test]"

    fun isPower(title: String): Boolean =
        title == POWER_LOST || title == POWER_BACK || title.startsWith("[Power")

    fun isReboot(title: String): Boolean = title == REBOOTED || title == "[Rebooted]"

    fun isVisibleInLog(title: String): Boolean = title != HEARTBEAT && title != PAUSED
}

/** Shared watchdog timing so sender, receiver, UI, and tests stay aligned. */
object WatchdogTiming {
    const val HEARTBEAT_INTERVAL_MINUTES = 30L
    const val MISSED_HEARTBEATS_BEFORE_OFFLINE = 2L
    const val OFFLINE_AFTER_MINUTES =
        HEARTBEAT_INTERVAL_MINUTES * MISSED_HEARTBEATS_BEFORE_OFFLINE
    const val HEARTBEAT_INTERVAL_MS = HEARTBEAT_INTERVAL_MINUTES * 60_000L
    const val OFFLINE_AFTER_MS = OFFLINE_AFTER_MINUTES * 60_000L
    const val MONITOR_INTERVAL_MS = 60_000L
}
