package com.experiment.argus

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Bridge between the user-started foreground service and any UI that is open.
 * The service owns the network connection; the ViewModel just observes.
 */
object StreamBus {
    val events = MutableSharedFlow<FeedEvent>(replay = 0, extraBufferCapacity = 64)
    val running = MutableStateFlow(false)
}
