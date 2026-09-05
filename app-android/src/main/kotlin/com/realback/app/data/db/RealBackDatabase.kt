package com.realback.app.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.realback.core.habit.Checkmark
import com.realback.core.habit.Habit
import com.realback.core.habit.HabitSchedule
import com.realback.core.usage.AppLimitRule
import com.realback.core.usage.AppUsageDay
import kotlinx.coroutines.flow.Flow

/**
 * Room entities mirror the core domain models. Mapping between entity and
 * domain object happens here so the core module stays persistence-agnostic.
 */

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String,
    /** Discriminator: DAILY / WEEKLY_TIMES / INTERVAL_DAYS (see HabitSchedule). */
    val scheduleType: String,
    val scheduleParam: Int,
    val archived: Boolean,
    val createdAtEpochDay: Long,
)

@Entity(tableName = "checkmarks", primaryKeys = ["habitId", "epochDay"])
data class CheckmarkEntity(
    val habitId: Long,
    val epochDay: Long,
    val value: Boolean,
)

@Entity(tableName = "app_usage_day", primaryKeys = ["packageName", "epochDay"])
data class AppUsageDayEntity(
    val packageName: String,
    val epochDay: Long,
    val foregroundMillis: Long,
    val openCount: Int,
)

@Entity(tableName = "app_limit_rules")
data class AppLimitRuleEntity(
    @PrimaryKey val packageName: String,
    val dailyLimitMillis: Long,
    val interventionEnabled: Boolean,
)

@Entity(tableName = "growth_points")
data class GrowthPointsEntity(
    @PrimaryKey val epochDay: Long,
    val points: Double,
)

fun HabitEntity.toDomain() = Habit(
    id = id,
    name = name,
    description = description,
    schedule = when (scheduleType) {
        "WEEKLY_TIMES" -> HabitSchedule.WeeklyTimes(scheduleParam)
        "INTERVAL_DAYS" -> HabitSchedule.IntervalDays(scheduleParam)
        else -> HabitSchedule.Daily
    },
    archived = archived,
    createdAtEpochDay = createdAtEpochDay,
)

fun Habit.toEntity() = HabitEntity(
    id = id,
    name = name,
    description = description,
    scheduleType = when (schedule) {
        is HabitSchedule.Daily -> "DAILY"
        is HabitSchedule.WeeklyTimes -> "WEEKLY_TIMES"
        is HabitSchedule.IntervalDays -> "INTERVAL_DAYS"
    },
    scheduleParam = when (val s = schedule) {
        is HabitSchedule.Daily -> 7
        is HabitSchedule.WeeklyTimes -> s.timesPerWeek
        is HabitSchedule.IntervalDays -> s.days
    },
    archived = archived,
    createdAtEpochDay = createdAtEpochDay,
)

fun AppUsageDayEntity.toDomain() = AppUsageDay(packageName, epochDay, foregroundMillis, openCount)
fun AppUsageDay.toEntity() = AppUsageDayEntity(packageName, epochDay, foregroundMillis, openCount)
fun AppLimitRuleEntity.toDomain() = AppLimitRule(packageName, dailyLimitMillis, interventionEnabled)
fun AppLimitRule.toEntity() = AppLimitRuleEntity(packageName, dailyLimitMillis, interventionEnabled)
fun CheckmarkEntity.toDomain() = Checkmark(habitId, epochDay, value)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY createdAtEpochDay ASC")
    fun observeActive(): Flow<List<HabitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(habit: HabitEntity): Long

    @Query("UPDATE habits SET archived = 1 WHERE id = :habitId")
    suspend fun archive(habitId: Long)

    @Query("SELECT * FROM checkmarks WHERE habitId = :habitId AND epochDay >= :sinceEpochDay ORDER BY epochDay ASC")
    suspend fun checkmarksFor(habitId: Long, sinceEpochDay: Long): List<CheckmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckmark(entry: CheckmarkEntity)
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM app_usage_day WHERE epochDay = :epochDay")
    suspend fun usageForDay(epochDay: Long): List<AppUsageDayEntity>

    @Query("SELECT * FROM app_usage_day WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    suspend fun usageRange(from: Long, to: Long): List<AppUsageDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(usage: AppUsageDayEntity)
}

@Dao
interface LimitRuleDao {
    @Query("SELECT * FROM app_limit_rules")
    suspend fun all(): List<AppLimitRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AppLimitRuleEntity)

    @Query("DELETE FROM app_limit_rules WHERE packageName = :packageName")
    suspend fun remove(packageName: String)
}

@Dao
interface GrowthDao {
    @Query("SELECT COALESCE(SUM(points), 0) FROM growth_points")
    suspend fun totalPoints(): Double

    @Query("SELECT COALESCE(points, 0) FROM growth_points WHERE epochDay = :epochDay")
    suspend fun pointsForDay(epochDay: Long): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordDayPoints(epochDay: Long, points: Double)
}

@Database(
    entities = [
        HabitEntity::class,
        CheckmarkEntity::class,
        AppUsageDayEntity::class,
        AppLimitRuleEntity::class,
        GrowthPointsEntity::class,
    ],
    version = 1,
    // Versioned export schema — M5 relies on it for migrations and JSON export.
    exportSchema = true,
)
abstract class RealBackDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun usageDao(): UsageDao
    abstract fun limitRuleDao(): LimitRuleDao
    abstract fun growthDao(): GrowthDao
}
