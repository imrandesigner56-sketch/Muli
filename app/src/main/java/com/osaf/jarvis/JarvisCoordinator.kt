package com.osaf.jarvis

import com.osaf.jarvis.ai.AiStreamEvent
import com.osaf.jarvis.ai.OfflineAiEngine
import com.osaf.jarvis.ai.OpenAiClient
import com.osaf.jarvis.commands.CommandInterpreter
import com.osaf.jarvis.commands.CommandOutcome
import com.osaf.jarvis.commands.PendingConfirmation
import com.osaf.jarvis.database.ChatRepository
import com.osaf.jarvis.database.JarvisSettings
import com.osaf.jarvis.database.MemoryRepository
import com.osaf.jarvis.database.SettingsRepository
import com.osaf.jarvis.device.DeviceActionResult
import com.osaf.jarvis.device.DeviceController
import com.osaf.jarvis.utils.LanguageMode
import com.osaf.jarvis.voice.VoiceEngine
import com.osaf.jarvis.wakeword.WakeWordEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ReactorState(val label: String) {
    IDLE("STANDBY"),
    LISTENING("LISTENING"),
    THINKING("PROCESSING"),
    SPEAKING("SPEAKING"),
    OFFLINE("OFFLINE MODE"),
    ERROR("ATTENTION")
}

class JarvisCoordinator(
    private val deviceController: DeviceController,
    private val settingsRepository: SettingsRepository,
    private val memoryRepository: MemoryRepository,
    private val chatRepository: ChatRepository,
    private val commandInterpreter: CommandInterpreter,
    private val openAiClient: OpenAiClient,
    private val offlineAiEngine: OfflineAiEngine,
    private val voiceEngine: VoiceEngine,
    private val wakeWordEngine: WakeWordEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _isThinking = MutableStateFlow(false)
    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _lastTranscript = MutableStateFlow("")
    val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()

    private val _lastResponse = MutableStateFlow("Hello Sir, I am OSAF JARVIS. How may I assist you?")
    val lastResponse: StateFlow<String> = _lastResponse.asStateFlow()

    private val _streamingResponse = MutableStateFlow("")
    val streamingResponse: StateFlow<String> = _streamingResponse.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    val isOnlineActive: StateFlow<Boolean> = combine(
        deviceController.isNetworkConnected,
        settingsRepository.settingsFlow
    ) { hasNet, settings ->
        hasNet && settings.preferOnlineMode
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val reactorState: StateFlow<ReactorState> = combine(
        voiceEngine.isListening,
        _isThinking,
        voiceEngine.isSpeaking,
        isOnlineActive,
        _errorBanner
    ) { listening, thinking, speaking, online, err ->
        when {
            err != null -> ReactorState.ERROR
            listening -> ReactorState.LISTENING
            thinking -> ReactorState.THINKING
            speaking -> ReactorState.SPEAKING
            !online -> ReactorState.OFFLINE
            else -> ReactorState.IDLE
        }
    }.stateIn(scope, SharingStarted.Eagerly, ReactorState.IDLE)

    private var lastProcessedText = ""
    private var lastProcessedAt = 0L

    fun startVoiceCommand() {
        _errorBanner.value = null
        scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val online = deviceController.isNetworkConnected.value && settings.preferOnlineMode
            voiceEngine.startListening(
                languageMode = settings.preferredLanguage,
                preferOffline = !online,
                onResult = { spoken ->
                    submitUserInput(spoken, fromVoice = true)
                },
                onError = { errMsg ->
                    _errorBanner.value = errMsg
                }
            )
        }
    }

    fun cancelVoiceCommand() {
        voiceEngine.cancelListening()
    }

    fun stopSpeaking() {
        voiceEngine.stopSpeaking()
    }

    fun clearError() {
        _errorBanner.value = null
    }

    fun triggerWakePhraseVerification(assistantName: String, onFinished: () -> Unit) {
        scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val online = deviceController.isNetworkConnected.value && settings.preferOnlineMode
            voiceEngine.startListening(
                languageMode = settings.preferredLanguage,
                preferOffline = !online,
                onResult = { transcript ->
                    if (wakeWordEngine.matchesWakePhrase(transcript, assistantName)) {
                        val remainder = commandInterpreter.stripWakePhrase(transcript, assistantName)
                        if (remainder.isNotBlank()) {
                            submitUserInput(transcript, fromVoice = true)
                            onFinished()
                        } else {
                            // Greet & listen immediately for follow-up command
                            val greet = "Yes ${settings.userDisplayName}?"
                            _lastResponse.value = greet
                            if (settings.ttsAutoSpeak) {
                                voiceEngine.speak(
                                    greet,
                                    LanguageMode.ENGLISH,
                                    settings.speechSpeed,
                                    settings.speechPitch,
                                    settings.selectedVoiceName
                                )
                            }
                            startVoiceCommand()
                            onFinished()
                        }
                    } else {
                        onFinished()
                    }
                },
                onError = {
                    onFinished()
                }
            )
        }
    }

    fun reportWakeError(msg: String) {
        _errorBanner.value = msg
    }

    fun confirmPendingAction() {
        val action = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        val res = when (action) {
            is PendingConfirmation.Call -> deviceController.prepareDialer(action.target)
            is PendingConfirmation.Sms -> deviceController.prepareSms(action.recipient, action.body)
        }
        val reply = when (res) {
            is DeviceActionResult.Success -> res.message
            is DeviceActionResult.Error -> res.message
        }
        _lastResponse.value = reply
        scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.saveChatHistory) {
                chatRepository.addMessage("assistant", reply, "en", "command")
            }
            if (settings.ttsAutoSpeak) {
                voiceEngine.speak(reply, LanguageMode.ENGLISH, settings.speechSpeed, settings.speechPitch, settings.selectedVoiceName)
            }
        }
    }

    fun cancelPendingAction() {
        _pendingConfirmation.value = null
        _lastResponse.value = "Action cancelled, Sir."
    }

    fun submitUserInput(rawText: String, fromVoice: Boolean = false) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return

        // Duplicate command execution guard (1.5s window)
        val now = System.currentTimeMillis()
        if (trimmed.equals(lastProcessedText, ignoreCase = true) && (now - lastProcessedAt) < 1500L) {
            return
        }
        lastProcessedText = trimmed
        lastProcessedAt = now

        _errorBanner.value = null
        _lastTranscript.value = trimmed
        _isThinking.value = true
        _streamingResponse.value = ""

        scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            try {
                when (val outcome = commandInterpreter.interpretAndExecute(trimmed)) {
                    is CommandOutcome.Handled -> {
                        if (settings.saveChatHistory) {
                            chatRepository.addMessage("user", trimmed, outcome.language.code, if (fromVoice) "voice" else "text")
                            chatRepository.addMessage("assistant", outcome.responseText, outcome.language.code, outcome.source)
                        }
                        _pendingConfirmation.value = outcome.confirmation
                        _lastResponse.value = outcome.responseText
                        _isThinking.value = false
                        speakIfEnabled(outcome.responseText, outcome.language, settings)
                    }

                    is CommandOutcome.NeedsOnlineAi -> {
                        if (settings.saveChatHistory) {
                            chatRepository.addMessage("user", trimmed, outcome.language.code, if (fromVoice) "voice" else "text")
                        }
                        val hasNet = deviceController.checkCurrentConnectivity()
                        val apiKey = settingsRepository.getDecryptedApiKey()

                        if (!settings.preferOnlineMode || !hasNet || apiKey.isBlank()) {
                            val reason = when {
                                !settings.preferOnlineMode -> "Online Mode is disabled in Settings."
                                !hasNet -> "No internet connection detected."
                                else -> "No API key configured in Settings."
                            }
                            val fallback = offlineAiEngine.buildOfflineFallbackResponse(
                                userDisplayName = settings.userDisplayName,
                                assistantName = settings.assistantName,
                                language = outcome.language,
                                reason = reason
                            )
                            if (settings.saveChatHistory) {
                                chatRepository.addMessage("assistant", fallback, outcome.language.code, "offline")
                            }
                            _lastResponse.value = fallback
                            _isThinking.value = false
                            speakIfEnabled(fallback, outcome.language, settings)
                            return@launch
                        }

                        val history = if (settings.saveChatHistory) chatRepository.getRecentContext(10) else emptyList()
                        val notes = if (settings.shareNotesWithOnlineAi) memoryRepository.getNotesSnapshot() else emptyList()
                        val buffer = StringBuilder()

                        openAiClient.streamChatCompletion(
                            settings = settings,
                            apiKey = apiKey,
                            userPrompt = outcome.cleanedQuery,
                            history = history,
                            detectedLanguage = outcome.language,
                            sharedNotes = notes
                        ).collect { event ->
                            when (event) {
                                is AiStreamEvent.Delta -> {
                                    buffer.append(event.chunk)
                                    _streamingResponse.value = buffer.toString()
                                    _lastResponse.value = buffer.toString()
                                }

                                is AiStreamEvent.Completed -> {
                                    _streamingResponse.value = ""
                                    _lastResponse.value = event.fullText
                                    _isThinking.value = false
                                    if (settings.saveChatHistory) {
                                        chatRepository.addMessage("assistant", event.fullText, outcome.language.code, "online")
                                    }
                                    speakIfEnabled(event.fullText, outcome.language, settings)
                                }

                                is AiStreamEvent.Error -> {
                                    _streamingResponse.value = ""
                                    _isThinking.value = false
                                    _errorBanner.value = event.message
                                    val safeMsg = "${settings.userDisplayName}, ${event.message}"
                                    _lastResponse.value = safeMsg
                                    if (settings.saveChatHistory) {
                                        chatRepository.addMessage("assistant", safeMsg, outcome.language.code, "error")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _isThinking.value = false
                _errorBanner.value = e.localizedMessage ?: "Command processing error."
            }
        }
    }

    private fun speakIfEnabled(text: String, lang: LanguageMode, settings: JarvisSettings) {
        if (settings.ttsAutoSpeak) {
            voiceEngine.speak(
                text = text,
                languageMode = lang,
                speechRate = settings.speechSpeed,
                speechPitch = settings.speechPitch,
                selectedVoiceName = settings.selectedVoiceName
            )
        }
    }
}
