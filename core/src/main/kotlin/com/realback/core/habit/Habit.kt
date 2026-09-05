package com.realback.core.habit

/**
 * A habit the user wants to build.
 *
 * Pure domain model — no Android types allowed in this module (see core/build.gradle.kts).
 * (kotlinx.serialization will be introduced together with the JSON export format in M5.)
 */
data class Habit(
    val id: Long = 0L,
    val name: String,
    val description: String = "",
    val schedule: HabitSchedule = HabitSchedule.Daily,
    val archived: Boolean = false,
    val createdAtEpochDay: Long,
)

/**
 * Flexible scheduling, inspired by (and to be replaced by) uhabits-core in M3:
 * - Daily: every day
 * - WeeklyTimes: N times per week (e.g. gym 3x/week)
 * - IntervalDays: every N days (e.g. water plants every 2 days)
 */
sealed interface HabitSchedule {
    data object Daily : HabitSchedule
    data class WeeklyTimes(val timesPerWeek: Int) : HabitSchedule
    data class IntervalDays(val days: Int) : HabitSchedule
}

/** One checkmark entry for a habit on a given epoch day. */
data class Checkmark(
    val habitId: Long,
    val epochDay: Long,
    val value: Boolean,
)
