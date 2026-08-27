package com.experiment.argus.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.experiment.argus.EventTitles
import com.experiment.argus.EventLogStore
import com.experiment.argus.DeviceMessage
import com.experiment.argus.FeedEvent
import com.experiment.argus.HomeDeviceStore
import com.experiment.argus.MainActivity
import com.experiment.argus.Ntfy
import com.experiment.argus.R
import com.experiment.argus.RoleStore
import com.experiment.argus.StreamBus
import com.experiment.argus.WatchdogTiming
import okhttp3.Call
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap

/**
 * User-started foreground service that keeps the ntfy stream alive even when
 * the app UI is swiped away. Raises a notification for selected home phones;
 * heartbeats only update the stored status silently.
 *
 * The persistent low-priority "Argus is watching home" notification is the
 * trade Android requires for background work - and honestly, it is also nice:
 * one glance confirms the watchdog is alive.
 */
class EventStreamService : Service() {

    private val generation = AtomicInteger(0)
    private val activeCall = AtomicReference<Call?>(null)
    private var worker: Thread? = null
    private var monitor: Thread? = null
    private val offlineNotified = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var monitoringSince = 0L

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val topic = RoleStore.topic(this)
        if (RoleStore.role(this) != "companion" ||
            !RoleStore.monitoringEnabled(this) ||
            topic.isEmpty()
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(SERVICE_ID, serviceNotification())
        StreamBus.running.value = true

        // restart the stream thread cleanly if topic changed or service re-started
        stopStreamWorker()
        val myGeneration = generation.incrementAndGet()
        monitoringSince = System.currentTimeMillis()
        offlineNotified.clear()
        worker = thread(name = "argus-stream") {
            while (generation.get() == myGeneration) {
                if (RoleStore.role(applicationContext) != "companion" ||
                    !RoleStore.monitoringEnabled(applicationContext)
                ) break
                val current = RoleStore.topic(applicationContext)
                if (current.isEmpty()) break
                var iterationCall: Call? = null
                Ntfy.stream(
                    current,
                    onCallReady = { call ->
                        iterationCall = call
                        if (generation.get() == myGeneration) {
                            activeCall.set(call)
                        } else {
                            call.cancel()
                        }
                    }
                ) { title, message, timeSec ->
                    if (generation.get() != myGeneration) {
                        false
                    } else {
                        deliver(title, message, timeSec)
                        true
                    }
                }
                iterationCall?.let { activeCall.compareAndSet(it, null) }
                if (generation.get() != myGeneration) break
                try {
                    Thread.sleep(3000) // reconnect loop
                } catch (_: InterruptedException) {
                    break
                }
            }
            if (generation.get() == myGeneration) StreamBus.running.value = false
        }
        monitor = thread(name = "argus-health-monitor") {
            while (generation.get() == myGeneration &&
                RoleStore.monitoringEnabled(applicationContext)
            ) {
                checkForSilence()
                try {
                    Thread.sleep(WatchdogTiming.MONITOR_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        return START_STICKY
    }

    private fun deliver(title: String, message: String, timeSec: Long) {
        if (!RoleStore.monitoringEnabled(this)) return
        val source = DeviceMessage.decodeOrLegacy(message)
        HomeDeviceStore.noteContact(this, source, title)
        offlineNotified.remove(source.deviceId)

        val displayTitle = titleForDevice(source.deviceName, title)
        val event = FeedEvent(displayTitle, source.body, timeSec)
        val shouldSurface = HomeDeviceStore.isMonitored(this, source.deviceId) &&
            EventTitles.isVisibleInLog(title)
        if (shouldSurface) EventLogStore.append(this, event)
        StreamBus.events.tryEmit(event)
        if (shouldSurface) notifyEvent(displayTitle, source.body)
    }

    private fun checkForSilence() {
        if (!RoleStore.monitoringEnabled(this)) return
        val devices = HomeDeviceStore.load(this).filter { it.monitored }
        offlineNotified.retainAll(devices.map { it.id }.toSet())
        val now = System.currentTimeMillis()
        devices.forEach { device ->
            val reference = maxOf(device.lastContactAt, monitoringSince)
            if (now - reference >= WatchdogTiming.OFFLINE_AFTER_MS &&
                offlineNotified.add(device.id)
            ) {
                val title = "${device.name} is offline"
                val message =
                    "No contact for one hour (two expected check-ins missed). Wi-Fi, internet, power, or Argus monitoring may be unavailable."
                val event = FeedEvent(title, message, now / 1000L)
                EventLogStore.append(this, event)
                StreamBus.events.tryEmit(event)
                notifyEvent(title, message)
            }
        }
    }

    private fun titleForDevice(deviceName: String, title: String): String = when (title) {
        EventTitles.TEST -> "$deviceName: Test alert"
        else -> "$deviceName: $title"
    }

    override fun onDestroy() {
        stopStreamWorker()
        StreamBus.running.value = false
        super.onDestroy()
    }

    private fun stopStreamWorker() {
        generation.incrementAndGet()
        activeCall.getAndSet(null)?.cancel()
        worker?.interrupt()
        worker?.join(1000)
        worker = null
        monitor?.interrupt()
        monitor?.join(1000)
        monitor = null
    }

    // ------------------------------------------------------------- notifications

    private fun serviceNotification(): Notification =
        NotificationCompat.Builder(this, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Argus: ${RoleStore.deviceName(this)}")
            .setContentText("Listening for selected home phones")
            .setOngoing(true)
            .setContentIntent(mainPendingIntent())
            .build()

    private fun notifyEvent(title: String, message: String) {
        val n = NotificationCompat.Builder(this, CH_EVENTS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent())
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(title.hashCode(), n)
        }
    }

    private fun mainPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        private const val SERVICE_ID = 42
        private const val CH_EVENTS = "argus_events"
        private const val CH_SERVICE = "argus_service"
        fun ensureChannels(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CH_EVENTS, "Home events", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Power cuts and watchdog alerts"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_SERVICE, "Watching status", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Persistent watcher notification"
                    setSound(null, null)
                }
            )
        }
    }
}
