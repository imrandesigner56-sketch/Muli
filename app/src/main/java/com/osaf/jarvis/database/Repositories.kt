package com.osaf.jarvis.database

import kotlinx.coroutines.flow.Flow

class MemoryRepository(
    private val noteDao: NoteDao,
    private val reminderDao: ReminderDao,
    private val memoryFactDao: MemoryFactDao,
    private val commandStatDao: CommandStatDao
) {
    val notes: Flow<List<NoteEntity>> = noteDao.observeAll()
    val reminders: Flow<List<ReminderEntity>> = reminderDao.observeAll()
    val memoryFacts: Flow<List<MemoryFactEntity>> = memoryFactDao.observeAll()
    val topCommands: Flow<List<CommandStatEntity>> = commandStatDao.observeTop(6)

    fun searchNotes(query: String): Flow<List<NoteEntity>> {
        return if (query.isBlank()) noteDao.observeAll() else noteDao.search(query.trim())
    }

    suspend fun getNotesSnapshot(): List<NoteEntity> = noteDao.getAllSnapshot()
    suspend fun getRemindersSnapshot(): List<ReminderEntity> = reminderDao.getAllSnapshot()
    suspend fun getFactsSnapshot(): List<MemoryFactEntity> = memoryFactDao.getAllSnapshot()

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

    suspend fun upsertMemoryFact(
        category: String,
        factKey: String,
        factValue: String,
        importance: Int = 2
    ): Long {
        val cleanKey = factKey.trim()
        val cleanVal = factValue.trim()
        if (cleanKey.isEmpty() || cleanVal.isEmpty()) return -1L

        val now = System.currentTimeMillis()
        val existing = memoryFactDao.findByKey(cleanKey)
        return if (existing != null) {
            memoryFactDao.update(
                existing.copy(
                    category = category.trim().ifEmpty { existing.category },
                    factValue = cleanVal,
                    importance = maxOf(existing.importance, importance),
                    updatedAt = now
                )
            )
            existing.id
        } else {
            memoryFactDao.insert(
                MemoryFactEntity(
                    category = category.trim().ifEmpty { "Preference" },
                    factKey = cleanKey,
                    factValue = cleanVal,
                    importance = importance,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun updateMemoryFact(fact: MemoryFactEntity) {
        memoryFactDao.update(fact.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteMemoryFact(id: Long) = memoryFactDao.deleteById(id)

    suspend fun recordCommandUsage(commandKey: String, samplePhrase: String) {
        if (commandKey.isBlank()) return
        val existing = commandStatDao.getByKey(commandKey)
        val now = System.currentTimeMillis()
        commandStatDao.upsert(
            CommandStatEntity(
                commandKey = commandKey,
                samplePhrase = samplePhrase.take(64),
                usageCount = (existing?.usageCount ?: 0) + 1,
                lastUsedAt = now
            )
        )
    }

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
        memoryFactDao.deleteAll()
        commandStatDao.deleteAll()
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
