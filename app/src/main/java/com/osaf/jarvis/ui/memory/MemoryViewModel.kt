package com.osaf.jarvis.ui.memory

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osaf.jarvis.JarvisApplication
import com.osaf.jarvis.database.NoteEntity
import com.osaf.jarvis.database.ReminderEntity
import com.osaf.jarvis.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as JarvisApplication).container
    private val memoryRepo = container.memoryRepository
    private val chatRepo = container.chatRepository
    private val backupManager = container.backupManager

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val notes: StateFlow<List<NoteEntity>> = _searchQuery
        .flatMapLatest { q -> memoryRepo.searchNotes(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = memoryRepo.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    fun updateSearch(q: String) {
        _searchQuery.value = q
    }

    fun addNote(title: String, content: String, category: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            memoryRepo.addNote(title, content, category)
            _statusBanner.value = "Note saved to encrypted-device local storage."
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch { memoryRepo.deleteNote(id) }
    }

    fun addReminder(title: String, minutesFromNow: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val triggerAt = System.currentTimeMillis() + (minutesFromNow.coerceAtLeast(1) * 60_000L)
            val id = memoryRepo.addReminder(title, triggerAt)
            NotificationHelper.scheduleLocalReminder(getApplication(), id, title, triggerAt)
            _statusBanner.value = "Reminder scheduled in $minutesFromNow minute(s)."
        }
    }

    fun toggleReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            memoryRepo.toggleReminderCompleted(reminder.id, !reminder.isCompleted)
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch { memoryRepo.deleteReminder(id) }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            chatRepo.clearHistory()
            _statusBanner.value = "Conversation history cleared."
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryRepo.clearAllMemories()
            _statusBanner.value = "All local notes and reminders cleared."
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = backupManager.exportToJson()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
                _statusBanner.value = "Backup exported successfully (API keys excluded for security)."
            } catch (e: Exception) {
                _statusBanner.value = "Export failed: ${e.localizedMessage ?: "error"}"
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    }.orEmpty()
                }
                val result = backupManager.importFromJson(json)
                result.onSuccess { summary ->
                    _statusBanner.value =
                        "Imported ${summary.notesImported} notes and ${summary.remindersImported} reminders."
                }.onFailure { err ->
                    _statusBanner.value = "Import failed: ${err.localizedMessage ?: "invalid file"}"
                }
            } catch (e: Exception) {
                _statusBanner.value = "Import error: ${e.localizedMessage ?: "error"}"
            }
        }
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }
}
