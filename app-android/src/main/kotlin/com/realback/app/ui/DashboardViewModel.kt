package com.realback.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.realback.app.RealBackApp
import com.realback.app.data.db.toDomain
import com.realback.app.service.UsageTrackingService
import com.realback.app.usage.UsageStatsCollector
import com.realback.core.usage.DashboardStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * M1 dashboard state: today's total screen time, unlock count and top apps.
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
        val loaded: Boolean = false,
        val totalMillis: Long = 0L,
        val totalOpens: Int = 0,
        val topApps: List<AppRow> = emptyList(),
    )

    private val db = (app as RealBackApp).database
    private val collector: UsageStatsCollector = (app as RealBackApp).usageCollector
    private val todayEpochDay: Long = LocalDate.now().toEpochDay()

    private val permissionGranted = MutableStateFlow(false)

    val state: StateFlow<State> = combine(
        db.usageDao().observeForDay(todayEpochDay),
        permissionGranted,
    ) { rows, granted ->
        val usage = rows.map { it.toDomain() }
        val total = DashboardStats.totalMillis(usage)
        State(
            permissionGranted = granted,
            loaded = true,
            totalMillis = total,
            totalOpens = DashboardStats.totalOpens(usage),
            topApps = DashboardStats.topApps(usage, TOP_APP_COUNT).map { day ->
                AppRow(
                    label = appLabel(day.packageName),
                    packageName = day.packageName,
                    millis = day.foregroundMillis,
                    opens = day.openCount,
                    share = DashboardStats.share(day, total).toFloat(),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /** Re-check the special permission and (re)start tracking on every resume. */
    fun onScreenResumed() {
        permissionGranted.value = collector.hasUsageAccess()
        if (permissionGranted.value) {
            UsageTrackingService.start(getApplication())
        }
    }

    private fun appLabel(packageName: String): String = try {
        val pm = getApplication<Application>().packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        packageName
    }

    private companion object {
        const val TOP_APP_COUNT = 5
    }
}
