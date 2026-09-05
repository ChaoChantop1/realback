package com.realback.core.habit

/**
 * Habit strength scoring.
 *
 * PLACEHOLDER ALGORITHM: a simple exponentially-weighted completion rate.
 * In M3 this will be replaced by the battle-tested score from uhabits-core
 * (GPL-3.0, https://github.com/iSoron/uhabits) — the public API of this
 * object is designed to survive that swap.
 */
object HabitScore {

    /** Half-life of a repetition's contribution, in days. */
    private const val HALF_LIFE_DAYS = 14.0

    /**
     * @param checkmarks entries ordered oldest-first, one per day, most recent last.
     * @return strength in [0.0, 1.0]
     */
    fun compute(checkmarks: List<Checkmark>): Double {
        if (checkmarks.isEmpty()) return 0.0
        val lastDay = checkmarks.last().epochDay
        var numerator = 0.0
        var denominator = 0.0
        for (entry in checkmarks) {
            val ageDays = (lastDay - entry.epochDay).toDouble()
            // Exponential decay: recent checkmarks matter more; old misses fade out.
            val weight = Math.pow(0.5, ageDays / HALF_LIFE_DAYS)
            denominator += weight
            if (entry.value) numerator += weight
        }
        return if (denominator == 0.0) 0.0 else (numerator / denominator).coerceIn(0.0, 1.0)
    }

    /**
     * Current streak of consecutive completions ending at the latest entry.
     * A streak is "resilient": a couple of missed days in a long streak only
     * reset the counter, never the score (philosophy borrowed from Loop).
     */
    fun currentStreak(checkmarks: List<Checkmark>): Int {
        if (checkmarks.isEmpty()) return 0
        var streak = 0
        for (entry in checkmarks.asReversed()) {
            if (entry.value) streak++ else break
        }
        return streak
    }
}
