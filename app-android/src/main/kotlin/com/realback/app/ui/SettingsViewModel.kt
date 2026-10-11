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
import java.io.ByteArrayOutputStream
import java.io.InputStream

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
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: error("cannot open input stream")
                    readCappedText(stream, MAX_BACKUP_BYTES)
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

    /**
     * Reads a stream into text, refusing anything larger than [maxBytes].
     *
     * The old `readBytes()` trusted the picked document to be small; a hostile
     * or accidentally chosen multi-hundred-MB file was pulled fully into memory
     * and then into a JSONObject, which is an out-of-memory crash on the spot
     * the user cannot undo.
     */
    private fun readCappedText(stream: InputStream, maxBytes: Long): String =
        stream.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read == -1) break
                if (buffer.size() + read > maxBytes) {
                    throw BackupManager.BackupFormatException(
                        "备份文件超过 ${maxBytes / (1024 * 1024)} MB 上限",
                    )
                }
                buffer.write(chunk, 0, read)
            }
            buffer.toString("UTF-8")
        }

    private companion object {
        /** A real backup of this app's data is a few kilobytes; 32 MB is generous. */
        const val MAX_BACKUP_BYTES = 32L * 1024L * 1024L
    }
}
