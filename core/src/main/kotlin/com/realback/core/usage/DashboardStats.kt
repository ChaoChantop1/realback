package com.realback.core.usage

/**
 * Pure-Kotlin dashboard statistics over daily app usage.
 * Kept in core so the aggregation rules are unit-tested and reusable
 * (e.g. for the iOS client later via KMP).
 */
object DashboardStats {

    /** Total foreground time across all apps for the given day(s). */
    fun totalMillis(days: List<AppUsageDay>): Long = days.sumOf { it.foregroundMillis }

    /** Total app opens (unlocks into foreground) for the given day(s). */
    fun totalOpens(days: List<AppUsageDay>): Int = days.sumOf { it.openCount }

    /** Top N apps by foreground time, descending. */
    fun topApps(days: List<AppUsageDay>, count: Int): List<AppUsageDay> =
        days.sortedByDescending { it.foregroundMillis }.take(count)

    /** Share of a single app relative to the total, in [0.0, 1.0]. */
    fun share(app: AppUsageDay, totalMillis: Long): Double =
        if (totalMillis > 0L) {
            (app.foregroundMillis.toDouble() / totalMillis).coerceIn(0.0, 1.0)
        } else 0.0
}
