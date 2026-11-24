package com.osaf.jarvis.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osaf.jarvis.JarvisApplication
import com.osaf.jarvis.ai.ConnectionTestResult
import com.osaf.jarvis.database.HudThemeMode
import com.osaf.jarvis.database.JarvisSettings
import com.osaf.jarvis.security.UrlValidationResult
import com.osaf.jarvis.security.UrlValidator
import com.osaf.jarvis.services.JarvisVoiceService
import com.osaf.jarvis.utils.DeviceDiagnostics
import com.osaf.jarvis.utils.LanguageMode
import com.osaf.jarvis.utils.SystemDiagnostics
import com.osaf.jarvis.voice.TtsVoiceOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as JarvisApplication).container
    private val settingsRepo = container.settingsRepository
    private val openAiClient = container.openAiClient
    private val voiceEngine = container.voiceEngine

    val settings: StateFlow<JarvisSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JarvisSettings())

    val availableVoices: StateFlow<List<TtsVoiceOption>> = voiceEngine.availableVoices

    private val _diagnostics = MutableStateFlow(DeviceDiagnostics.collect(application))
    val diagnostics: StateFlow<SystemDiagnostics> = _diagnostics.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<ConnectionTestResult?>(null)
    val connectionTestResult: StateFlow<ConnectionTestResult?> = _connectionTestResult.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    fun saveApiConfig(
        endpoint: String,
        newApiKeyInput: String,
        modelName: String,
        temperature: Float,
        maxTokens: Int,
        timeoutSeconds: Int,
        streamingEnabled: Boolean
    ) {
        val urlCheck = UrlValidator.validateHttpsEndpoint(endpoint)
        if (urlCheck is UrlValidationResult.Invalid) {
            _feedbackMessage.value = urlCheck.reason
            return
        }
        viewModelScope.launch {
            settingsRepo.updateApiConfig(
                endpoint = (urlCheck as UrlValidationResult.Valid).normalizedUrl,
                modelName = modelName,
                temperature = temperature,
                maxTokens = maxTokens,
                timeoutSeconds = timeoutSeconds,
                streamingEnabled = streamingEnabled
            )
            if (newApiKeyInput.isNotBlank()) {
                settingsRepo.saveApiKey(newApiKeyInput)
            }
            _feedbackMessage.value = "AI configuration saved securely (Android Keystore AES-256)."
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            settingsRepo.saveApiKey("")
            _feedbackMessage.value = "Stored API key removed."
        }
    }

    fun testApiConnection(
        endpoint: String,
        unsavedApiKey: String,
        modelName: String,
        timeoutSeconds: Int
    ) {
        val urlCheck = UrlValidator.validateHttpsEndpoint(endpoint)
        if (urlCheck is UrlValidationResult.Invalid) {
            _connectionTestResult.value = ConnectionTestResult(false, urlCheck.reason, 0L)
            return
        }
        _isTestingConnection.value = true
        _connectionTestResult.value = null
        viewModelScope.launch {
            val activeKey = unsavedApiKey.trim().ifEmpty { settingsRepo.getDecryptedApiKey() }
            val testSettings = settings.value.copy(
                apiEndpoint = (urlCheck as UrlValidationResult.Valid).normalizedUrl,
                modelName = modelName.trim().ifEmpty { "gpt-4o-mini" },
                timeoutSeconds = timeoutSeconds.coerceIn(5, 120)
            )
            _connectionTestResult.value = openAiClient.testConnection(testSettings, activeKey)
            _isTestingConnection.value = false
        }
    }

    fun updatePersonalization(
        assistantName: String,
        userDisplayName: String,
        preferredLanguage: LanguageMode,
        themeMode: HudThemeMode
    ) {
        viewModelScope.launch {
            settingsRepo.updatePersonalization(assistantName, userDisplayName, preferredLanguage, themeMode)
            _feedbackMessage.value = "Personalization preferences updated."
        }
    }

    fun updateVoiceSettings(
        selectedVoiceName: String,
        speechSpeed: Float,
        speechPitch: Float,
        ttsAutoSpeak: Boolean,
        wakeWordEnabled: Boolean,
        wakeWordSensitivity: Float
    ) {
        viewModelScope.launch {
            settingsRepo.updateVoiceSettings(
                selectedVoiceName = selectedVoiceName,
                speechSpeed = speechSpeed,
                speechPitch = speechPitch,
                ttsAutoSpeak = ttsAutoSpeak,
                wakeWordEnabled = wakeWordEnabled,
                wakeWordSensitivity = wakeWordSensitivity
            )
            val app = getApplication<Application>()
            if (wakeWordEnabled) {
                JarvisVoiceService.start(app)
            } else {
                JarvisVoiceService.stop(app)
            }
        }
    }

    fun testVoice(speechSpeed: Float, speechPitch: Float, selectedVoiceName: String, userDisplayName: String) {
        voiceEngine.speak(
            text = "Hello $userDisplayName, OSAF JARVIS voice telemetry is calibrated and online.",
            languageMode = LanguageMode.ENGLISH,
            speechRate = speechSpeed,
            speechPitch = speechPitch,
            selectedVoiceName = selectedVoiceName
        )
    }

    fun setPreferOnline(online: Boolean) {
        viewModelScope.launch { settingsRepo.setPreferOnlineMode(online) }
    }

    fun updatePrivacyAndNotifications(
        notificationsEnabled: Boolean,
        shareNotesWithOnlineAi: Boolean,
        saveChatHistory: Boolean
    ) {
        viewModelScope.launch {
            settingsRepo.updatePrivacyAndNotifications(
                notificationsEnabled = notificationsEnabled,
                shareNotesWithOnlineAi = shareNotesWithOnlineAi,
                saveChatHistory = saveChatHistory
            )
        }
    }

    fun refreshVoices() = voiceEngine.refreshVoices()

    fun dismissFeedback() {
        _feedbackMessage.value = null
    }
}
