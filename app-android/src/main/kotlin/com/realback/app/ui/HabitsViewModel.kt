package com.realback.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.realback.app.RealBackApp
import com.realback.app.data.db.CheckmarkEntity
import com.realback.app.data.db.toDomain
import com.realback.app.data.db.toEntity
import com.realback.core.habit.Habit
import com.realback.core.habit.HabitEngine
import com.realback.core.habit.HabitSchedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * M3 habits list: create habits, toggle today's checkmark, per-habit stats.
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
    private val todayEpochDay: Long = LocalDate.now().toEpochDay()

    val state: StateFlow<State> = combine(
        db.habitDao().observeActive(),
        db.habitDao().observeCheckmarksSince(todayEpochDay - HabitEngine.HISTORY_DAYS + 1),
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
                    todayChecked = entries.any { it.epochDay == todayEpochDay && it.value },
                    streak = HabitEngine.streak(entries, todayEpochDay),
                    scorePercent = (HabitEngine.score(entries, todayEpochDay) * 100).toInt(),
                    last7Days = HabitEngine.recentValues(entries, todayEpochDay, days = 7),
                    weeklyTarget = (habit.schedule as? HabitSchedule.WeeklyTimes)?.timesPerWeek ?: 0,
                    weeklyDone = HabitEngine.weeklyDone(entries, todayEpochDay),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun toggleToday(habitId: Long) {
        viewModelScope.launch {
            val existing = db.habitDao()
                .checkmarksFor(habitId, todayEpochDay)
                .firstOrNull { it.epochDay == todayEpochDay }
            db.habitDao().upsertCheckmark(
                CheckmarkEntity(
                    habitId = habitId,
                    epochDay = todayEpochDay,
                    value = !(existing?.value ?: false),
                ),
            )
        }
    }

    fun createHabit(name: String, schedule: HabitSchedule) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val habit = Habit(
                name = name.trim(),
                schedule = schedule,
                createdAtEpochDay = todayEpochDay,
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
