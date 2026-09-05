package com.realback.core.habit

/**
 * Habit engine: turns sparse checkmark records into dense daily history
 * and derives per-habit statistics.
 *
 * Storage is sparse (only days the user interacted with have rows), so every
 * statistic first materialises a dense [from, to] window where missing days
 * count as unchecked.
 */
object HabitEngine {

    /** Score/streak look-back window. */
    const val HISTORY_DAYS: Int = 180

    /**
     * Epoch day of the Monday of the week containing [epochDay]
     * (1970-01-01 was a Thursday).
     */
    fun weekStartEpochDay(epochDay: Long): Long {
        val dayOfWeek = ((epochDay % 7L) + 7L) % 7L // 0 = Thursday
        return epochDay - ((dayOfWeek + 3L) % 7L)
    }

    /** Dense completion values for [from, to] (inclusive), oldest first. */
    fun denseValues(entries: List<Checkmark>, from: Long, to: Long): List<Checkmark> {
        if (to < from) return emptyList()
        val byDay = HashMap<Long, Boolean>(entries.size)
        for (e in entries) byDay[e.epochDay] = e.value
        return ((from)..to).map { day ->
            Checkmark(habitId = entries.firstOrNull()?.habitId ?: 0L, epochDay = day, value = byDay[day] ?: false)
        }
    }

    /** Whether the habit is scheduled for the given day. */
    fun dueToday(habit: Habit, epochDay: Long): Boolean = when (val s = habit.schedule) {
        is HabitSchedule.Daily -> true
        is HabitSchedule.IntervalDays -> {
            val delta = epochDay - habit.createdAtEpochDay
            delta >= 0L && (delta % s.days) == 0L
        }
        is HabitSchedule.WeeklyTimes -> true // not a per-day schedule; track weekly progress
    }

    /** Consecutive completed days ending at [todayEpochDay] (dense). */
    fun streak(entries: List<Checkmark>, todayEpochDay: Long): Int {
        val from = todayEpochDay - HISTORY_DAYS + 1
        val dense = denseValues(entries, from, todayEpochDay)
        return HabitScore.currentStreak(dense)
    }

    /**
     * Habit strength in [0.0, 1.0]. The dense window starts at the earliest
     * recorded entry (the habit's lifetime) — days before the habit existed
     * must not count as misses, or new habits would be punished.
     */
    fun score(entries: List<Checkmark>, todayEpochDay: Long): Double {
        if (entries.isEmpty()) return 0.0
        val from = entries.minOf { it.epochDay }
        val dense = denseValues(entries, from, todayEpochDay)
        return HabitScore.compute(dense)
    }

    /** Completions since the Monday of the current week (inclusive of today). */
    fun weeklyDone(entries: List<Checkmark>, todayEpochDay: Long): Int {
        val from = weekStartEpochDay(todayEpochDay)
        return entries.count { it.epochDay in from..todayEpochDay && it.value }
    }

    /** Completion values for the trailing [days] days, oldest first (for UI dots). */
    fun recentValues(entries: List<Checkmark>, todayEpochDay: Long, days: Int): List<Boolean> =
        denseValues(entries, todayEpochDay - days + 1, todayEpochDay).map { it.value }
}
