package com.realback.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.realback.app.util.formatDurationMillis

/**
 * M2 limit settings screen: pick a daily budget per app.
 * Reminder-style intervention only — never a hard block (design red line #3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LimitsScreen(vm: LimitsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<LimitsViewModel.LimitRow?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("应用限额") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "为容易刷过头的应用设定每日限额。超过后回真会提醒你，但不会强制锁定。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                !state.loaded -> Text("加载中…")
                state.rows.isEmpty() -> Text(
                    "今天还没有应用使用数据，用过手机后再来看看。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> state.rows.forEach { row ->
                    LimitRowCard(row, onClick = { editing = row })
                }
            }
        }
    }

    editing?.let { row ->
        SetLimitDialog(
            row = row,
            onDismiss = { editing = null },
            onConfirm = { minutes ->
                vm.setLimit(row.packageName, minutes)
                editing = null
            },
            onClear = {
                vm.setLimit(row.packageName, 0)
                editing = null
            },
        )
    }
}

@Composable
private fun LimitRowCard(row: LimitsViewModel.LimitRow, onClick: () -> Unit) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = if (row.limitMillis > 0L) {
                        if (row.exceeded) "已超限" else "限额 ${formatDurationMillis(row.limitMillis)}"
                    } else {
                        "未设置限额"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (row.exceeded) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = "今日已用 ${formatDurationMillis(row.todayMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (row.limitMillis > 0L) {
                LinearProgressIndicator(
                    progress = { row.progress },
                    color = if (row.exceeded) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SetLimitDialog(
    row: LimitsViewModel.LimitRow,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("「${row.label}」每日限额") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "今日已用 ${formatDurationMillis(row.todayMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val presets = listOf(15, 30, 45, 60, 90, 120, 180, 240)
                presets.chunked(4).forEach { chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        chunk.forEach { minutes ->
                            TextButton(onClick = { onConfirm(minutes) }) {
                                Text("${minutes}分")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row {
                if (row.limitMillis > 0L) {
                    TextButton(onClick = onClear) { Text("清除限额") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
