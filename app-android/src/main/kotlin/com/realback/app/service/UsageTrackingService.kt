package com.realback.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.realback.app.R
import com.realback.app.RealBackApp
import com.realback.app.data.db.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Foreground service that periodically aggregates app usage into Room.
 *
 * The foreground dataSync service plus START_STICKY is our first line of
 * defence against OEM battery killers (HyperOS / ColorOS / OriginOS).
 * Poll interval is 15 minutes; each poll REPLACEs today's aggregates,
 * so a killed-and-restarted service self-heals with the next poll.
 */
class UsageTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground()
        scope.launch { pollLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service re-created after process death: the poll loop is already
        // running from onCreate; nothing extra to do per-start.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun pollLoop() {
        val app = application as RealBackApp
        val dao = app.database.usageDao()
        val collector = app.usageCollector
        while (scope.isActive) {
            if (collector.hasUsageAccess()) {
                val today = LocalDate.now().toEpochDay()
                val rows = collector.collectEpochDay(today)
                if (rows.isNotEmpty()) {
                    dao.upsertAll(rows.map { it.toEntity() })
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.usage_tracking_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.usage_tracking_title))
            .setSmallIcon(android.R.drawable.ic_menu_view) // replaced by real icon in M1.1
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "usage_tracking"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MS = 15L * 60L * 1000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, UsageTrackingService::class.java))
        }
    }
}
