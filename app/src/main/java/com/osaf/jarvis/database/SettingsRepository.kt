package com.osaf.jarvis.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

enum class NeuralTtsProvider(val displayName: String, val defaultEndpoint: String, val defaultModel: String, val defaultVoice: String) {
    DEVICE_BEST(
        displayName = "Best Device Offline TTS",
        defaultEndpoint = "",
        defaultModel = "local",
        defaultVoice = ""
    ),
    OPENAI(
        displayName = "OpenAI Neural Voice",
        defaultEndpoint = "https://api.openai.com/v1/audio/speech",
        defaultModel = "gpt-4o-mini-tts",
        defaultVoice = "onyx"
    ),
    ELEVENLABS(
        displayName = "ElevenLabs Human Voice",
        defaultEndpoint = "https://api.elevenlabs.io/v1/text-to-speech",
        defaultModel = "eleven_multilingual_v2",
        defaultVoice = "pNInz6obpgDQGcFmaJgB"
    ),
    CUSTOM_HTTPS(
        displayName = "Custom Neural TTS URL",
        defaultEndpoint = "https://api.openai.com/v1/audio/speech",
        defaultModel = "tts-1",
        defaultVoice = "onyx"
    )
}

data class JarvisSettings(
    val assistantName: String = "JARVIS",
    val userDisplayName: String = "Osaf",
    val preferredLanguage: LanguageMode = LanguageMode.AUTO,
    val selectedVoiceName: String = "",
    val speechSpeed: Float = 1.0f,
    val speechPitch: Float = 0.98f,
    val voiceVolume: Float = 1.0f,
    val ttsAutoSpeak: Boolean = true,
    val neuralTtsProvider: NeuralTtsProvider = NeuralTtsProvider.DEVICE_BEST,
    val neuralTtsEndpoint: String = NeuralTtsProvider.OPENAI.defaultEndpoint,
    val neuralTtsModel: String = NeuralTtsProvider.OPENAI.defaultModel,
    val neuralTtsVoiceId: String = NeuralTtsProvider.OPENAI.defaultVoice,
    val hasTtsApiKey: Boolean = false,
    val maskedTtsApiKey: String = "Using main AI key",
    val wakeWordEnabled: Boolean = false,
    val wakeWordSensitivity: Float = 0.72f,
    val continuousConversation: Boolean = true,
    val bargeInEnabled: Boolean = true,
    val preferOnlineMode: Boolean = true,
    val apiEndpoint: String = "https://api.openai.com/v1/chat/completions",
    val hasApiKey: Boolean = false,
    val maskedApiKey: String = "Not configured",
    val modelName: String = "gpt-4o-mini",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 700,
    val timeoutSeconds: Int = 30,
    val streamingEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val shareNotesWithOnlineAi: Boolean = false,
    val shareMemoryWithOnlineAi: Boolean = true,
    val saveChatHistory: Boolean = true,
    val themeMode: HudThemeMode = HudThemeMode.SIMPLE_DARK,
    val lastInteractionAt: Long = 0L,
    val lastInactivityGreetingDay: String = ""
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
        val VOICE_VOLUME = floatPreferencesKey("voice_volume")
        val TTS_AUTO_SPEAK = booleanPreferencesKey("tts_auto_speak")
        val NEURAL_TTS_PROVIDER = stringPreferencesKey("neural_tts_provider")
        val NEURAL_TTS_ENDPOINT = stringPreferencesKey("neural_tts_endpoint")
        val NEURAL_TTS_MODEL = stringPreferencesKey("neural_tts_model")
        val NEURAL_TTS_VOICE_ID = stringPreferencesKey("neural_tts_voice_id")
        val ENCRYPTED_TTS_API_KEY = stringPreferencesKey("encrypted_tts_api_key")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val WAKE_WORD_SENSITIVITY = floatPreferencesKey("wake_word_sensitivity")
        val CONTINUOUS_CONVERSATION = booleanPreferencesKey("continuous_conversation")
        val BARGE_IN_ENABLED = booleanPreferencesKey("barge_in_enabled")
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
        val SHARE_MEMORY_ONLINE = booleanPreferencesKey("share_memory_online")
        val SAVE_CHAT_HISTORY = booleanPreferencesKey("save_chat_history")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_INTERACTION_AT = longPreferencesKey("last_interaction_at")
        val LAST_INACTIVITY_GREETING_DAY = stringPreferencesKey("last_inactivity_greeting_day")
    }

    val settingsFlow: Flow<JarvisSettings> = context.jarvisDataStore.data.map { prefs ->
        val encryptedKey = prefs[Keys.ENCRYPTED_API_KEY].orEmpty()
        val decrypted = if (encryptedKey.isNotEmpty()) cryptoManager.decrypt(encryptedKey) else ""

        val encryptedTtsKey = prefs[Keys.ENCRYPTED_TTS_API_KEY].orEmpty()
        val decryptedTts = if (encryptedTtsKey.isNotEmpty()) cryptoManager.decrypt(encryptedTtsKey) else ""

        val ttsProvider = NeuralTtsProvider.entries.find { it.name == prefs[Keys.NEURAL_TTS_PROVIDER] }
            ?: NeuralTtsProvider.DEVICE_BEST

        JarvisSettings(
            assistantName = prefs[Keys.ASSISTANT_NAME]?.ifBlank { "JARVIS" } ?: "JARVIS",
            userDisplayName = prefs[Keys.USER_DISPLAY_NAME]?.ifBlank { "Osaf" } ?: "Osaf",
            preferredLanguage = LanguageMode.entries.find { it.code == prefs[Keys.PREFERRED_LANGUAGE] }
                ?: LanguageMode.AUTO,
            selectedVoiceName = prefs[Keys.SELECTED_VOICE].orEmpty(),
            speechSpeed = (prefs[Keys.SPEECH_SPEED] ?: 1.0f).coerceIn(0.5f, 2.0f),
            speechPitch = (prefs[Keys.SPEECH_PITCH] ?: 0.98f).coerceIn(0.5f, 2.0f),
            voiceVolume = (prefs[Keys.VOICE_VOLUME] ?: 1.0f).coerceIn(0.1f, 1.0f),
            ttsAutoSpeak = prefs[Keys.TTS_AUTO_SPEAK] ?: true,
            neuralTtsProvider = ttsProvider,
            neuralTtsEndpoint = prefs[Keys.NEURAL_TTS_ENDPOINT]?.ifBlank { ttsProvider.defaultEndpoint }
                ?: ttsProvider.defaultEndpoint,
            neuralTtsModel = prefs[Keys.NEURAL_TTS_MODEL]?.ifBlank { ttsProvider.defaultModel }
                ?: ttsProvider.defaultModel,
            neuralTtsVoiceId = prefs[Keys.NEURAL_TTS_VOICE_ID]?.ifBlank { ttsProvider.defaultVoice }
                ?: ttsProvider.defaultVoice,
            hasTtsApiKey = decryptedTts.isNotBlank(),
            maskedTtsApiKey = if (decryptedTts.isNotBlank()) cryptoManager.maskKey(decryptedTts) else "Using main AI key",
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            wakeWordSensitivity = (prefs[Keys.WAKE_WORD_SENSITIVITY] ?: 0.72f).coerceIn(0.2f, 0.95f),
            continuousConversation = prefs[Keys.CONTINUOUS_CONVERSATION] ?: true,
            bargeInEnabled = prefs[Keys.BARGE_IN_ENABLED] ?: true,
            preferOnlineMode = prefs[Keys.PREFER_ONLINE] ?: true,
            apiEndpoint = prefs[Keys.API_ENDPOINT]?.ifBlank { "https://api.openai.com/v1/chat/completions" }
                ?: "https://api.openai.com/v1/chat/completions",
            hasApiKey = decrypted.isNotBlank(),
            maskedApiKey = cryptoManager.maskKey(decrypted),
            modelName = prefs[Keys.MODEL_NAME]?.ifBlank { "gpt-4o-mini" } ?: "gpt-4o-mini",
            temperature = (prefs[Keys.TEMPERATURE] ?: 0.7f).coerceIn(0.0f, 1.5f),
            maxTokens = (prefs[Keys.MAX_TOKENS] ?: 700).coerceIn(64, 4096),
            timeoutSeconds = (prefs[Keys.TIMEOUT_SECONDS] ?: 30).coerceIn(5, 120),
            streamingEnabled = prefs[Keys.STREAMING_ENABLED] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            shareNotesWithOnlineAi = prefs[Keys.SHARE_NOTES_ONLINE] ?: false,
            shareMemoryWithOnlineAi = prefs[Keys.SHARE_MEMORY_ONLINE] ?: true,
            saveChatHistory = prefs[Keys.SAVE_CHAT_HISTORY] ?: true,
            themeMode = HudThemeMode.entries.find { it.name == prefs[Keys.THEME_MODE] }
                ?: HudThemeMode.SIMPLE_DARK,
            lastInteractionAt = prefs[Keys.LAST_INTERACTION_AT] ?: 0L,
            lastInactivityGreetingDay = prefs[Keys.LAST_INACTIVITY_GREETING_DAY].orEmpty()
        )
    }

    suspend fun getDecryptedApiKey(): String {
        val prefs = context.jarvisDataStore.data.first()
        val encrypted = prefs[Keys.ENCRYPTED_API_KEY].orEmpty()
        return if (encrypted.isNotBlank()) cryptoManager.decrypt(encrypted) else ""
    }

    suspend fun getDecryptedTtsApiKey(): String {
        val prefs = context.jarvisDataStore.data.first()
        val encrypted = prefs[Keys.ENCRYPTED_TTS_API_KEY].orEmpty()
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

    suspend fun saveTtsApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        context.jarvisDataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(Keys.ENCRYPTED_TTS_API_KEY)
            } else {
                prefs[Keys.ENCRYPTED_TTS_API_KEY] = cryptoManager.encrypt(trimmed)
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
            prefs[Keys.USER_DISPLAY_NAME] = userDisplayName.trim().ifEmpty { "Osaf" }
            prefs[Keys.PREFERRED_LANGUAGE] = preferredLanguage.code
            prefs[Keys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun updateVoiceSettings(
        selectedVoiceName: String,
        speechSpeed: Float,
        speechPitch: Float,
        voiceVolume: Float,
        ttsAutoSpeak: Boolean,
        wakeWordEnabled: Boolean,
        wakeWordSensitivity: Float,
        continuousConversation: Boolean,
        bargeInEnabled: Boolean
    ) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.SELECTED_VOICE] = selectedVoiceName
            prefs[Keys.SPEECH_SPEED] = speechSpeed.coerceIn(0.5f, 2.0f)
            prefs[Keys.SPEECH_PITCH] = speechPitch.coerceIn(0.5f, 2.0f)
            prefs[Keys.VOICE_VOLUME] = voiceVolume.coerceIn(0.1f, 1.0f)
            prefs[Keys.TTS_AUTO_SPEAK] = ttsAutoSpeak
            prefs[Keys.WAKE_WORD_ENABLED] = wakeWordEnabled
            prefs[Keys.WAKE_WORD_SENSITIVITY] = wakeWordSensitivity.coerceIn(0.2f, 0.95f)
            prefs[Keys.CONTINUOUS_CONVERSATION] = continuousConversation
            prefs[Keys.BARGE_IN_ENABLED] = bargeInEnabled
        }
    }

    suspend fun updateNeuralTtsConfig(
        provider: NeuralTtsProvider,
        endpoint: String,
        model: String,
        voiceId: String,
        newTtsApiKey: String
    ) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.NEURAL_TTS_PROVIDER] = provider.name
            prefs[Keys.NEURAL_TTS_ENDPOINT] = endpoint.trim().ifEmpty { provider.defaultEndpoint }
            prefs[Keys.NEURAL_TTS_MODEL] = model.trim().ifEmpty { provider.defaultModel }
            prefs[Keys.NEURAL_TTS_VOICE_ID] = voiceId.trim().ifEmpty { provider.defaultVoice }
            if (newTtsApiKey.isNotBlank()) {
                prefs[Keys.ENCRYPTED_TTS_API_KEY] = cryptoManager.encrypt(newTtsApiKey.trim())
            }
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
        shareMemoryWithOnlineAi: Boolean,
        saveChatHistory: Boolean
    ) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = notificationsEnabled
            prefs[Keys.SHARE_NOTES_ONLINE] = shareNotesWithOnlineAi
            prefs[Keys.SHARE_MEMORY_ONLINE] = shareMemoryWithOnlineAi
            prefs[Keys.SAVE_CHAT_HISTORY] = saveChatHistory
        }
    }

    suspend fun recordInteraction(timestampMs: Long = System.currentTimeMillis()) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.LAST_INTERACTION_AT] = timestampMs
        }
    }

    suspend fun markInactivityGreetingShown(dayKey: String) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.LAST_INACTIVITY_GREETING_DAY] = dayKey
            prefs[Keys.LAST_INTERACTION_AT] = System.currentTimeMillis()
        }
    }
}
