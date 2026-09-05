package com.realback.core

import com.realback.core.growth.GrowthEngine
import com.realback.core.usage.AppLimitRule
import com.realback.core.usage.AppUsageDay
import com.realback.core.usage.InterventionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthEngineTest {

    @Test
    fun `day points combine focus minutes and checkmarks`() {
        val points = GrowthEngine.computeDay(GrowthEngine.DailyInput(focusMinutes = 60, habitCheckmarks = 3))
        assertEquals(60 * 1.0 + 3 * 10.0, points, 1e-9)
    }

    @Test
    fun `level thresholds grow geometrically`() {
        assertEquals(0.0, GrowthEngine.pointsForLevel(1), 1e-9)
        assertEquals(100.0, GrowthEngine.pointsForLevel(2), 1e-9)
        assertEquals(220.0, GrowthEngine.pointsForLevel(3), 1e-9)
    }

    @Test
    fun `resolve maps points to level and progress`() {
        val g = GrowthEngine.resolve(160.0)
        assertEquals(2, g.level)
        assertEquals(0.6, g.levelProgress, 1e-9)

        val fresh = GrowthEngine.resolve(0.0)
        assertEquals(1, fresh.level)
        assertEquals(0.0, fresh.levelProgress, 1e-9)
    }
}

class InterventionEngineTest {

    private fun usage(millis: Long) =
        AppUsageDay(packageName = "com.example.feed", epochDay = 200L, foregroundMillis = millis, openCount = 5)

    @Test
    fun `no rule means no intervention`() {
        val d = InterventionEngine.evaluate(usage(1_800_000), null)
        assertFalse(d.exceeded)
    }

    @Test
    fun `over budget with intervention enabled triggers decision`() {
        val rule = AppLimitRule("com.example.feed", dailyLimitMillis = 1_200_000, interventionEnabled = true)
        val d = InterventionEngine.evaluate(usage(1_800_000), rule)
        assertTrue(d.exceeded)
        assertEquals(1_800_000L, d.usedMillis)
    }

    @Test
    fun `over budget but intervention disabled never triggers`() {
        val rule = AppLimitRule("com.example.feed", dailyLimitMillis = 1_200_000, interventionEnabled = false)
        assertFalse(InterventionEngine.evaluate(usage(1_800_000), rule).exceeded)
    }

    @Test
    fun `evaluateAll maps rules by package name`() {
        val today = listOf(usage(2_000_000))
        val rules = mapOf("com.example.feed" to AppLimitRule("com.example.feed", 600_000, true))
        val decisions = InterventionEngine.evaluateAll(today, rules)
        assertEquals(1, decisions.size)
        assertTrue(decisions.first().exceeded)
    }
}
