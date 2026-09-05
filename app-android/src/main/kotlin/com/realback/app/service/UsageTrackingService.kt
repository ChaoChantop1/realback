package com.realback.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.realback.app.R

/**
 * Foreground service stub (M0).
 *
 * Purpose (implemented in M1/M2): periodically poll UsageStatsManager,
 * persist daily aggregates via UsageDao, and feed the intervention engine.
 * Running as a foreground dataSync service is our first line of defence
 * against OEM battery killers (HyperOS / ColorOS / OriginOS).
 */
class UsageTrackingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // M1: schedule periodic UsageStats polls here (WorkManager fallback).
        return START_STICKY
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
            .setSmallIcon(android.R.drawable.ic_menu_view) // replaced by real icon in M1
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "usage_tracking"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, UsageTrackingService::class.java))
        }
    }
}
