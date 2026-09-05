package com.realback.app.intervention

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.realback.app.R
import com.realback.app.util.formatDurationMillis
import com.realback.core.usage.InterventionDecision

/**
 * Posts the reminder-style intervention notification (one per app per day,
 * see core ReminderPolicy). The full-screen intent opens the breathing
 * confirmation page when the screen is off/locked; otherwise it lands in
 * the notification tray.
 *
 * Design red line: reminder only — we never block the app itself.
 */
class InterventionNotifier(private val context: Context) {

    fun remind(decision: InterventionDecision, appLabel: String) {
        createChannel()
        val intent = Intent(context, BreathingActivity::class.java).apply {
            putExtra(BreathingActivity.EXTRA_LABEL, appLabel)
            putExtra(BreathingActivity.EXTRA_USED_MILLIS, decision.usedMillis)
            putExtra(BreathingActivity.EXTRA_LIMIT_MILLIS, decision.limitMillis)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context,
            decision.packageName.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.intervention_title))
            .setContentText(
                context.getString(
                    R.string.intervention_text_exceeded,
                    appLabel,
                    formatDurationMillis(decision.usedMillis),
                    formatDurationMillis(decision.limitMillis),
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setFullScreenIntent(fullScreenPending, true)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify(decision.packageName.hashCode(), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — user declined; respect that.
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.intervention_channel),
                NotificationManager.IMPORTANCE_HIGH,
            )
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private companion object {
        const val CHANNEL_ID = "intervention"
    }
}
