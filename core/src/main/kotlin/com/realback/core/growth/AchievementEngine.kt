package com.realback.core.growth

/**
 * M4 achievements: derived on the fly from live stats — nothing extra is
 * persisted, so achievements can never drift out of sync with the data.
 */
object AchievementEngine {

    enum class Kind {
        LEVEL_2,
        LEVEL_3,
        LEVEL_5,
        LEVEL_10,
        STREAK_7,
        STREAK_30,
        FOCUS_30_TODAY,
        FOCUS_60_TODAY,
    }

    data class Achievement(
        val kind: Kind,
        val title: String,
        val description: String,
        val unlocked: Boolean,
    )

    /**
     * @param totalPoints lifetime growth points (focus minutes + checkmarks).
     * @param bestStreak best current streak across all habits.
     * @param focusMinutesToday focus minutes recorded today.
     */
    fun evaluate(totalPoints: Double, bestStreak: Int, focusMinutesToday: Int): List<Achievement> {
        val level = GrowthEngine.resolve(totalPoints).level
        return listOf(
            Achievement(Kind.LEVEL_2, "破茧", "成长等级达到 2 级", level >= 2),
            Achievement(Kind.LEVEL_3, "拔节", "成长等级达到 3 级", level >= 3),
            Achievement(Kind.LEVEL_5, "成林", "成长等级达到 5 级", level >= 5),
            Achievement(Kind.LEVEL_10, "参天", "成长等级达到 10 级", level >= 10),
            Achievement(Kind.STREAK_7, "七日之约", "任意习惯连续坚持 7 天", bestStreak >= 7),
            Achievement(Kind.STREAK_30, "月有恒", "任意习惯连续坚持 30 天", bestStreak >= 30),
            Achievement(Kind.FOCUS_30_TODAY, "今日专注·30", "今天累计专注 30 分钟", focusMinutesToday >= 30),
            Achievement(Kind.FOCUS_60_TODAY, "今日专注·60", "今天累计专注 60 分钟", focusMinutesToday >= 60),
        )
    }
}
