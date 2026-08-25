package com.experiment.argus

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experiment.argus.push.EventStreamService
import com.experiment.argus.sentinel.SentinelJobs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FeedEvent(val title: String, val message: String, val timeSec: Long)

data class UiState(
    val role: String = "none",
    val topic: String = "",
    val topicInput: String = "",
    val events: List<FeedEvent> = emptyList(),
    val live: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val lastHbAt: Long = 0L,
    val lastBatt: String = "",
    val lastPow: String = ""
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        reload()
        // observe service state and live events pushed through the bus
        viewModelScope.launch {
            StreamBus.running.collect { live -> _state.update { it.copy(live = live) } }
        }
        viewModelScope.launch {
            StreamBus.events.collect { ev -> handleEvent(ev.title, ev.message, ev.timeSec) }
        }
        if (RoleStore.role(app) == "companion" && RoleStore.topic(app).isNotEmpty()) {
            startStream()
        }
    }

    fun reload() {
        val ctx = getApplication<Application>()
        _state.update {
            it.copy(
                role = RoleStore.role(ctx),
                topic = RoleStore.topic(ctx),
                topicInput = RoleStore.topic(ctx),
                lastHbAt = RoleStore.lastHeartbeatAt(ctx),
                lastBatt = RoleStore.lastBatteryText(ctx),
                lastPow = RoleStore.lastPowerText(ctx)
            )
        }
    }

    fun setRole(role: String) {
        RoleStore.setRole(getApplication(), role)
        if (role == "sentinel") {
            SentinelJobs.ensure(getApplication())
        } else {
            SentinelJobs.cancel(getApplication())
        }
        if (role == "companion") {
            startStream()
        } else {
            stopStream()
        }
        reload()
    }

    fun setTopicInput(value: String) = _state.update { it.copy(topicInput = value) }

    fun saveTopic() {
        val normalized = RoleStore.normalizeTopic(_state.value.topicInput)
        if (normalized == null) {
            _state.update { it.copy(message = "Topic may contain only letters, numbers, dash, underscore.") }
            return
        }
        RoleStore.setTopic(getApplication(), normalized)
        when (_state.value.role) {
            "sentinel" -> {
                SentinelJobs.ensure(getApplication())
                stopStream()
            }
            "companion" -> {
                startStream()
            }
            else -> stopStream()
        }
        _state.update { it.copy(message = "Channel saved: " + normalized) }
        reload()
    }

    fun generateTopic() = _state.update { it.copy(topicInput = RoleStore.generateTopic()) }

    /** Sends a test publish so the user can verify the whole chain end to end. */
    fun testAlert() {
        val topic = _state.value.topic.ifEmpty { RoleStore.topic(getApplication()) }
        if (topic.isEmpty()) {
            _state.update { it.copy(message = "Set a channel first.") }
            return
        }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, detail) = Ntfy.send(topic, "[Test]", "Hello from " +
                (if (RoleStore.role(getApplication()) == "sentinel") "the home phone." else "your pocket phone."),
                priority = 3)
            withContext(Dispatchers.Main) {
                _state.update { it.copy(busy = false, message = if (ok) "Test sent (" + detail + ")" else "Send failed: " + detail) }
            }
        }
    }

    /** Ensures the foreground service is running - it owns the actual connection. */
    fun startStream() {
        if (RoleStore.role(getApplication()) != "companion" ||
            RoleStore.topic(getApplication()).isEmpty()
        ) return
        runCatching {
            ContextCompat.startForegroundService(
                getApplication(),
                Intent(getApplication(), EventStreamService::class.java)
            )
        }
    }

    fun stopStream() {
        runCatching {
            getApplication<Application>().stopService(
                Intent(getApplication(), EventStreamService::class.java)
            )
        }
        _state.update { it.copy(live = false) }
    }

    private fun handleEvent(title: String, message: String, timeSec: Long) {
        val ctx = getApplication<Application>()
        when {
            title == "[Heartbeat]" -> RoleStore.noteHeartbeat(ctx, message, RoleStore.lastPowerText(ctx))
            title.startsWith("[Power") -> RoleStore.notePowerEvent(ctx, title + "  " + message)
            title == "[Rebooted]" -> RoleStore.notePowerEvent(ctx, title)
        }
        run {
            _state.update {
                it.copy(
                    events = (listOf(FeedEvent(title, message, timeSec)) + it.events).take(100),
                    lastHbAt = RoleStore.lastHeartbeatAt(ctx),
                    lastBatt = RoleStore.lastBatteryText(ctx),
                    lastPow = RoleStore.lastPowerText(ctx)
                )
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // Intentionally do NOT stop the service here: push must survive the app being closed.
}
