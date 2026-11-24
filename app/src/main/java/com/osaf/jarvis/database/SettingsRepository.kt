package com.osaf.jarvis.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.osaf.jarvis.security.KeystoreCryptoManager
import com.osaf.jarvis.utils.LanguageMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "osaf_jarvis_prefs")

enum class HudThemeMode(val displayName: String) {
    SIMPLE_DARK("Simple Dark"),
    SIMPLE_LIGHT("Simple Light"),
    CYBER_CYAN("Classic HUD")
}

data class JarvisSettings(
    val assistantName: String = "JARVIS",
    val userDisplayName: String = "Sir",
    val preferredLanguage: LanguageMode = LanguageMode.AUTO,
    val selectedVoiceName: String = "",
    val speechSpeed: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val ttsAutoSpeak: Boolean = true,
    val wakeWordEnabled: Boolean = false,
    val wakeWordSensitivity: Float = 0.65f,
    val preferOnlineMode: Boolean = true,
    val apiEndpoint: String = "https://api.openai.com/v1/chat/completions",
    val hasApiKey: Boolean = false,
    val maskedApiKey: String = "Not configured",
    val modelName: String = "gpt-4o-mini",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 600,
    val timeoutSeconds: Int = 30,
    val streamingEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val shareNotesWithOnlineAi: Boolean = false,
    val saveChatHistory: Boolean = true,
    val themeMode: HudThemeMode = HudThemeMode.SIMPLE_DARK
)

class SettingsRepository(
    private val context: Context,
    private val cryptoManager: KeystoreCryptoManager
) {
    private object Keys {
        val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val SELECTED_VOICE = stringPreferencesKey("selected_voice")
        val SPEECH_SPEED = floatPreferencesKey("speech_speed")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val TTS_AUTO_SPEAK = booleanPreferencesKey("tts_auto_speak")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val WAKE_WORD_SENSITIVITY = floatPreferencesKey("wake_word_sensitivity")
        val PREFER_ONLINE = booleanPreferencesKey("prefer_online")
        val API_ENDPOINT = stringPreferencesKey("api_endpoint")
        val ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_api_key")
        val MODEL_NAME = stringPreferencesKey("model_name")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val TIMEOUT_SECONDS = intPreferencesKey("timeout_seconds")
        val STREAMING_ENABLED = booleanPreferencesKey("streaming_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val SHARE_NOTES_ONLINE = booleanPreferencesKey("share_notes_online")
        val SAVE_CHAT_HISTORY = booleanPreferencesKey("save_chat_history")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settingsFlow: Flow<JarvisSettings> = context.jarvisDataStore.data.map { prefs ->
        val encryptedKey = prefs[Keys.ENCRYPTED_API_KEY].orEmpty()
        val decrypted = if (encryptedKey.isNotEmpty()) cryptoManager.decrypt(encryptedKey) else ""
        JarvisSettings(
            assistantName = prefs[Keys.ASSISTANT_NAME]?.ifBlank { "JARVIS" } ?: "JARVIS",
            userDisplayName = prefs[Keys.USER_DISPLAY_NAME]?.ifBlank { "Sir" } ?: "Sir",
            preferredLanguage = LanguageMode.entries.find { it.code == prefs[Keys.PREFERRED_LANGUAGE] }
                ?: LanguageMode.AUTO,
            selectedVoiceName = prefs[Keys.SELECTED_VOICE].orEmpty(),
            speechSpeed = (prefs[Keys.SPEECH_SPEED] ?: 1.0f).coerceIn(0.5f, 2.0f),
            speechPitch = (prefs[Keys.SPEECH_PITCH] ?: 1.0f).coerceIn(0.5f, 2.0f),
            ttsAutoSpeak = prefs[Keys.TTS_AUTO_SPEAK] ?: true,
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            wakeWordSensitivity = (prefs[Keys.WAKE_WORD_SENSITIVITY] ?: 0.65f).coerceIn(0.2f, 0.95f),
            preferOnlineMode = prefs[Keys.PREFER_ONLINE] ?: true,
            apiEndpoint = prefs[Keys.API_ENDPOINT]?.ifBlank { "https://api.openai.com/v1/chat/completions" }
                ?: "https://api.openai.com/v1/chat/completions",
            hasApiKey = decrypted.isNotBlank(),
            maskedApiKey = cryptoManager.maskKey(decrypted),
            modelName = prefs[Keys.MODEL_NAME]?.ifBlank { "gpt-4o-mini" } ?: "gpt-4o-mini",
            temperature = (prefs[Keys.TEMPERATURE] ?: 0.7f).coerceIn(0.0f, 1.5f),
            maxTokens = (prefs[Keys.MAX_TOKENS] ?: 600).coerceIn(64, 4096),
            timeoutSeconds = (prefs[Keys.TIMEOUT_SECONDS] ?: 30).coerceIn(5, 120),
            streamingEnabled = prefs[Keys.STREAMING_ENABLED] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            shareNotesWithOnlineAi = prefs[Keys.SHARE_NOTES_ONLINE] ?: false,
            saveChatHistory = prefs[Keys.SAVE_CHAT_HISTORY] ?: true,
            themeMode = HudThemeMode.entries.find { it.name == prefs[Keys.THEME_MODE] }
                ?: HudThemeMode.SIMPLE_DARK
        )
    }

    suspend fun getDecryptedApiKey(): String {
        val prefs = context.jarvisDataStore.data.first()
        val encrypted = prefs[Keys.ENCRYPTED_API_KEY].orEmpty()
        return if (encrypted.isNotBlank()) cryptoManager.decrypt(encrypted) else ""
    }

    suspend fun saveApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        context.jarvisDataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(Keys.ENCRYPTED_API_KEY)
            } else {
                prefs[Keys.ENCRYPTED_API_KEY] = cryptoManager.encrypt(trimmed)
            }
        }
    }

    suspend fun updatePersonalization(
        assistantName: String,
        userDisplayName: String,
        preferredLanguage: LanguageMode,
        themeMode: HudThemeMode
    ) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.ASSISTANT_NAME] = assistantName.trim().ifEmpty { "JARVIS" }
            prefs[Keys.USER_DISPLAY_NAME] = userDisplayName.trim().ifEmpty { "Sir" }
            prefs[Keys.PREFERRED_LANGUAGE] = preferredLanguage.code
            prefs[Keys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun updateVoiceSettings(
        selectedVoiceName: String,
        speechSpeed: Float,
        speechPitch: Float,
        ttsAutoSpeak: Boolean,
        wakeWordEnabled: Boolean,
        wakeWordSensitivity: Float
    ) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.SELECTED_VOICE] = selectedVoiceName
            prefs[Keys.SPEECH_SPEED] = speechSpeed.coerceIn(0.5f, 2.0f)
            prefs[Keys.SPEECH_PITCH] = speechPitch.coerceIn(0.5f, 2.0f)
            prefs[Keys.TTS_AUTO_SPEAK] = ttsAutoSpeak
            prefs[Keys.WAKE_WORD_ENABLED] = wakeWordEnabled
            prefs[Keys.WAKE_WORD_SENSITIVITY] = wakeWordSensitivity.coerceIn(0.2f, 0.95f)
        }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.WAKE_WORD_ENABLED] = enabled
        }
    }

    suspend fun setPreferOnlineMode(online: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.PREFER_ONLINE] = online
        }
    }

    suspend fun updateApiConfig(
        endpoint: String,
        modelName: String,
        temperature: Float,
        maxTokens: Int,
        timeoutSeconds: Int,
        streamingEnabled: Boolean
    ) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.API_ENDPOINT] = endpoint.trim()
            prefs[Keys.MODEL_NAME] = modelName.trim().ifEmpty { "gpt-4o-mini" }
            prefs[Keys.TEMPERATURE] = temperature.coerceIn(0.0f, 1.5f)
            prefs[Keys.MAX_TOKENS] = maxTokens.coerceIn(64, 4096)
            prefs[Keys.TIMEOUT_SECONDS] = timeoutSeconds.coerceIn(5, 120)
            prefs[Keys.STREAMING_ENABLED] = streamingEnabled
        }
    }

    suspend fun updatePrivacyAndNotifications(
        notificationsEnabled: Boolean,
        shareNotesWithOnlineAi: Boolean,
        saveChatHistory: Boolean
    ) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = notificationsEnabled
            prefs[Keys.SHARE_NOTES_ONLINE] = shareNotesWithOnlineAi
            prefs[Keys.SAVE_CHAT_HISTORY] = saveChatHistory
        }
    }
}
