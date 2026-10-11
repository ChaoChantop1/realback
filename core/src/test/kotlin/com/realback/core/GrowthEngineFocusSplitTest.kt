package com.realback.core

import com.realback.core.growth.GrowthEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The `growth_points` table stores focus and checkmark points merged into one
 * number per day. These tests pin the inverse operation that the focus screen
 * and the focus achievements depend on.
 */
class GrowthEngineFocusSplitTest {

    @Test
    fun `checkmark points are not read back as focus minutes`() {
        // A day with 3 check-ins (30 points) and no focus session at all.
        assertEquals(0, GrowthEngine.focusMinutesToday(dayPoints = 30.0, checkmarksToday = 3))
    }

    @Test
    fun `focus minutes survive when checkmarks are mixed in`() {
        // 25 minutes of focus (25 points) plus 2 check-ins (20 points).
        assertEquals(25, GrowthEngine.focusMinutesToday(dayPoints = 45.0, checkmarksToday = 2))
    }

    @Test
    fun `pure focus day is unchanged`() {
        assertEquals(40, GrowthEngine.focusMinutesToday(dayPoints = 40.0, checkmarksToday = 0))
    }

    @Test
    fun `round trip through computeDay recovers the focus minutes`() {
        val input = GrowthEngine.DailyInput(focusMinutes = 17, habitCheckmarks = 4)
        val merged = GrowthEngine.computeDay(input)
        // 17 focus points + 4 x 10 checkmark points, merged into one total.
        assertEquals(57.0, merged, 0.0)
        assertEquals(17, GrowthEngine.focusMinutesToday(merged, input.habitCheckmarks))
    }

    @Test
    fun `negative remainder is clamped instead of going negative`() {
        // Points restored from an older backup can be smaller than what the
        // checkmark count alone would account for.
        assertEquals(0, GrowthEngine.focusMinutesToday(dayPoints = 5.0, checkmarksToday = 3))
    }
}
