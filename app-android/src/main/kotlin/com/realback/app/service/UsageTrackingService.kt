package com.realback.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.realback.app.R
import com.realback.app.RealBackApp
import com.realback.app.data.db.toDomain
import com.realback.app.data.db.toEntity
import com.realback.app.intervention.InterventionNotifier
import com.realback.app.util.appLabel
import com.realback.core.usage.InterventionEngine
import com.realback.core.usage.ReminderPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Foreground service that periodically aggregates app usage into Room and
 * evaluates the user's limit rules.
 *
 * Defences against OEM battery killers (HyperOS / ColorOS / OriginOS):
 * foreground dataSync service + START_STICKY + battery-optimization
 * whitelist guidance in the dashboard UI.
 *
 * Poll cadence: 5 minutes. Each poll REPLACEs today's aggregates, so a
 * killed-and-restarted service self-heals with the next poll; interventions
 * fire at most once per app per day (core ReminderPolicy). That date is
 * persisted, so a process restart cannot re-remind the same day.
 */
class UsageTrackingService : Service() {

    /**
     * Safety net for anything that escapes [pollLoop]'s own guarding: without a
     * handler, an uncaught exception in this scope reaches the thread's default
     * handler and kills the whole process — and START_STICKY would resurrect the
     * service straight into the same crash. Declared before [scope], which
     * consumes it during initialisation.
     */
    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "usage poll loop died; tracking continues on next start", throwable)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + serviceExceptionHandler)

    /**
     * Last reminded epoch day per package, persisted across process death.
     * An in-memory map here would silently break the "one reminder per app per
     * day" promise every time the OEM kills and restarts the service, turning a
     * nudge into spam.
     */
    private val remindedPrefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

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
        while (scope.isActive) {
            try {
                pollOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // UsageStatsManager and the OEM permission screens behind it are a
                // known source of SecurityException / IllegalStateException on
                // custom ROMs. One bad poll must cost one cycle, not the user's
                // background tracking: the whole body of the cycle is inside this
                // guard, including the object lookups, so nothing here can end
                // the loop. (If it could, the service would look alive while
                // silently collecting nothing — START_STICKY never restarts a
                // process that is still running.)
                Log.e(TAG, "poll cycle failed; retrying next interval", e)
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    /** Collects today's usage and fires any due reminders. May throw. */
    private suspend fun pollOnce() {
        val app = application as RealBackApp
        val db = app.database
        val collector = app.usageCollector
        val notifier = InterventionNotifier(this)

        if (!collector.hasUsageAccess()) return

        val today = LocalDate.now().toEpochDay()
        val rows = collector.collectEpochDay(today)
        if (rows.isNotEmpty()) {
            db.usageDao().upsertAll(rows.map { it.toEntity() })
        }

        val rules = db.limitRuleDao().all()
            .map { it.toDomain() }
            .associateBy { it.packageName }
        val decisions = InterventionEngine.evaluateAll(rows, rules)
        for (decision in decisions) {
            val alreadyReminded = remindedPrefs.getLong(decision.packageName, -1L) == today
            if (ReminderPolicy.shouldRemindByDecision(decision, alreadyReminded)) {
                notifier.remind(decision, appLabel(this, decision.packageName))
                remindedPrefs.edit().putLong(decision.packageName, today).apply()
            }
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
        private const val TAG = "UsageTrackingService"
        private const val CHANNEL_ID = "usage_tracking"
        private const val NOTIFICATION_ID = 1
        private const val PREFS_NAME = "usage_reminders"
        private const val POLL_INTERVAL_MS = 5L * 60L * 1000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, UsageTrackingService::class.java))
        }
    }
}
