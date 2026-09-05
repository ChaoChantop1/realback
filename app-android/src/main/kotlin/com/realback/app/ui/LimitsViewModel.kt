package com.realback.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.realback.app.RealBackApp
import com.realback.app.data.db.toDomain
import com.realback.app.data.db.toEntity
import com.realback.app.util.appLabel
import com.realback.core.usage.AppLimitRule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * M2 limit settings: today's app usage joined with the user's limit rules.
 */
class LimitsViewModel(app: Application) : AndroidViewModel(app) {

    data class LimitRow(
        val label: String,
        val packageName: String,
        val todayMillis: Long,
        val limitMillis: Long,
    ) {
        val exceeded: Boolean get() = limitMillis > 0L && todayMillis > limitMillis
        val progress: Float
            get() = if (limitMillis > 0L) {
                (todayMillis.toFloat() / limitMillis).coerceIn(0f, 1f)
            } else 0f
    }

    data class State(
        val loaded: Boolean = false,
        val rows: List<LimitRow> = emptyList(),
    )

    private val db = (app as RealBackApp).database
    private val todayEpochDay: Long = LocalDate.now().toEpochDay()

    val state: StateFlow<State> = combine(
        db.usageDao().observeForDay(todayEpochDay),
        db.limitRuleDao().observeAll(),
    ) { usage, rules ->
        val ruleMap = rules.associateBy { it.packageName }
        State(
            loaded = true,
            rows = usage
                .filter { it.foregroundMillis > 0L }
                .sortedByDescending { it.foregroundMillis }
                .map { entity ->
                    LimitRow(
                        label = appLabel(getApplication(), entity.packageName),
                        packageName = entity.packageName,
                        todayMillis = entity.foregroundMillis,
                        limitMillis = ruleMap[entity.packageName]?.dailyLimitMillis ?: 0L,
                    )
                },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /** minutes <= 0 clears the rule. */
    fun setLimit(packageName: String, minutes: Int) {
        viewModelScope.launch {
            if (minutes <= 0) {
                db.limitRuleDao().remove(packageName)
            } else {
                val rule = AppLimitRule(
                    packageName = packageName,
                    dailyLimitMillis = minutes * 60_000L,
                    interventionEnabled = true,
                )
                db.limitRuleDao().upsert(rule.toEntity())
            }
        }
    }
}
