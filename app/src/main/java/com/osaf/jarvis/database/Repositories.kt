package com.osaf.jarvis.database

import kotlinx.coroutines.flow.Flow

class MemoryRepository(
    private val noteDao: NoteDao,
    private val reminderDao: ReminderDao
) {
    val notes: Flow<List<NoteEntity>> = noteDao.observeAll()
    val reminders: Flow<List<ReminderEntity>> = reminderDao.observeAll()

    fun searchNotes(query: String): Flow<List<NoteEntity>> {
        return if (query.isBlank()) noteDao.observeAll() else noteDao.search(query.trim())
    }

    suspend fun getNotesSnapshot(): List<NoteEntity> = noteDao.getAllSnapshot()
    suspend fun getRemindersSnapshot(): List<ReminderEntity> = reminderDao.getAllSnapshot()

    suspend fun addNote(title: String, content: String, category: String = "General", language: String = "en"): Long {
        val now = System.currentTimeMillis()
        return noteDao.insert(
            NoteEntity(
                title = title.trim().ifEmpty { "Note" },
                content = content.trim(),
                category = category.trim().ifEmpty { "General" },
                language = language,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateNote(note: NoteEntity) {
        noteDao.update(note.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteNote(id: Long) = noteDao.deleteById(id)

    suspend fun addReminder(title: String, triggerAtMillis: Long): Long {
        return reminderDao.insert(
            ReminderEntity(
                title = title.trim(),
                triggerAtMillis = triggerAtMillis,
                isCompleted = false
            )
        )
    }

    suspend fun toggleReminderCompleted(id: Long, completed: Boolean) {
        reminderDao.setCompleted(id, completed)
    }

    suspend fun deleteReminder(id: Long) = reminderDao.deleteById(id)

    suspend fun clearAllMemories() {
        noteDao.deleteAll()
        reminderDao.deleteAll()
    }
}

class ChatRepository(private val chatDao: ChatDao) {
    val messages: Flow<List<ChatMessageEntity>> = chatDao.observeAll()

    suspend fun addMessage(role: String, content: String, language: String, source: String): Long {
        return chatDao.insert(
            ChatMessageEntity(
                role = role,
                content = content,
                language = language,
                source = source,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun getRecentContext(limit: Int = 12): List<ChatMessageEntity> {
        return chatDao.getRecentMessages(limit).reversed()
    }

    suspend fun deleteMessage(id: Long) = chatDao.deleteById(id)

    suspend fun clearHistory() = chatDao.clearAll()
}
