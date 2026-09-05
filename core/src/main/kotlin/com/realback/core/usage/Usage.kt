package com.realback.core.usage

/** Aggregated usage of a single app for a single epoch day. */
data class AppUsageDay(
    val packageName: String,
    val epochDay: Long,
    /** Total foreground time in milliseconds. */
    val foregroundMillis: Long,
    /** Number of times the app was opened to the foreground. */
    val openCount: Int,
)

/** An app-level intervention rule configured by the user. */
data class AppLimitRule(
    val packageName: String,
    /** Daily time budget in milliseconds; 0 disables the rule. */
    val dailyLimitMillis: Long = 0L,
    /** Whether a breathing confirmation is shown when the budget is exceeded. */
    val interventionEnabled: Boolean = true,
)

/** Result of evaluating today's usage against the rules. */
data class InterventionDecision(
    val packageName: String,
    val exceeded: Boolean,
    val usedMillis: Long,
    val limitMillis: Long,
)

/**
 * Rule engine deciding when to trigger the gentle (non-blocking) intervention.
 *
 * DESIGN RULE (from project plan §4, pitfall #1): reminder-style intervention
 * only — NO hard blocking, NO content-level filtering.
 */
object InterventionEngine {

    fun evaluate(usage: AppUsageDay, rule: AppLimitRule?): InterventionDecision {
        val limit = rule?.dailyLimitMillis ?: 0L
        val exceeded = limit > 0L && usage.foregroundMillis > limit && (rule?.interventionEnabled ?: false)
        return InterventionDecision(
            packageName = usage.packageName,
            exceeded = exceeded,
            usedMillis = usage.foregroundMillis,
            limitMillis = limit,
        )
    }

    fun evaluateAll(usageToday: List<AppUsageDay>, rules: Map<String, AppLimitRule>): List<InterventionDecision> =
        usageToday.map { evaluate(it, rules[it.packageName]) }
}
