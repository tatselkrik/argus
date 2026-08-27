package com.experiment.argus.sentinel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.experiment.argus.EventTitles
import com.experiment.argus.DeviceMessage
import com.experiment.argus.MainActivity
import com.experiment.argus.Ntfy
import com.experiment.argus.R
import com.experiment.argus.RoleStore
import com.experiment.argus.SentinelBus
import com.experiment.argus.WatchdogTiming
import com.experiment.argus.batterySummary
import com.experiment.argus.isCharging
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Visible watchdog while the user has pressed Start. Dynamic registration avoids
 * Android/OEM suppression of manifest-declared implicit power broadcasts.
 */
class SentinelService : Service() {

    private val executor = Executors.newScheduledThreadPool(2)
    private var heartbeatTask: ScheduledFuture<*>? = null
    private var receiverRegistered = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val powerReceiver = PowerEventReceiver { charging ->
        SentinelBus.charging.value = charging
        getSystemService(NotificationManager::class.java)?.notify(
            SERVICE_ID,
            serviceNotification(charging)
        )
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        registerPowerReceiver()
        registerNetworkListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (RoleStore.role(this) != "sentinel" ||
            !RoleStore.monitoringEnabled(this) ||
            RoleStore.topic(this).isEmpty()
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val charging = isCharging(this)
        SentinelBus.charging.value = charging
        startForeground(SERVICE_ID, serviceNotification(charging))

        executor.execute { sendHeartbeat() }
        if (heartbeatTask?.isCancelled != false) {
            heartbeatTask = executor.scheduleWithFixedDelay(
                { sendHeartbeat() },
                WatchdogTiming.HEARTBEAT_INTERVAL_MINUTES,
                WatchdogTiming.HEARTBEAT_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
        }
        executor.execute { SentinelJobs.flushPendingNow(applicationContext) }
        return START_STICKY
    }

    private fun registerPowerReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            this,
            powerReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    private fun registerNetworkListener() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                executor.execute {
                    SentinelJobs.flushPendingNow(applicationContext)
                    sendHeartbeat()
                }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onSuccess { networkCallback = callback }
    }

    private fun sendHeartbeat() {
        if (RoleStore.role(this) != "sentinel" || !RoleStore.monitoringEnabled(this)) return
        val topic = RoleStore.topic(this)
        if (topic.isEmpty()) return
        Ntfy.send(
            topic,
            EventTitles.HEARTBEAT,
            DeviceMessage.forThisPhone(this, batterySummary(this))
        )
    }

    override fun onDestroy() {
        heartbeatTask?.cancel(true)
        heartbeatTask = null
        if (receiverRegistered) runCatching { unregisterReceiver(powerReceiver) }
        networkCallback?.let { callback ->
            runCatching {
                getSystemService(ConnectivityManager::class.java)
                    ?.unregisterNetworkCallback(callback)
            }
        }
        executor.shutdownNow()
        SentinelBus.charging.value = null
        super.onDestroy()
    }

    private fun serviceNotification(charging: Boolean): Notification =
        NotificationCompat.Builder(this, CH_SENTINEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Argus: ${RoleStore.deviceName(this)}")
            .setContentText(if (charging) "On charger · power monitoring active" else "Not charging · monitoring active")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(mainPendingIntent())
            .build()

    private fun mainPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        private const val SERVICE_ID = 43
        private const val CH_SENTINEL = "argus_sentinel"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SentinelService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SentinelService::class.java))
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(
                    CH_SENTINEL,
                    "Home watchdog",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps power and connectivity monitoring active"
                    setSound(null, null)
                }
            )
        }
    }
}
