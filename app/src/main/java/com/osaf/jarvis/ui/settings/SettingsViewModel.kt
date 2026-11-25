package com.osaf.jarvis.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osaf.jarvis.JarvisApplication
import com.osaf.jarvis.ai.ConnectionTestResult
import com.osaf.jarvis.database.HudThemeMode
import com.osaf.jarvis.database.JarvisSettings
import com.osaf.jarvis.database.NeuralTtsProvider
import com.osaf.jarvis.security.UrlValidationResult
import com.osaf.jarvis.security.UrlValidator
import com.osaf.jarvis.services.JarvisVoiceService
import com.osaf.jarvis.utils.DeviceDiagnostics
import com.osaf.jarvis.utils.LanguageMode
import com.osaf.jarvis.utils.PerformanceMetrics
import com.osaf.jarvis.utils.RuntimePerformanceSnapshot
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
    private val deviceController = container.deviceController

    val settings: StateFlow<JarvisSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JarvisSettings())

    val availableVoices: StateFlow<List<TtsVoiceOption>> = voiceEngine.availableVoices
    val activeVoiceSource: StateFlow<String> = voiceEngine.activeVoiceSource
    val perfSnapshot: StateFlow<RuntimePerformanceSnapshot> = PerformanceMetrics.snapshot

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
            _feedbackMessage.value = "AI configuration saved securely (Android Keystore AES-GCM)."
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            settingsRepo.saveApiKey("")
            _feedbackMessage.value = "Stored AI API key removed."
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

    fun saveNeuralTtsConfig(
        provider: NeuralTtsProvider,
        endpoint: String,
        model: String,
        voiceId: String,
        newTtsApiKey: String
    ) {
        if (provider != NeuralTtsProvider.DEVICE_BEST) {
            val effectiveEndpoint = endpoint.ifBlank { provider.defaultEndpoint }
            val urlCheck = UrlValidator.validateHttpsEndpoint(effectiveEndpoint)
            if (urlCheck is UrlValidationResult.Invalid) {
                _feedbackMessage.value = urlCheck.reason
                return
            }
        }
        viewModelScope.launch {
            settingsRepo.updateNeuralTtsConfig(
                provider = provider,
                endpoint = endpoint.ifBlank { provider.defaultEndpoint },
                model = model,
                voiceId = voiceId,
                newTtsApiKey = newTtsApiKey
            )
            _feedbackMessage.value = "Voice provider set to ${provider.displayName}."
        }
    }

    fun clearTtsApiKey() {
        viewModelScope.launch {
            settingsRepo.saveTtsApiKey("")
            _feedbackMessage.value = "Dedicated TTS API key removed."
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
        voiceVolume: Float,
        ttsAutoSpeak: Boolean,
        wakeWordEnabled: Boolean,
        wakeWordSensitivity: Float,
        continuousConversation: Boolean,
        bargeInEnabled: Boolean
    ) {
        viewModelScope.launch {
            settingsRepo.updateVoiceSettings(
                selectedVoiceName = selectedVoiceName,
                speechSpeed = speechSpeed,
                speechPitch = speechPitch,
                voiceVolume = voiceVolume,
                ttsAutoSpeak = ttsAutoSpeak,
                wakeWordEnabled = wakeWordEnabled,
                wakeWordSensitivity = wakeWordSensitivity,
                continuousConversation = continuousConversation,
                bargeInEnabled = bargeInEnabled
            )
            val app = getApplication<Application>()
            if (wakeWordEnabled) {
                JarvisVoiceService.start(app)
            } else {
                JarvisVoiceService.stop(app)
            }
        }
    }

    fun testVoice(currentSettings: JarvisSettings) {
        viewModelScope.launch {
            val online = deviceController.checkCurrentConnectivity() && currentSettings.preferOnlineMode
            val dedicatedTtsKey = settingsRepo.getDecryptedTtsApiKey()
            val effectiveKey = dedicatedTtsKey.ifBlank {
                if (currentSettings.neuralTtsProvider == NeuralTtsProvider.OPENAI ||
                    currentSettings.neuralTtsProvider == NeuralTtsProvider.CUSTOM_HTTPS
                ) {
                    settingsRepo.getDecryptedApiKey()
                } else {
                    ""
                }
            }
            val allowNeural = online &&
                currentSettings.neuralTtsProvider != NeuralTtsProvider.DEVICE_BEST &&
                effectiveKey.isNotBlank()

            val sampleText = when (currentSettings.preferredLanguage) {
                LanguageMode.URDU -> "السلام علیکم ${currentSettings.userDisplayName}، میں ${currentSettings.assistantName} ہوں۔ آپ کی آواز کی سیٹنگز بالکل تیار ہیں۔"
                LanguageMode.ROMAN_URDU, LanguageMode.AUTO ->
                    "Assalam o alaikum ${currentSettings.userDisplayName}, main ${currentSettings.assistantName} hoon! Sab ready hai, batayein kya help karoon?"
                LanguageMode.ENGLISH ->
                    "Hey ${currentSettings.userDisplayName}, I'm ${currentSettings.assistantName}. Voice calibration is active and ready whenever you are."
            }
            voiceEngine.speak(
                text = sampleText,
                languageMode = currentSettings.preferredLanguage,
                settings = currentSettings,
                ttsApiKey = effectiveKey,
                allowNeural = allowNeural
            )
        }
    }

    fun setPreferOnline(online: Boolean) {
        viewModelScope.launch { settingsRepo.setPreferOnlineMode(online) }
    }

    fun updatePrivacyAndNotifications(
        notificationsEnabled: Boolean,
        shareNotesWithOnlineAi: Boolean,
        shareMemoryWithOnlineAi: Boolean,
        saveChatHistory: Boolean
    ) {
        viewModelScope.launch {
            settingsRepo.updatePrivacyAndNotifications(
                notificationsEnabled = notificationsEnabled,
                shareNotesWithOnlineAi = shareNotesWithOnlineAi,
                shareMemoryWithOnlineAi = shareMemoryWithOnlineAi,
                saveChatHistory = saveChatHistory
            )
        }
    }

    fun refreshVoices() = voiceEngine.refreshVoices()

    fun refreshPerformance() = PerformanceMetrics.refreshMemory()

    fun dismissFeedback() {
        _feedbackMessage.value = null
    }
}
