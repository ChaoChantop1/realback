package com.realback.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * M0 placeholder dashboard. M1 replaces the body with real usage data
 * (UsageStatsManager via UsageTrackingService) and the top-apps ranking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("回真 RealBack") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "M0 骨架已就绪",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "M1 将在这里显示：屏幕时间仪表盘、应用排行、习惯打卡",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
