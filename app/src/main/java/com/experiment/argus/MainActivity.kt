package com.experiment.argus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.experiment.argus.ui.DrawerTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DrawerTheme { Root() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Root(vm: MainViewModel = viewModel()) {
    val st by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(st.message) {
        st.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    BackHandler(enabled = st.selectingRole) { vm.cancelRoleSwitch() }

    Scaffold(
        topBar = {
            when {
                st.selectingRole -> TopAppBar(
                    title = { Text("Choose role") },
                    navigationIcon = {
                        IconButton(onClick = { vm.cancelRoleSwitch() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                st.role != "none" -> TopAppBar(
                    title = {
                        Text(
                            if (st.role == "sentinel") "Home · ${st.deviceName}"
                            else "Away · ${st.deviceName}"
                        )
                    },
                    actions = {
                        TextButton(onClick = { vm.beginRoleSwitch() }) { Text("Switch role") }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        when {
            st.selectingRole || st.role == "none" -> PickScreen(vm, Modifier.padding(pad))
            st.role == "sentinel" -> SentinelScreen(vm, Modifier.padding(pad))
            else -> CompanionScreen(vm, Modifier.padding(pad))
        }
    }
}

@Composable
fun PickScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Drawer Phone", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "One or more home phones can share a channel. Your away phone watches the ones you select.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        RoleCard(
            title = "This phone stays home",
            subtitle = "Reports power cuts, check-ins, and reboots after you press Start.",
            icon = { Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(32.dp)) }
        ) { vm.setRole("sentinel") }
        Spacer(Modifier.height(16.dp))
        RoleCard(
            title = "This phone comes with me",
            subtitle = "Listens only after you press Start and watches selected home phones.",
            icon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(32.dp)) }
        ) { vm.setRole("companion") }
    }
}

@Composable
fun RoleCard(title: String, subtitle: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.size(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DeviceNameEditor(st: UiState, vm: MainViewModel) {
    Column {
        Text("Phone name", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = st.deviceNameInput,
            onValueChange = { vm.setDeviceNameInput(it) },
            label = { Text("Example: S10 Plus") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { vm.saveDeviceName() }) { Text("Save name") }
    }
}

@Composable
fun TopicEditor(st: UiState, vm: MainViewModel) {
    Column {
        Text("Alert channel", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = st.topicInput,
            onValueChange = { vm.setTopicInput(it) },
            label = { Text("Channel name (shared secret)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveTopic() }) { Text("Save") }
            OutlinedButton(onClick = { vm.generateTopic() }) { Text("Generate random") }
        }
        Text(
            "Every home and away phone in this group must use the same channel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun MonitoringControl(
    st: UiState,
    detailWhenStarted: String,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (st.monitoringEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (st.monitoringEnabled) "Started" else "Stopped",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (st.monitoringEnabled) detailWhenStarted else "No detection or alerts are running.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (st.monitoringEnabled) {
                OutlinedButton(onClick = onStop) { Text("Stop") }
            } else {
                Button(onClick = onStart) { Text("Start") }
            }
        }
    }
}

@Composable
fun SentinelScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val st by vm.state.collectAsState()
    val serviceCharging by SentinelBus.charging.collectAsState()
    val charging = serviceCharging ?: isCharging(context)
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.startMonitoring() }
    val start = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.startMonitoring()
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        DeviceNameEditor(st, vm)
        Spacer(Modifier.height(20.dp))
        TopicEditor(st, vm)
        Spacer(Modifier.height(20.dp))
        MonitoringControl(
            st,
            "Power monitoring and 30-minute check-ins are active.",
            start,
            vm::stopMonitoring
        )

        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = when {
                        !st.monitoringEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        charging -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        when {
                            !st.monitoringEnabled -> "Monitoring is stopped"
                            charging -> "On charger · monitoring"
                            else -> "Not charging · power lost alert sent"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        batterySummary(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (st.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            OutlinedButton(
                onClick = { vm.testAlert() },
                enabled = st.monitoringEnabled
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Send test alert")
            }
        }

        Spacer(Modifier.height(24.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Setup checklist", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "1. Give this phone a unique name and save the shared channel.\n" +
                        "2. Plug it into a charger and keep it on Wi-Fi.\n" +
                        "3. Exempt Argus from battery killing.\n" +
                        "   Samsung: Settings > Battery > Background limits > Never sleeping apps.\n" +
                        "4. Press Start. Use Stop whenever you do not want monitoring.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }) { Text("Exempt from battery optimization") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun CompanionScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val st by vm.state.collectAsState()
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.startMonitoring() }
    val start = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.startMonitoring()
        }
    }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st.monitoringEnabled) {
        while (st.monitoringEnabled) {
            delay(60_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item { DeviceNameEditor(st, vm) }
        item { TopicEditor(st, vm) }
        if (st.topic.isNotEmpty()) {
            item {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, st.topic)
                    }
                    context.startActivity(Intent.createChooser(send, "Share channel name"))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Share channel name")
                }
            }
        }
        item {
            MonitoringControl(
                st,
                if (st.live) "Connected and listening for selected home phones."
                else "Started and reconnecting to the alert channel.",
                start,
                vm::stopMonitoring
            )
        }
        if (st.monitoringEnabled) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (st.live) "Secure channel connected" else "Reconnecting…",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (st.live) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.reload(); vm.startStream() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            }
        }
        item { HomePhonesPanel(st, vm, nowMs) }
        item {
            Column {
                Text("Alert log", fontWeight = FontWeight.SemiBold)
                Text(
                    "Swipe an entry left or right to remove it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (st.events.isEmpty()) {
            item {
                Text(
                    "No saved alerts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(st.events, key = { it.id }) { event ->
                SwipeEventRow(event, vm)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun HomePhonesPanel(st: UiState, vm: MainViewModel, nowMs: Long) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Home phones", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = st.monitorAll,
                    onCheckedChange = { vm.setMonitorAll(it) }
                )
                Text("Monitor all home phones")
            }
            if (!st.monitorAll) {
                Text(
                    "Choose one or more phones below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (st.homeDevices.isEmpty()) {
                Text(
                    if (st.monitoringEnabled) {
                        "Waiting to discover a named home phone on this channel."
                    } else {
                        "Press Start to discover home phones on this channel."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                st.homeDevices.forEach { device ->
                    HomePhoneRow(device, st.monitorAll, st.monitoringEnabled, nowMs) { enabled ->
                        vm.setHomeDeviceMonitored(device.id, enabled)
                    }
                }
            }
        }
    }
}

@Composable
fun HomePhoneRow(
    device: HomeDeviceStatus,
    monitorAll: Boolean,
    monitoringActive: Boolean,
    nowMs: Long,
    onMonitoredChange: (Boolean) -> Unit
) {
    val ageMs = nowMs - device.lastContactAt
    val ageMin = (ageMs / 60_000L).coerceAtLeast(0L).toInt()
    val status: String
    val statusColor: Color
    when {
        !monitoringActive -> {
            status = "Away monitoring stopped"
            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
        !device.monitored -> {
            status = "Not monitored"
            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
        !device.active -> {
            status = "Home monitoring stopped"
            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
        ageMs < WatchdogTiming.HEARTBEAT_INTERVAL_MS -> {
            status = "Seen ${fmtAgo(ageMin)} · all good"
            statusColor = MaterialTheme.colorScheme.primary
        }
        ageMs < WatchdogTiming.OFFLINE_AFTER_MS -> {
            status = "One check-in missed"
            statusColor = MaterialTheme.colorScheme.tertiary
        }
        else -> {
            status = "Offline · two check-ins missed"
            statusColor = MaterialTheme.colorScheme.error
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = device.monitored,
            onCheckedChange = onMonitoredChange,
            enabled = !monitorAll
        )
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            Text(device.name, fontWeight = FontWeight.SemiBold)
            Text(status, style = MaterialTheme.typography.bodySmall, color = statusColor)
            if (device.lastBattery.isNotEmpty()) {
                Text(device.lastBattery, style = MaterialTheme.typography.bodySmall)
            }
            if (device.lastPower.isNotEmpty()) {
                Text("Last power: ${device.lastPower}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeEventRow(event: FeedEvent, vm: MainViewModel) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                vm.removeEvent(event.id)
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(event.title, fontWeight = FontWeight.SemiBold)
                Text(
                    event.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    fmtTime(event.timeSec),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun fmtTime(sec: Long): String =
    SimpleDateFormat("MMM d  HH:mm", Locale.getDefault()).format(Date(sec * 1000L))

fun fmtAgo(minutes: Int): String = when {
    minutes <= 0 -> "just now"
    minutes < 60 -> "$minutes min ago"
    else -> "${minutes / 60} h ${minutes % 60} m ago"
}
