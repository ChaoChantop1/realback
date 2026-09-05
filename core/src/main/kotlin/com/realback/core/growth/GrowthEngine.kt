package com.realback.core.growth

/**
 * Growth engine: the closed loop connecting usage intervention (①) and
 * habit growth (④). Offline focus time + habit checkmarks => growth points
 * => levels & achievements.
 */
object GrowthEngine {

    /** Growth points earned per minute of offline/focus time. */
    const val POINTS_PER_FOCUS_MINUTE: Double = 1.0

    /** Growth points earned per habit checkmark. */
    const val POINTS_PER_CHECKMARK: Double = 10.0

    data class DailyInput(
        val focusMinutes: Int,
        val habitCheckmarks: Int,
    )

    data class DailyGrowth(
        val points: Double,
        val level: Int,
        /** Progress within the current level, in [0.0, 1.0). */
        val levelProgress: Double,
    )

    fun computeDay(input: DailyInput): Double =
        input.focusMinutes * POINTS_PER_FOCUS_MINUTE + input.habitCheckmarks * POINTS_PER_CHECKMARK

    /**
     * Level curve: each level requires 20% more points than the previous one.
     * Level 1 starts at 0, level 2 at 100 points, level 3 at 220, ...
     */
    fun pointsForLevel(level: Int): Double {
        require(level >= 1) { "level must be >= 1" }
        var threshold = 0.0
        var span = 100.0
        repeat(level - 1) {
            threshold += span
            span *= 1.2
        }
        return threshold
    }

    fun resolve(totalPoints: Double): DailyGrowth {
        var level = 1
        while (totalPoints >= pointsForLevel(level + 1)) level++
        val currentFloor = pointsForLevel(level)
        val nextFloor = pointsForLevel(level + 1)
        val progress = if (nextFloor > currentFloor) {
            (totalPoints - currentFloor) / (nextFloor - currentFloor)
        } else 0.0
        return DailyGrowth(
            points = totalPoints,
            level = level,
            levelProgress = progress.coerceIn(0.0, 1.0),
        )
    }
}
