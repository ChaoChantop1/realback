package com.realback.core.usage

/**
 * Decides whether an intervention reminder should fire for a given app today.
 *
 * Design rule: one reminder per app per day — nagging destroys trust
 * (a recurring complaint in blocker apps' issue trackers).
 */
object ReminderPolicy {

    fun shouldRemind(
        usage: AppUsageDay,
        rule: AppLimitRule?,
        alreadyRemindedToday: Boolean,
    ): Boolean {
        if (alreadyRemindedToday) return false
        return InterventionEngine.evaluate(usage, rule).exceeded
    }

    /** Overload for pre-evaluated decisions (used by the tracking service). */
    fun shouldRemindByDecision(
        decision: InterventionDecision,
        alreadyRemindedToday: Boolean,
    ): Boolean = !alreadyRemindedToday && decision.exceeded
}
