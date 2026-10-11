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

    /**
     * Dense completion values for [from, to] (inclusive), oldest first.
     *
     * Calendar-dense: every day in the range appears, missing days as unchecked.
     * Correct for daily habits; schedule-aware statistics use [dueDenseValues]
     * so that days a habit was never scheduled for do not read as misses.
     */
    fun denseValues(entries: List<Checkmark>, from: Long, to: Long): List<Checkmark> {
        if (to < from) return emptyList()
        val byDay = HashMap<Long, Boolean>(entries.size)
        for (e in entries) byDay[e.epochDay] = e.value
        return ((from)..to).map { day ->
            Checkmark(habitId = entries.firstOrNull()?.habitId ?: 0L, epochDay = day, value = byDay[day] ?: false)
        }
    }

    /**
     * Dense completion values restricted to the days [habit] is actually
     * scheduled for. For [HabitSchedule.Daily] this is identical to
     * [denseValues]; for an [HabitSchedule.IntervalDays] habit the non-due
     * days are dropped instead of being materialised as misses.
     */
    fun dueDenseValues(habit: Habit, entries: List<Checkmark>, from: Long, to: Long): List<Checkmark> {
        if (to < from) return emptyList()
        val byDay = HashMap<Long, Boolean>(entries.size)
        for (e in entries) byDay[e.epochDay] = e.value
        val habitId = entries.firstOrNull()?.habitId ?: 0L
        return (from..to)
            .filter { dueToday(habit, it) }
            .map { day -> Checkmark(habitId = habitId, epochDay = day, value = byDay[day] ?: false) }
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

    /**
     * Consecutive scheduled days completed, counting back from today.
     *
     * Only days the habit is due for are considered: an every-2-days habit done
     * on every one of its scheduled days has a real streak, whereas counting
     * over the raw calendar would break it at every non-due day and cap such
     * habits at a streak of 1.
     */
    fun streak(habit: Habit, entries: List<Checkmark>, todayEpochDay: Long): Int {
        val from = todayEpochDay - HISTORY_DAYS + 1
        val dense = dueDenseValues(habit, entries, from, todayEpochDay)
        return HabitScore.currentStreak(dense)
    }

    /**
     * Habit strength in [0.0, 1.0]. The dense window starts at the earliest
     * recorded entry (the habit's lifetime) — days before the habit existed
     * must not count as misses, or new habits would be punished. Days the
     * habit is not scheduled for are likewise skipped, not scored as misses.
     */
    fun score(habit: Habit, entries: List<Checkmark>, todayEpochDay: Long): Double {
        if (entries.isEmpty()) return 0.0
        val from = entries.minOf { it.epochDay }
        val dense = dueDenseValues(habit, entries, from, todayEpochDay)
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
