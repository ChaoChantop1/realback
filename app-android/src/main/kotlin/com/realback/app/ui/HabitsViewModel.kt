@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.realback.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.realback.app.RealBackApp
import com.realback.app.data.db.CheckmarkEntity
import com.realback.app.data.db.toDomain
import com.realback.app.data.db.toEntity
import com.realback.app.util.todayEpochDay
import com.realback.app.util.todayEpochDayFlow
import com.realback.core.growth.GrowthEngine
import com.realback.core.habit.Habit
import com.realback.core.habit.HabitEngine
import com.realback.core.habit.HabitSchedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * M3 habits list: create habits, toggle today's checkmark, per-habit stats.
 *
 * Every statistic is computed against a live "today" ([todayEpochDayFlow]) so
 * the list re-rolls over at midnight instead of freezing on the day the screen
 * was first opened.
 */
class HabitsViewModel(app: Application) : AndroidViewModel(app) {

    data class HabitRow(
        val habitId: Long,
        val name: String,
        val scheduleLabel: String,
        val todayChecked: Boolean,
        val streak: Int,
        val scorePercent: Int,
        val last7Days: List<Boolean>,
        val weeklyTarget: Int,
        val weeklyDone: Int,
    )

    data class State(
        val loaded: Boolean = false,
        val rows: List<HabitRow> = emptyList(),
    )

    private val db = (app as RealBackApp).database

    val state: StateFlow<State> = todayEpochDayFlow()
        .flatMapLatest { today ->
            combine(
                db.habitDao().observeActive(),
                db.habitDao().observeCheckmarksSince(today - HabitEngine.HISTORY_DAYS + 1),
            ) { habits, checkmarks ->
                State(
                    loaded = true,
                    rows = habits.map { entity ->
                        val habit = entity.toDomain()
                        val entries = checkmarks.filter { it.habitId == entity.id }.map { it.toDomain() }
                        HabitRow(
                            habitId = entity.id,
                            name = habit.name,
                            scheduleLabel = scheduleLabel(habit),
                            todayChecked = entries.any { it.epochDay == today && it.value },
                            streak = HabitEngine.streak(habit, entries, today),
                            scorePercent = (HabitEngine.score(habit, entries, today) * 100).toInt(),
                            last7Days = HabitEngine.recentValues(entries, today, days = 7),
                            weeklyTarget = (habit.schedule as? HabitSchedule.WeeklyTimes)?.timesPerWeek ?: 0,
                            weeklyDone = HabitEngine.weeklyDone(entries, today),
                        )
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun toggleToday(habitId: Long) {
        viewModelScope.launch {
            // Evaluated per tap: a check-in at 00:05 belongs to today, not to
            // the day this screen happened to be opened on.
            val today = todayEpochDay()
            // Checkmark, growth day row and the points delta must move as one
            // unit — a crash between them used to desync the checkmark count
            // from the growth total, which also skews the derived focus time.
            db.withTransaction {
                val existing = db.habitDao()
                    .checkmarksFor(habitId, today)
                    .firstOrNull { it.epochDay == today }
                val nowChecked = !(existing?.value ?: false)
                db.habitDao().upsertCheckmark(
                    CheckmarkEntity(
                        habitId = habitId,
                        epochDay = today,
                        value = nowChecked,
                    ),
                )
                // Growth loop (M4): a checkmark is worth ±10 points, reversible.
                db.growthDao().ensureDay(today)
                db.growthDao().addPoints(
                    today,
                    if (nowChecked) {
                        GrowthEngine.POINTS_PER_CHECKMARK
                    } else {
                        -GrowthEngine.POINTS_PER_CHECKMARK
                    },
                )
            }
        }
    }

    fun createHabit(name: String, schedule: HabitSchedule) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val habit = Habit(
                name = name.trim(),
                schedule = schedule,
                createdAtEpochDay = todayEpochDay(),
            )
            db.habitDao().upsert(habit.toEntity())
        }
    }

    fun archiveHabit(habitId: Long) {
        viewModelScope.launch { db.habitDao().archive(habitId) }
    }

    private fun scheduleLabel(habit: Habit): String = when (val s = habit.schedule) {
        is HabitSchedule.Daily -> "每天"
        is HabitSchedule.WeeklyTimes -> "每周 ${s.timesPerWeek} 次"
        is HabitSchedule.IntervalDays -> "每 ${s.days} 天一次"
    }
}
