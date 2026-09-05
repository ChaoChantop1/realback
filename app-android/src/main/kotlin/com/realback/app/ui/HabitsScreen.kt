package com.realback.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.realback.core.habit.HabitSchedule

/**
 * M3 habits screen: create habits, check them off, watch streaks grow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(vm: HabitsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("习惯") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新建习惯")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "把省下来的时间，投进想成为的自己。点击卡片完成今日打卡。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            when {
                !state.loaded -> item { Text("加载中…") }
                state.rows.isEmpty() -> item {
                    Text(
                        text = "还没有习惯。点右下角 + 创建第一个吧。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> items(state.rows, key = { it.habitId }) { row ->
                    HabitCard(
                        row = row,
                        onToggle = { vm.toggleToday(row.habitId) },
                    )
                }
            }
            item { Box(modifier = Modifier.size(80.dp)) } // FAB spacing
        }
    }

    if (showCreate) {
        CreateHabitDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, schedule ->
                vm.createHabit(name, schedule)
                showCreate = false
            },
        )
    }
}

@Composable
private fun HabitCard(row: HabitsViewModel.HabitRow, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = row.scheduleLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TodayCheckCircle(checked = row.todayChecked)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "连续 ${row.streak} 天 · 强度 ${row.scorePercent}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (row.weeklyTarget > 0) {
                    Text(
                        text = "本周 ${row.weeklyDone}/${row.weeklyTarget} 次",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (row.weeklyDone >= row.weeklyTarget) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            WeekDots(row.last7Days)
        }
    }
}

@Composable
private fun TodayCheckCircle(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(
                text = "✓",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Trailing 7 days, oldest on the left. */
@Composable
private fun WeekDots(days: List<Boolean>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { done ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (done) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun CreateHabitDialog(
    onDismiss: () -> Unit,
    onCreate: (String, HabitSchedule) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var scheduleType by remember { mutableStateOf(0) } // 0 daily / 1 weekly / 2 interval
    var weeklyTimes by remember { mutableStateOf(3) }
    var intervalDays by remember { mutableStateOf(2) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新习惯") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（如：阅读 30 分钟）") },
                    singleLine = true,
                )
                Text("频率", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("每天", "每周几次", "隔几天").forEachIndexed { i, label ->
                        if (scheduleType == i) {
                            Button(onClick = { scheduleType = i }) { Text(label) }
                        } else {
                            OutlinedButton(onClick = { scheduleType = i }) { Text(label) }
                        }
                    }
                }
                when (scheduleType) {
                    1 -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..5).forEach { n ->
                            if (weeklyTimes == n) {
                                Button(onClick = { weeklyTimes = n }) { Text("$n") }
                            } else {
                                OutlinedButton(onClick = { weeklyTimes = n }) { Text("$n") }
                            }
                        }
                    }
                    2 -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(2, 3, 4, 7).forEach { n ->
                            if (intervalDays == n) {
                                Button(onClick = { intervalDays = n }) { Text("$n") }
                            } else {
                                OutlinedButton(onClick = { intervalDays = n }) { Text("$n") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val schedule = when (scheduleType) {
                        1 -> HabitSchedule.WeeklyTimes(weeklyTimes)
                        2 -> HabitSchedule.IntervalDays(intervalDays)
                        else -> HabitSchedule.Daily
                    }
                    onCreate(name, schedule)
                },
                enabled = name.isNotBlank(),
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
