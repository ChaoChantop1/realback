@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.realback.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.realback.app.RealBackApp
import com.realback.app.data.db.toDomain
import com.realback.app.service.UsageTrackingService
import com.realback.app.system.BatteryOptimizationHelper
import com.realback.app.usage.UsageStatsCollector
import com.realback.app.util.appLabel
import com.realback.app.util.todayEpochDayFlow
import com.realback.core.usage.DashboardStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * M1/M2 dashboard state: today's totals, top apps and system-health flags.
 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    data class AppRow(
        val label: String,
        val packageName: String,
        val millis: Long,
        val opens: Int,
        val share: Float,
    )

    data class State(
        val permissionGranted: Boolean = false,
        val batteryOptimizationIgnored: Boolean = false,
        val loaded: Boolean = false,
        val totalMillis: Long = 0L,
        val totalOpens: Int = 0,
        val topApps: List<AppRow> = emptyList(),
    )

    private val db = (app as RealBackApp).database
    private val collector: UsageStatsCollector = (app as RealBackApp).usageCollector

    private val permissionGranted = MutableStateFlow(false)
    private val batteryOptimizationIgnored = MutableStateFlow(false)

    val state: StateFlow<State> = todayEpochDayFlow()
        .flatMapLatest { today ->
            combine(
                db.usageDao().observeForDay(today),
                permissionGranted,
                batteryOptimizationIgnored,
            ) { rows, granted, battery ->
                val usage = rows.map { it.toDomain() }
                val total = DashboardStats.totalMillis(usage)
                State(
                    permissionGranted = granted,
                    batteryOptimizationIgnored = battery,
                    loaded = true,
                    totalMillis = total,
                    totalOpens = DashboardStats.totalOpens(usage),
                    topApps = DashboardStats.topApps(usage, TOP_APP_COUNT).map { day ->
                        AppRow(
                            label = appLabel(getApplication(), day.packageName),
                            packageName = day.packageName,
                            millis = day.foregroundMillis,
                            opens = day.openCount,
                            share = DashboardStats.share(day, total).toFloat(),
                        )
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /** Re-check system permissions and (re)start tracking on every resume. */
    fun onScreenResumed() {
        val app = getApplication<Application>()
        permissionGranted.value = collector.hasUsageAccess()
        batteryOptimizationIgnored.value = BatteryOptimizationHelper.isIgnoring(app)
        if (permissionGranted.value) {
            UsageTrackingService.start(app)
        }
    }

    private companion object {
        const val TOP_APP_COUNT = 5
    }
}
