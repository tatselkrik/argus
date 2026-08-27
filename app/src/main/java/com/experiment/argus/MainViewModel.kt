package com.experiment.argus

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experiment.argus.push.EventStreamService
import com.experiment.argus.sentinel.SentinelJobs
import com.experiment.argus.sentinel.SentinelService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class FeedEvent(
    val title: String,
    val message: String,
    val timeSec: Long,
    val id: String = UUID.randomUUID().toString()
)

data class UiState(
    val role: String = "none",
    val selectingRole: Boolean = false,
    val monitoringEnabled: Boolean = false,
    val deviceName: String = "",
    val deviceNameInput: String = "",
    val topic: String = "",
    val topicInput: String = "",
    val events: List<FeedEvent> = emptyList(),
    val homeDevices: List<HomeDeviceStatus> = emptyList(),
    val monitorAll: Boolean = true,
    val live: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        reload()
        viewModelScope.launch {
            StreamBus.running.collect { live -> _state.update { it.copy(live = live) } }
        }
        viewModelScope.launch {
            StreamBus.events.collect { refreshCompanionData() }
        }
        reconcileBackgroundWork()
    }

    fun reload() {
        val ctx = getApplication<Application>()
        val name = RoleStore.deviceName(ctx)
        _state.update {
            it.copy(
                role = RoleStore.role(ctx),
                monitoringEnabled = RoleStore.monitoringEnabled(ctx),
                deviceName = name,
                deviceNameInput = name,
                topic = RoleStore.topic(ctx),
                topicInput = RoleStore.topic(ctx),
                events = EventLogStore.load(ctx),
                homeDevices = HomeDeviceStore.load(ctx),
                monitorAll = HomeDeviceStore.monitorAll(ctx)
            )
        }
    }

    fun beginRoleSwitch() = _state.update { it.copy(selectingRole = true) }

    fun cancelRoleSwitch() = _state.update { it.copy(selectingRole = false) }

    fun setRole(role: String) {
        val ctx = getApplication<Application>()
        RoleStore.setMonitoringEnabled(ctx, false)
        SentinelJobs.cancel(ctx)
        SentinelService.stop(ctx)
        stopStream()
        RoleStore.setRole(ctx, role)
        _state.update { it.copy(selectingRole = false) }
        reload()
    }

    fun startMonitoring() {
        val ctx = getApplication<Application>()
        val role = RoleStore.role(ctx)
        if (role == "none") {
            _state.update { it.copy(message = "Choose a role first.") }
            return
        }
        if (RoleStore.topic(ctx).isEmpty()) {
            _state.update { it.copy(message = "Save the shared channel before starting.") }
            return
        }
        RoleStore.setMonitoringEnabled(ctx, true)
        when (role) {
            "sentinel" -> {
                SentinelJobs.ensure(ctx)
                runCatching { SentinelService.start(ctx) }
            }
            "companion" -> startStream()
        }
        reload()
        _state.update { it.copy(message = "Argus started.") }
    }

    fun stopMonitoring() {
        val ctx = getApplication<Application>()
        RoleStore.setMonitoringEnabled(ctx, false)
        SentinelJobs.cancel(ctx)
        SentinelService.stop(ctx)
        stopStream()
        reload()
        _state.update { it.copy(message = "Argus stopped.") }
    }

    fun setDeviceNameInput(value: String) = _state.update { it.copy(deviceNameInput = value) }

    fun saveDeviceName() {
        val normalized = RoleStore.normalizeDeviceName(_state.value.deviceNameInput)
        if (normalized == null) {
            _state.update { it.copy(message = "Phone name must be 1-40 characters.") }
            return
        }
        val ctx = getApplication<Application>()
        RoleStore.setDeviceName(ctx, normalized)
        if (RoleStore.monitoringEnabled(ctx)) {
            when (RoleStore.role(ctx)) {
                "sentinel" -> runCatching { SentinelService.start(ctx) }
                "companion" -> startStream()
            }
        }
        reload()
        _state.update { it.copy(message = "Phone name saved.") }
    }

    fun setTopicInput(value: String) = _state.update { it.copy(topicInput = value) }

    fun saveTopic() {
        val normalized = RoleStore.normalizeTopic(_state.value.topicInput)
        if (normalized == null) {
            _state.update { it.copy(message = "Topic may contain only letters, numbers, dash, underscore.") }
            return
        }
        val ctx = getApplication<Application>()
        RoleStore.setTopic(ctx, normalized)
        if (RoleStore.monitoringEnabled(ctx)) {
            when (RoleStore.role(ctx)) {
                "sentinel" -> {
                    SentinelJobs.ensure(ctx)
                    runCatching { SentinelService.start(ctx) }
                    stopStream()
                }
                "companion" -> startStream()
            }
        }
        reload()
        _state.update { it.copy(message = "Channel saved.") }
    }

    fun generateTopic() = _state.update { it.copy(topicInput = RoleStore.generateTopic()) }

    /** Sends a named Sentinel test event through the full notification path. */
    fun testAlert() {
        val ctx = getApplication<Application>()
        if (!RoleStore.monitoringEnabled(ctx)) {
            _state.update { it.copy(message = "Press Start before sending a test alert.") }
            return
        }
        val topic = _state.value.topic.ifEmpty { RoleStore.topic(ctx) }
        if (topic.isEmpty()) {
            _state.update { it.copy(message = "Set a channel first.") }
            return
        }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val body = DeviceMessage.forThisPhone(
                ctx,
                "Hello from ${RoleStore.deviceName(ctx)}."
            )
            val (ok, detail) = Ntfy.send(topic, EventTitles.TEST, body, priority = 3)
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        busy = false,
                        message = if (ok) "Test sent ($detail)" else "Send failed: $detail"
                    )
                }
            }
        }
    }

    /** Ensures the Companion foreground service owns the actual connection. */
    fun startStream() {
        val ctx = getApplication<Application>()
        if (RoleStore.role(ctx) != "companion" ||
            !RoleStore.monitoringEnabled(ctx) ||
            RoleStore.topic(ctx).isEmpty()
        ) return
        runCatching {
            ContextCompat.startForegroundService(ctx, Intent(ctx, EventStreamService::class.java))
        }
    }

    fun stopStream() {
        val ctx = getApplication<Application>()
        runCatching { ctx.stopService(Intent(ctx, EventStreamService::class.java)) }
        _state.update { it.copy(live = false) }
    }

    fun setMonitorAll(enabled: Boolean) {
        HomeDeviceStore.setMonitorAll(getApplication(), enabled)
        refreshCompanionData()
    }

    fun setHomeDeviceMonitored(deviceId: String, monitored: Boolean) {
        HomeDeviceStore.setMonitored(getApplication(), deviceId, monitored)
        refreshCompanionData()
    }

    fun removeEvent(eventId: String) {
        EventLogStore.remove(getApplication(), eventId)
        refreshCompanionData()
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun refreshCompanionData() {
        val ctx = getApplication<Application>()
        _state.update {
            it.copy(
                events = EventLogStore.load(ctx),
                homeDevices = HomeDeviceStore.load(ctx),
                monitorAll = HomeDeviceStore.monitorAll(ctx)
            )
        }
    }

    private fun reconcileBackgroundWork() {
        val ctx = getApplication<Application>()
        if (!RoleStore.monitoringEnabled(ctx)) {
            SentinelJobs.cancel(ctx)
            SentinelService.stop(ctx)
            stopStream()
            return
        }
        when (RoleStore.role(ctx)) {
            "sentinel" -> {
                SentinelJobs.ensure(ctx)
                runCatching { SentinelService.start(ctx) }
            }
            "companion" -> startStream()
            else -> stopMonitoring()
        }
    }
}
