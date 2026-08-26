package com.experiment.argus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.experiment.argus.ui.DrawerTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DrawerTheme {
                Root()
            }
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
    Scaffold(
        topBar = {
            if (st.role != "none") {
                TopAppBar(
                    title = { Text(if (st.role == "sentinel") "Sentinel (home)" else "Companion") },
                    actions = { TextButton(onClick = { vm.setRole("none") }) { Text("Switch role") } }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        when (st.role) {
            "sentinel" -> SentinelScreen(vm, Modifier.padding(pad))
            "companion" -> CompanionScreen(vm, Modifier.padding(pad))
            else -> PickScreen(vm, Modifier.padding(pad))
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
            "One old phone guards your home. Your daily phone watches over it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        RoleCard(
            title = "This phone stays home",
            subtitle = "Plugs into a charger and reports power cuts, heartbeats, reboots.",
            icon = { Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(32.dp)) }
        ) { vm.setRole("sentinel") }
        Spacer(Modifier.height(16.dp))
        RoleCard(
            title = "This phone comes with me",
            subtitle = "Shows the live feed and warns when the home phone goes silent.",
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
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TopicEditor(st: UiState, vm: MainViewModel) {
    Column {
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
            "Both phones must use the exact same channel. Anyone who knows it can read it - keep it random.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun SentinelScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val st by vm.state.collectAsState()
    val serviceCharging by SentinelBus.charging.collectAsState()
    val charging = serviceCharging ?: isCharging(context)

    Column(modifier.fillMaxSize().padding(20.dp)) {

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle, contentDescription = null,
                    tint = if (charging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(if (charging) "On charger - on duty" else "NOT charging - alerts will say power lost!",
                        fontWeight = FontWeight.SemiBold)
                    Text(batterySummary(context), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Alert channel", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        TopicEditor(st, vm)

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (st.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            OutlinedButton(onClick = { vm.testAlert() }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Send test alert")
            }
        }

        Spacer(Modifier.height(24.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("Setup checklist", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "1. Plug this phone into a charger and leave it plugged in." + "\n" +
                    "2. Keep it on Wi-Fi." + "\n" +
                    "3. Tap the button below to exempt this app from battery killing." + "\n" +
                    "   Samsung path: Settings > Battery > Background limits > Never sleeping apps." + "\n" +
                    "4. Screen can stay off. That is fine.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    runCatching {
                        context.startActivity(Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + context.packageName)
                        ))
                    }
                }) { Text("Exempt from battery optimization") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val st by vm.state.collectAsState()

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        vm.startStream()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(modifier.fillMaxSize().padding(20.dp)) {

        StatusCard(st)

        Spacer(Modifier.height(18.dp))
        Text("Alert channel", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        TopicEditor(st, vm)
        if (st.topic.isNotEmpty()) {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND)
                send.type = "text/plain"
                send.putExtra(Intent.EXTRA_TEXT, st.topic)
                context.startActivity(Intent.createChooser(send, "Share channel name"))
            }) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(6.dp))
                Text("Share channel name")
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (st.live) "live" else "reconnecting...",
                style = MaterialTheme.typography.labelMedium,
                color = if (st.live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.reload(); vm.startStream() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(st.events) { ev ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(ev.title, fontWeight = FontWeight.SemiBold)
                        Text(ev.message, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmtTime(ev.timeSec), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(st: UiState) {
    val now = System.currentTimeMillis()
    val ageMs = if (st.lastHbAt == 0L) -1L else now - st.lastHbAt
    val ageMin = if (ageMs < 0L) -1 else (ageMs / 60000L).toInt()

    val pair = when {
        ageMin < 0 -> "No signal yet - waiting for first contact" to MaterialTheme.colorScheme.surfaceVariant
        ageMs <= 90_000L -> ("Seen " + fmtAgo(ageMin) + " - all good") to MaterialTheme.colorScheme.primaryContainer
        ageMs <= 300_000L -> ("Silent " + fmtAgo(ageMin) + " - check the connection") to MaterialTheme.colorScheme.tertiaryContainer
        else -> ("SILENT " + fmtAgo(ageMin) + " - something is wrong") to MaterialTheme.colorScheme.errorContainer
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = pair.second)) {
        Column(Modifier.padding(18.dp)) {
            Text(pair.first, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (st.lastBatt.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(st.lastBatt, style = MaterialTheme.typography.bodySmall)
            }
            if (st.lastPow.isNotEmpty()) {
                Text("Last power event: " + st.lastPow, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun fmtTime(sec: Long): String =
    SimpleDateFormat("MMM d  HH:mm", Locale.getDefault()).format(Date(sec * 1000L))

fun fmtAgo(minutes: Int): String =
    if (minutes <= 0) "just now"
    else if (minutes < 60) minutes.toString() + " min ago"
    else (minutes / 60).toString() + " h " + (minutes % 60) + " m ago"
