package com.realback.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.realback.core.growth.AchievementEngine
import kotlinx.coroutines.delay

/**
 * M4 focus & growth screen: focus timer feeding the growth loop.
 *
 * Focus is a timer, not a lock (design red line #3): leaving the app or
 * using the phone does not cancel it — the minutes still count when the
 * session completes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(vm: GrowthViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var running by remember { mutableStateOf(false) }
    var remainingSec by remember { mutableIntStateOf(0) }
    var plannedMinutes by remember { mutableIntStateOf(25) }

    /** Minutes actually focused in this session (elapsed = planned - remaining). */
    fun elapsedMinutes(): Int = (plannedMinutes * 60 - remainingSec + 59) / 60

    // One-second ticker; survives recomposition, pauses with the app process
    // (accepted for MVP; a chronometer-based service is the M5 hardening item).
    LaunchedEffect(running) {
        while (running) {
            delay(1_000)
            remainingSec -= 1
            if (remainingSec <= 0) {
                remainingSec = 0
                vm.recordFocus(elapsedMinutes())
                running = false
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("专注 · 成长") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GrowthCard(state)
            FocusTimerCard(
                running = running,
                remainingSec = remainingSec,
                plannedMinutes = plannedMinutes,
                focusMinutesToday = state.focusMinutesToday,
                onSelectPreset = { if (!running) plannedMinutes = it },
                onStart = {
                    remainingSec = plannedMinutes * 60
                    running = true
                },
                onAbandon = {
                    running = false
                    val elapsed = elapsedMinutes()
                    if (elapsed > 0) vm.recordFocus(elapsed)
                    remainingSec = 0
                },
            )
            AchievementsCard(state.achievements)
        }
    }
}

@Composable
private fun GrowthCard(state: GrowthViewModel.State) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Lv.${state.level}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "今日专注 ${state.focusMinutesToday} 分钟",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "累计 ${state.totalPoints.toInt()} 成长值",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { state.levelProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "再获得 ${state.pointsForNextLevel.toInt()} 成长值升到 Lv.${state.level + 1}（打卡 +10，专注每分钟 +1）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FocusTimerCard(
    running: Boolean,
    remainingSec: Int,
    plannedMinutes: Int,
    focusMinutesToday: Int,
    onSelectPreset: (Int) -> Unit,
    onStart: () -> Unit,
    onAbandon: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (running) {
                    String.format("%02d:%02d", remainingSec / 60, remainingSec % 60)
                } else {
                    String.format("%02d:00", plannedMinutes)
                },
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
            )
            if (!running) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 45, 60).forEach { preset ->
                        if (plannedMinutes == preset) {
                            Button(onClick = { onSelectPreset(preset) }) { Text("${preset}分") }
                        } else {
                            OutlinedButton(onClick = { onSelectPreset(preset) }) { Text("${preset}分") }
                        }
                    }
                }
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("开始专注")
                }
            } else {
                OutlinedButton(onClick = onAbandon, modifier = Modifier.fillMaxWidth()) {
                    Text("结束并结算")
                }
            }
            Text(
                text = "专注不是锁机：这段时间里你依然自由，但每一分钟都会成为你的成长值。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AchievementsCard(achievements: List<AchievementEngine.Achievement>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("成就", style = MaterialTheme.typography.titleMedium)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(achievements, key = { it.kind }) { achievement ->
                    AchievementTile(achievement)
                }
            }
        }
    }
}

@Composable
private fun AchievementTile(achievement: AchievementEngine.Achievement) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (achievement.unlocked) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    if (achievement.unlocked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    CircleShape,
                ),
        )
        Column {
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = achievement.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
