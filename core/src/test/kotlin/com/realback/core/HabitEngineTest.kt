package com.realback.core

import com.realback.core.habit.Checkmark
import com.realback.core.habit.Habit
import com.realback.core.habit.HabitEngine
import com.realback.core.habit.HabitSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitEngineTest {

    private fun entry(day: Long, value: Boolean = true) =
        Checkmark(habitId = 1L, epochDay = day, value = value)

    @Test
    fun `week start is Monday`() {
        // 2026-09-05 (epoch day 20701) is a Saturday -> Monday = 20696.
        assertEquals(20696L, HabitEngine.weekStartEpochDay(20701L))
        // A Monday maps to itself.
        assertEquals(20696L, HabitEngine.weekStartEpochDay(20696L))
    }

    @Test
    fun `dense values fill missing days as unchecked`() {
        val entries = listOf(entry(100L), entry(103L, false))
        val dense = HabitEngine.denseValues(entries, 100L, 103L)
        assertEquals(listOf(true, false, false, false), dense.map { it.value })
    }

    @Test
    fun `streak counts back from today and stops at first miss`() {
        val today = 500L
        // Sparse: hits on 498..500, gap on 497.
        val entries = listOf(entry(498L), entry(499L), entry(500L))
        assertEquals(3, HabitEngine.streak(entries, today))

        // Yesterday missing (sparse) must break the streak.
        val entries2 = listOf(entry(499L))
        assertEquals(0, HabitEngine.streak(entries2, today))
    }

    @Test
    fun `daily habit is due every day`() {
        val habit = Habit(name = "阅读", schedule = HabitSchedule.Daily, createdAtEpochDay = 100L)
        assertTrue(HabitEngine.dueToday(habit, 100L))
        assertTrue(HabitEngine.dueToday(habit, 999L))
    }

    @Test
    fun `interval habit is due every N days from creation`() {
        val habit = Habit(name = "浇花", schedule = HabitSchedule.IntervalDays(2), createdAtEpochDay = 100L)
        assertTrue(HabitEngine.dueToday(habit, 100L))
        assertFalse(HabitEngine.dueToday(habit, 101L))
        assertTrue(HabitEngine.dueToday(habit, 102L))
        // Before creation: not due.
        assertFalse(HabitEngine.dueToday(habit, 99L))
    }

    @Test
    fun `weekly done counts completions since Monday`() {
        // Today Saturday 20701; week starts Monday 20696.
        val entries = listOf(
            entry(20696L), // Monday, done
            entry(20697L, false), // explicitly undone
            entry(20699L), // Wednesday, done
            entry(20695L), // previous Sunday, must not count
        )
        assertEquals(2, HabitEngine.weeklyDone(entries, 20701L))
    }

    @Test
    fun `recent values return trailing window`() {
        val today = 100L
        val entries = listOf(entry(99L), entry(100L))
        assertEquals(listOf(false, true, true), HabitEngine.recentValues(entries, today, days = 3))
    }

    @Test
    fun `score over sparse data matches dense computation`() {
        val today = 200L
        val entries = (150L..200L).map { entry(it) }
        val expected = HabitEngine.score(entries, today)
        assertTrue(expected in 0.0..1.0)
        // Perfect 51-day recent history -> very strong score.
        assertTrue(expected > 0.99)
    }
}
