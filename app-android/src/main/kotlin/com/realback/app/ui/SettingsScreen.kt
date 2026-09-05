package com.realback.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * M5 settings screen: data backup (export/import) and about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { confirmImportUri = it } }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(
                when (it) {
                    SettingsViewModel.Message.Exported -> "已导出备份文件"
                    SettingsViewModel.Message.Imported -> "导入完成，数据已恢复"
                    is SettingsViewModel.Message.Failed -> it.reason
                },
            )
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("数据备份", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "把习惯、打卡记录、限额和成长值导出为 JSON 文件保存；换机或重装后可一键恢复。用量明细不包含在内（可由系统重新统计）。导入会替换当前全部数据。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { exportLauncher.launch("realback-backup.json") }) {
                        Text("导出备份")
                    }
                    OutlinedButton(onClick = {
                        importLauncher.launch(arrayOf("application/json"))
                    }) {
                        Text("从备份恢复")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("关于", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "回真 RealBack v0.1.0\n让手机回归工具，让人回归现实。\nGPL-3.0 开源 · 数据仅存于本机",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    confirmImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImportUri = null },
            title = { Text("恢复备份？") },
            text = { Text("当前的习惯、打卡、限额与成长数据将被备份文件中的内容整体替换，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.importFrom(uri)
                    confirmImportUri = null
                }) { Text("替换并恢复") }
            },
            dismissButton = {
                TextButton(onClick = { confirmImportUri = null }) { Text("取消") }
            },
        )
    }
}
