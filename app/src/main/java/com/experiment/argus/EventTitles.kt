package com.experiment.argus

/** User-facing event names shared by the Sentinel and Companion. */
object EventTitles {
    const val POWER_LOST = "Power lost at home"
    const val POWER_BACK = "Power is back"
    const val REBOOTED = "Phone has rebooted"
    const val OFFLINE = "Home phone is offline"
    const val HEARTBEAT = "[Heartbeat]"
    const val TEST = "[Test]"

    fun isPower(title: String): Boolean =
        title == POWER_LOST || title == POWER_BACK || title.startsWith("[Power")

    fun isReboot(title: String): Boolean = title == REBOOTED || title == "[Rebooted]"

    fun isVisibleInLog(title: String): Boolean = title != HEARTBEAT
}
