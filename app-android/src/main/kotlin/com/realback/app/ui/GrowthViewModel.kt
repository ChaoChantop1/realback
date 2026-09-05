package com.realback.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.realback.app.RealBackApp
import com.realback.app.data.db.toDomain
import com.realback.core.growth.AchievementEngine
import com.realback.core.growth.GrowthEngine
import com.realback.core.habit.HabitEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * M4 growth state: level curve over lifetime points, today's focus minutes
 * and the achievement grid. Focus minutes are stored as points (1 per minute).
 */
class GrowthViewModel(app: Application) : AndroidViewModel(app) {

    data class State(
        val loaded: Boolean = false,
        val totalPoints: Double = 0.0,
        val level: Int = 1,
        val levelProgress: Float = 0f,
        val pointsForNextLevel: Double = 0.0,
        val focusMinutesToday: Int = 0,
        val bestStreak: Int = 0,
        val achievements: List<AchievementEngine.Achievement> = emptyList(),
    )

    private val db = (app as RealBackApp).database
    private val todayEpochDay: Long = LocalDate.now().toEpochDay()

    val state: StateFlow<State> = combine(
        db.growthDao().observeTotalPoints(),
        db.growthDao().observePointsForDay(todayEpochDay),
        db.habitDao().observeActive(),
        db.habitDao().observeCheckmarksSince(todayEpochDay - HabitEngine.HISTORY_DAYS + 1),
    ) { total, today, habits, checkmarks ->
        // Focus points are 1/min; checkmark points are 10/each — split them out.
        val focusMinutes = today / GrowthEngine.POINTS_PER_FOCUS_MINUTE
        val growth = GrowthEngine.resolve(total)
        val bestStreak = habits.maxOfOrNull { entity ->
            val entries = checkmarks.filter { it.habitId == entity.id }.map { it.toDomain() }
            HabitEngine.streak(entries, todayEpochDay)
        } ?: 0
        State(
            loaded = true,
            totalPoints = total,
            level = growth.level,
            levelProgress = growth.levelProgress.toFloat(),
            pointsForNextLevel = GrowthEngine.pointsForLevel(growth.level + 1) - total,
            focusMinutesToday = focusMinutes.toInt(),
            bestStreak = bestStreak,
            achievements = AchievementEngine.evaluate(total, bestStreak, focusMinutes.toInt()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /** Awards focus points for the minutes actually focused (>= 0). */
    fun recordFocus(minutes: Int) {
        if (minutes <= 0) return
        viewModelScope.launch {
            db.growthDao().ensureDay(todayEpochDay)
            db.growthDao().addPoints(todayEpochDay, minutes * GrowthEngine.POINTS_PER_FOCUS_MINUTE)
        }
    }
}
