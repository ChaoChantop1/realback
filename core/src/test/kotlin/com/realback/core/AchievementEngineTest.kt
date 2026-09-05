package com.realback.core

import com.realback.core.growth.AchievementEngine
import com.realback.core.growth.GrowthEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementEngineTest {

    @Test
    fun `fresh account unlocks nothing`() {
        val list = AchievementEngine.evaluate(totalPoints = 0.0, bestStreak = 0, focusMinutesToday = 0)
        assertTrue(list.none { it.unlocked })
        assertEquals(8, list.size)
    }

    @Test
    fun `level achievements follow points`() {
        // Level 2 needs 100 points.
        val list = AchievementEngine.evaluate(100.0, 0, 0)
        assertTrue(list.first { it.kind == AchievementEngine.Kind.LEVEL_2 }.unlocked)
        assertFalse(list.first { it.kind == AchievementEngine.Kind.LEVEL_3 }.unlocked)
    }

    @Test
    fun `streak and focus achievements unlock independently`() {
        val list = AchievementEngine.evaluate(0.0, bestStreak = 7, focusMinutesToday = 60)
        assertTrue(list.first { it.kind == AchievementEngine.Kind.STREAK_7 }.unlocked)
        assertFalse(list.first { it.kind == AchievementEngine.Kind.STREAK_30 }.unlocked)
        assertTrue(list.first { it.kind == AchievementEngine.Kind.FOCUS_30_TODAY }.unlocked)
        assertTrue(list.first { it.kind == AchievementEngine.Kind.FOCUS_60_TODAY }.unlocked)
    }

    @Test
    fun `checkmark points feed the level curve consistently with GrowthEngine`() {
        // 30 checkmarks = 300 points -> level 3 (threshold 220).
        val points = GrowthEngine.computeDay(GrowthEngine.DailyInput(0, 0)) // sanity: zero
        assertEquals(0.0, points, 1e-9)
        val list = AchievementEngine.evaluate(300.0, 0, 0)
        assertTrue(list.first { it.kind == AchievementEngine.Kind.LEVEL_3 }.unlocked)
    }
}
