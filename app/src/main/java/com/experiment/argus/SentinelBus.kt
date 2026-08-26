package com.experiment.argus

import kotlinx.coroutines.flow.MutableStateFlow

/** Live process state shared by the Sentinel foreground service and its UI. */
object SentinelBus {
    val charging = MutableStateFlow<Boolean?>(null)
}
