package com.realback.core

import com.realback.core.usage.AppUsageDay
import com.realback.core.usage.DashboardStats
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardStatsTest {

    private fun day(vararg pairs: Pair<String, Long>) =
        pairs.map { (pkg, mins) ->
            AppUsageDay(
                packageName = pkg,
                epochDay = 300L,
                foregroundMillis = mins * 60_000L,
                openCount = (mins / 10).toInt(),
            )
        }

    @Test
    fun `totals sum across apps`() {
        val usage = day("a" to 30L, "b" to 90L, "c" to 60L)
        assertEquals(180 * 60_000L, DashboardStats.totalMillis(usage))
        assertEquals((3 + 9 + 6), DashboardStats.totalOpens(usage))
    }

    @Test
    fun `empty day yields zeros`() {
        assertEquals(0L, DashboardStats.totalMillis(emptyList()))
        assertEquals(0, DashboardStats.totalOpens(emptyList()))
        assertEquals(emptyList<AppUsageDay>(), DashboardStats.topApps(emptyList(), 5))
    }

    @Test
    fun `top apps sorted descending and capped`() {
        val usage = day("a" to 30L, "b" to 90L, "c" to 60L, "d" to 10L)
        val top = DashboardStats.topApps(usage, 2)
        assertEquals(listOf("b", "c"), top.map { it.packageName })
    }

    @Test
    fun `share is clamped and zero-safe`() {
        val usage = day("a" to 50L, "b" to 50L)
        assertEquals(0.5, DashboardStats.share(usage[0], DashboardStats.totalMillis(usage)), 1e-9)
        assertEquals(0.0, DashboardStats.share(usage[0], 0L), 1e-9)
        // More than total (corrupted data) must clamp to 1.0, not exceed it.
        assertEquals(1.0, DashboardStats.share(usage[0], 100L * 60_000 / 2), 1e-9)
    }
}
