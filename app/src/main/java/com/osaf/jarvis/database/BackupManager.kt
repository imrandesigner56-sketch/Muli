package com.osaf.jarvis.database

import com.osaf.jarvis.utils.LanguageMode
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

data class BackupImportSummary(
    val notesImported: Int,
    val remindersImported: Int
)

class BackupManager(
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun exportToJson(): String {
        val notes = memoryRepository.getNotesSnapshot()
        val reminders = memoryRepository.getRemindersSnapshot()
        val settings = settingsRepository.settingsFlow.first()

        val root = JSONObject()
        root.put("format", "osaf_jarvis_backup_v1")
        root.put("exportedAt", System.currentTimeMillis())

        // Export non-secret preferences only (NEVER export API key!)
        val prefsObj = JSONObject().apply {
            put("assistantName", settings.assistantName)
            put("userDisplayName", settings.userDisplayName)
            put("preferredLanguage", settings.preferredLanguage.code)
            put("speechSpeed", settings.speechSpeed.toDouble())
            put("speechPitch", settings.speechPitch.toDouble())
            put("ttsAutoSpeak", settings.ttsAutoSpeak)
            put("themeMode", settings.themeMode.name)
        }
        root.put("preferences", prefsObj)

        val notesArray = JSONArray()
        for (note in notes) {
            notesArray.put(
                JSONObject().apply {
                    put("title", note.title)
                    put("content", note.content)
                    put("category", note.category)
                    put("language", note.language)
                    put("createdAt", note.createdAt)
                    put("updatedAt", note.updatedAt)
                }
            )
        }
        root.put("notes", notesArray)

        val remindersArray = JSONArray()
        for (rem in reminders) {
            remindersArray.put(
                JSONObject().apply {
                    put("title", rem.title)
                    put("triggerAtMillis", rem.triggerAtMillis)
                    put("isCompleted", rem.isCompleted)
                }
            )
        }
        root.put("reminders", remindersArray)
        return root.toString(2)
    }

    suspend fun importFromJson(jsonText: String): Result<BackupImportSummary> {
        return try {
            val root = JSONObject(jsonText)
            val format = root.optString("format")
            if (format != "osaf_jarvis_backup_v1") {
                return Result.failure(IllegalArgumentException("Unsupported backup format."))
            }

            root.optJSONObject("preferences")?.let { p ->
                val lang = LanguageMode.entries.find { it.code == p.optString("preferredLanguage") }
                    ?: LanguageMode.AUTO
                val theme = HudThemeMode.entries.find { it.name == p.optString("themeMode") }
                    ?: HudThemeMode.CYBER_CYAN
                settingsRepository.updatePersonalization(
                    assistantName = p.optString("assistantName", "JARVIS"),
                    userDisplayName = p.optString("userDisplayName", "Sir"),
                    preferredLanguage = lang,
                    themeMode = theme
                )
            }

            var notesCount = 0
            val notesArr = root.optJSONArray("notes")
            if (notesArr != null) {
                for (i in 0 until notesArr.length()) {
                    val obj = notesArr.optJSONObject(i) ?: continue
                    val content = obj.optString("content").trim()
                    if (content.isNotEmpty()) {
                        memoryRepository.addNote(
                            title = obj.optString("title", "Imported Note"),
                            content = content,
                            category = obj.optString("category", "General"),
                            language = obj.optString("language", "en")
                        )
                        notesCount++
                    }
                }
            }

            var remindersCount = 0
            val remArr = root.optJSONArray("reminders")
            if (remArr != null) {
                for (i in 0 until remArr.length()) {
                    val obj = remArr.optJSONObject(i) ?: continue
                    val title = obj.optString("title").trim()
                    val triggerAt = obj.optLong("triggerAtMillis", System.currentTimeMillis() + 3600_000L)
                    if (title.isNotEmpty()) {
                        val id = memoryRepository.addReminder(title, triggerAt)
                        if (obj.optBoolean("isCompleted", false)) {
                            memoryRepository.toggleReminderCompleted(id, true)
                        }
                        remindersCount++
                    }
                }
            }

            Result.success(BackupImportSummary(notesCount, remindersCount))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
