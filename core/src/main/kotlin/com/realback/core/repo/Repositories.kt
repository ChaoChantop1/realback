package com.realback.core.repo

import com.realback.core.growth.GrowthEngine
import com.realback.core.habit.Checkmark
import com.realback.core.habit.Habit
import com.realback.core.usage.AppLimitRule
import com.realback.core.usage.AppUsageDay

/**
 * Repository contracts implemented by the platform layer (app-android, Room).
 * The core module depends only on these interfaces — never on Room itself.
 */

interface HabitRepository {
    suspend fun listActive(): List<Habit>
    suspend fun upsert(habit: Habit): Long
    suspend fun archive(habitId: Long)
    suspend fun checkmarksFor(habitId: Long, sinceEpochDay: Long): List<Checkmark>
    suspend fun toggleCheckmark(habitId: Long, epochDay: Long)
}

interface UsageRepository {
    suspend fun usageForDay(epochDay: Long): List<AppUsageDay>
    suspend fun usageRange(fromEpochDay: Long, toEpochDay: Long): List<AppUsageDay>
    suspend fun recordUsage(usage: AppUsageDay)
}

interface LimitRuleRepository {
    suspend fun allRules(): Map<String, AppLimitRule>
    suspend fun upsert(rule: AppLimitRule)
    suspend fun remove(packageName: String)
}

interface GrowthRepository {
    /** Total growth points accumulated so far. */
    suspend fun totalPoints(): Double

    /** Points earned on a specific day. */
    suspend fun pointsForDay(epochDay: Long): Double

    /** Persist today's computed points (idempotent per day). */
    suspend fun recordDayPoints(epochDay: Long, points: Double)

    suspend fun snapshot(): GrowthEngine.DailyGrowth
}
