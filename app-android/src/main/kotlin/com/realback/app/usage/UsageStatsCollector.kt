package com.realback.app.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.realback.core.usage.AppUsageDay
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads per-app daily foreground usage from the system UsageStatsManager.
 *
 * M1 scope: app-level aggregates only (foreground time + open counts).
 * No content-level tracking — see design red lines in README.
 */
class UsageStatsCollector(private val context: Context) {

    private val usageStatsManager: UsageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** PACKAGE_USAGE_STATS is an AppOps-gated special permission, not a runtime one. */
    @Suppress("DEPRECATION")
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Collect the usage aggregate of one epoch day (system timezone).
     * Returns one [AppUsageDay] per package with non-zero foreground time.
     */
    fun collectEpochDay(epochDay: Long): List<AppUsageDay> {
        val zone: ZoneId = ZoneId.systemDefault()
        val startMillis = LocalDate.ofEpochDay(epochDay).atStartOfDay(zone)
            .toInstant().toEpochMilli()
        val endMillis = startMillis + MILLIS_PER_DAY

        val openCounts = countOpens(startMillis, endMillis)
        val daily = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startMillis,
            endMillis,
        ) ?: return emptyList()

        return daily
            .filter { it.totalTimeInForeground > 0L }
            .map { stats ->
                AppUsageDay(
                    packageName = stats.packageName,
                    epochDay = epochDay,
                    foregroundMillis = stats.totalTimeInForeground,
                    openCount = openCounts[stats.packageName] ?: 0,
                )
            }
    }

    /** Counts app-into-foreground transitions per package within the window. */
    @Suppress("DEPRECATION")
    private fun countOpens(startMillis: Long, endMillis: Long): Map<String, Int> {
        val counts = HashMap<String, Int>()
        val events = usageStatsManager.queryEvents(startMillis, endMillis)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                counts.merge(event.packageName, 1, Int::plus)
            }
        }
        return counts
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
