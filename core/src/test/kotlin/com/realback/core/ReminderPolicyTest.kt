package com.realback.core

import com.realback.core.usage.AppLimitRule
import com.realback.core.usage.AppUsageDay
import com.realback.core.usage.ReminderPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {

    private fun usage(millis: Long) =
        AppUsageDay(packageName = "com.example.feed", epochDay = 400L, foregroundMillis = millis, openCount = 3)

    private val rule = AppLimitRule("com.example.feed", dailyLimitMillis = 1_200_000, interventionEnabled = true)

    @Test
    fun `fires when over budget and not yet reminded`() {
        assertTrue(ReminderPolicy.shouldRemind(usage(1_800_000), rule, alreadyRemindedToday = false))
    }

    @Test
    fun `never repeats within the same day`() {
        assertFalse(ReminderPolicy.shouldRemind(usage(1_800_000), rule, alreadyRemindedToday = true))
    }

    @Test
    fun `does not fire under budget`() {
        assertFalse(ReminderPolicy.shouldRemind(usage(600_000), rule, alreadyRemindedToday = false))
    }

    @Test
    fun `does not fire without a rule`() {
        assertFalse(ReminderPolicy.shouldRemind(usage(1_800_000), null, alreadyRemindedToday = false))
    }
}
