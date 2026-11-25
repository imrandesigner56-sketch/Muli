package com.osaf.jarvis.database

import com.osaf.jarvis.utils.LanguageMode
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

data class BackupImportSummary(
    val notesImported: Int,
    val remindersImported: Int,
    val factsImported: Int
)

class BackupManager(
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun exportToJson(): String {
        val notes = memoryRepository.getNotesSnapshot()
        val reminders = memoryRepository.getRemindersSnapshot()
        val facts = memoryRepository.getFactsSnapshot()
        val settings = settingsRepository.settingsFlow.first()

        val root = JSONObject()
        root.put("format", "osaf_jarvis_backup_v2")
        root.put("exportedAt", System.currentTimeMillis())

        // Export non-secret preferences only (NEVER export API keys!)
        val prefsObj = JSONObject().apply {
            put("assistantName", settings.assistantName)
            put("userDisplayName", settings.userDisplayName)
            put("preferredLanguage", settings.preferredLanguage.code)
            put("speechSpeed", settings.speechSpeed.toDouble())
            put("speechPitch", settings.speechPitch.toDouble())
            put("voiceVolume", settings.voiceVolume.toDouble())
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

        val factsArray = JSONArray()
        for (fact in facts) {
            factsArray.put(
                JSONObject().apply {
                    put("category", fact.category)
                    put("factKey", fact.factKey)
                    put("factValue", fact.factValue)
                    put("importance", fact.importance)
                }
            )
        }
        root.put("memoryFacts", factsArray)

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
            if (format != "osaf_jarvis_backup_v1" && format != "osaf_jarvis_backup_v2") {
                return Result.failure(IllegalArgumentException("Unsupported backup format."))
            }

            root.optJSONObject("preferences")?.let { p ->
                val lang = LanguageMode.entries.find { it.code == p.optString("preferredLanguage") }
                    ?: LanguageMode.AUTO
                val theme = HudThemeMode.entries.find { it.name == p.optString("themeMode") }
                    ?: HudThemeMode.SIMPLE_DARK
                settingsRepository.updatePersonalization(
                    assistantName = p.optString("assistantName", "JARVIS"),
                    userDisplayName = p.optString("userDisplayName", "Osaf"),
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

            var factsCount = 0
            val factsArr = root.optJSONArray("memoryFacts")
            if (factsArr != null) {
                for (i in 0 until factsArr.length()) {
                    val obj = factsArr.optJSONObject(i) ?: continue
                    val k = obj.optString("factKey").trim()
                    val v = obj.optString("factValue").trim()
                    if (k.isNotEmpty() && v.isNotEmpty()) {
                        memoryRepository.upsertMemoryFact(
                            category = obj.optString("category", "Preference"),
                            factKey = k,
                            factValue = v,
                            importance = obj.optInt("importance", 2)
                        )
                        factsCount++
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

            Result.success(BackupImportSummary(notesCount, remindersCount, factsCount))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
