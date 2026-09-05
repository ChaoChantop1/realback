package com.realback.core

import com.realback.core.habit.Checkmark
import com.realback.core.habit.HabitScore
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitScoreTest {

    private fun entries(days: List<Boolean>, startDay: Long = 100L): List<Checkmark> =
        days.mapIndexed { i, v -> Checkmark(habitId = 1L, epochDay = startDay + i, value = v) }

    @Test
    fun `empty history yields zero score`() {
        assertEquals(0.0, HabitScore.compute(emptyList()), 1e-9)
    }

    @Test
    fun `all completed yields perfect score`() {
        val history = entries(List(30) { true })
        assertEquals(1.0, HabitScore.compute(history), 1e-9)
    }

    @Test
    fun `recent activity outweighs old activity`() {
        val recentGood = entries(List(20) { false } + List(10) { true })
        val recentBad = entries(List(20) { true } + List(10) { false })
        val scoreGood = HabitScore.compute(recentGood)
        val scoreBad = HabitScore.compute(recentBad)
        assert(scoreGood > scoreBad) { "expected $scoreGood > $scoreBad" }
    }

    @Test
    fun `score is resilient to a few misses`() {
        val perfect = HabitScore.compute(entries(List(60) { true }))
        val history = List(60) { true }.toMutableList()
        history[3] = false
        history[7] = false
        val withMisses = HabitScore.compute(entries(history))
        // Two misses in 60 days should barely dent the score.
        assert(perfect - withMisses < 0.15) { "drop too large: ${perfect - withMisses}" }
    }

    @Test
    fun `streak counts trailing completions only`() {
        assertEquals(3, HabitScore.currentStreak(entries(listOf(true, false, true, true, true))))
        assertEquals(1, HabitScore.currentStreak(entries(listOf(true, true, true, false, true))))
        assertEquals(0, HabitScore.currentStreak(entries(listOf(true, true, false))))
        assertEquals(0, HabitScore.currentStreak(emptyList()))
    }
}
