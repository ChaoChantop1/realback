@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.realback.app.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.realback.app.RealBackApp
import com.realback.app.data.db.toDomain
import com.realback.app.util.todayEpochDay
import com.realback.app.util.todayEpochDayFlow
import com.realback.core.growth.AchievementEngine
import com.realback.core.growth.GrowthEngine
import com.realback.core.habit.HabitEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * M4/M5 growth state: level curve, achievements and the focus session.
 *
 * M5 hardening: the focus session is timestamp-driven (elapsedRealtime at
 * start), so the timer keeps running while the app is backgrounded and
 * points are settled from wall-clock facts, never from tick counts.
 *
 * "Today" is never cached at construction time — see [todayEpochDayFlow]: the
 * app is routinely left open across midnight, and a frozen date would bank
 * focus points and read the dashboard against yesterday.
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

    /** A running focus session; null when idle. */
    data class FocusSession(
        val startElapsed: Long,
        val plannedMinutes: Int,
        /**
         * Wall-clock day the session started on. Points are banked there, not
         * on the day the timer happens to reach zero: a session started at
         * 23:50 is a 23:50 session even when it finishes after midnight.
         */
        val startEpochDay: Long,
    )

    private val db = (app as RealBackApp).database

    private val _session = MutableStateFlow<FocusSession?>(null)
    val session: StateFlow<FocusSession?> = _session

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(1_000)
        }
    }

    /** Remaining whole seconds of the session; -1 when idle. */
    val remainingSec: StateFlow<Int> = _session
        .combine(ticker) { s, _ -> s }
        .map { s ->
            if (s == null) {
                -1
            } else {
                val elapsedSec = (SystemClock.elapsedRealtime() - s.startElapsed) / 1000L
                (s.plannedMinutes * 60L - elapsedSec).toInt()
            }
        }
        .onEach { remaining ->
            if (remaining == 0 && _session.value != null) {
                settleSession()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

    val state: StateFlow<State> = todayEpochDayFlow()
        .flatMapLatest { today ->
            combine(
                db.growthDao().observeTotalPoints(),
                db.growthDao().observePointsForDay(today),
                db.habitDao().observeActive(),
                db.habitDao().observeCheckmarksSince(today - HabitEngine.HISTORY_DAYS + 1),
            ) { total, dayPoints, habits, checkmarks ->
                // The day row merges focus points (1/min) with checkmark points
                // (10 each). Undoing that merge is mandatory: dividing the
                // merged total by the focus rate counted every check-in as 10
                // minutes of focus and unlocked the focus achievements.
                val checkedToday = checkmarks.count { it.epochDay == today && it.value }
                val focusMinutes = GrowthEngine.focusMinutesToday(dayPoints, checkedToday)
                val growth = GrowthEngine.resolve(total)
                val bestStreak = habits.maxOfOrNull { entity ->
                    val habit = entity.toDomain()
                    val entries = checkmarks.filter { it.habitId == entity.id }.map { it.toDomain() }
                    HabitEngine.streak(habit, entries, today)
                } ?: 0
                State(
                    loaded = true,
                    totalPoints = total,
                    level = growth.level,
                    levelProgress = growth.levelProgress.toFloat(),
                    pointsForNextLevel = GrowthEngine.pointsForLevel(growth.level + 1) - total,
                    focusMinutesToday = focusMinutes,
                    bestStreak = bestStreak,
                    achievements = AchievementEngine.evaluate(total, bestStreak, focusMinutes),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun startFocus(minutes: Int) {
        if (_session.value != null) return
        _session.value = FocusSession(
            startElapsed = SystemClock.elapsedRealtime(),
            plannedMinutes = minutes,
            startEpochDay = todayEpochDay(),
        )
    }

    /** Ends the session early and banks the minutes actually focused. */
    fun abandonFocus() {
        settleSession()
    }

    private fun settleSession() {
        val s = _session.value ?: return
        _session.value = null
        val elapsedSec = (SystemClock.elapsedRealtime() - s.startElapsed) / 1000L
        val minutes = ((elapsedSec + 59) / 60L).toInt().coerceAtMost(s.plannedMinutes)
        if (minutes > 0) {
            viewModelScope.launch {
                val day = s.startEpochDay
                db.growthDao().ensureDay(day)
                db.growthDao().addPoints(day, minutes * GrowthEngine.POINTS_PER_FOCUS_MINUTE)
            }
        }
    }
}
