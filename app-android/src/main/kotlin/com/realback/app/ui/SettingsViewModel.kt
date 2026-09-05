package com.realback.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.realback.app.RealBackApp
import com.realback.app.data.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M5 settings: backup export/import via SAF documents.
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface Message {
        data object Exported : Message
        data object Imported : Message
        data class Failed(val reason: String) : Message
    }

    private val db = (app as RealBackApp).database

    private val _message = MutableStateFlow<Message?>(null)
    val message: StateFlow<Message?> = _message

    fun consumeMessage() {
        _message.value = null
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { BackupManager.export(db) }
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("cannot open output stream")
                }
                _message.value = Message.Exported
            } catch (e: Exception) {
                _message.value = Message.Failed("导出失败：${e.message}")
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("cannot open input stream")
                }
                withContext(Dispatchers.IO) { BackupManager.import(db, json) }
                _message.value = Message.Imported
            } catch (e: BackupManager.BackupFormatException) {
                _message.value = Message.Failed("文件格式不受支持：${e.message}")
            } catch (e: Exception) {
                _message.value = Message.Failed("导入失败：${e.message}")
            }
        }
    }
}
