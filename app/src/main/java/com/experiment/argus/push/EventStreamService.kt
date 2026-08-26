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
import com.experiment.argus.FeedEvent
import com.experiment.argus.MainActivity
import com.experiment.argus.Ntfy
import com.experiment.argus.R
import com.experiment.argus.RoleStore
import com.experiment.argus.StreamBus
import okhttp3.Call
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground service that keeps the ntfy stream alive even when the app UI is
 * swiped away. Raises a system notification for every power/reboot/test event;
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
    @Volatile private var offlineNotified = false
    @Volatile private var monitoringSince = 0L

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val topic = RoleStore.topic(this)
        if (RoleStore.role(this) != "companion" || topic.isEmpty()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(SERVICE_ID, serviceNotification())
        StreamBus.running.value = true

        // restart the stream thread cleanly if topic changed or service re-started
        stopStreamWorker()
        val myGeneration = generation.incrementAndGet()
        monitoringSince = System.currentTimeMillis()
        offlineNotified = false
        worker = thread(name = "argus-stream") {
            while (generation.get() == myGeneration) {
                if (RoleStore.role(applicationContext) != "companion") break
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
            while (generation.get() == myGeneration) {
                checkForSilence()
                try {
                    Thread.sleep(15_000)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        return START_STICKY
    }

    private fun deliver(title: String, message: String, timeSec: Long) {
        RoleStore.noteContact(this)
        when {
            title == EventTitles.HEARTBEAT ->
                RoleStore.noteHeartbeat(this, message, RoleStore.lastPowerText(this))
            EventTitles.isPower(title) ->
                RoleStore.notePowerEvent(this, title + "  " + message)
            EventTitles.isReboot(title) ->
                RoleStore.notePowerEvent(this, title)
        }
        offlineNotified = false
        val event = FeedEvent(title, message, timeSec)
        if (EventTitles.isVisibleInLog(title)) EventLogStore.append(this, event)
        StreamBus.events.tryEmit(event)
        if (EventTitles.isVisibleInLog(title)) notifyEvent(title, message)
    }

    private fun checkForSilence() {
        val lastContact = RoleStore.lastHeartbeatAt(this)
        val reference = if (lastContact == 0L) monitoringSince else lastContact
        val silentFor = System.currentTimeMillis() - reference
        if (!offlineNotified && silentFor >= OFFLINE_AFTER_MS) {
            offlineNotified = true
            val message =
                "No contact for more than a minute. This can mean Wi-Fi, internet, or power is unavailable."
            val event = FeedEvent(
                EventTitles.OFFLINE,
                message,
                System.currentTimeMillis() / 1000L
            )
            EventLogStore.append(this, event)
            StreamBus.events.tryEmit(event)
            notifyEvent(EventTitles.OFFLINE, message)
        }
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
            .setContentTitle("Argus is watching home")
            .setContentText("Listening for home alerts")
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
        private const val OFFLINE_AFTER_MS = 90_000L

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
