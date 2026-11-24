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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ReactorState(val label: String) {
    IDLE("Ready"),
    LISTENING("Listening"),
    THINKING("Thinking"),
    SPEAKING("Speaking"),
    OFFLINE("Offline"),
    ERROR("Issue")
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

    private val _lastResponse = MutableStateFlow("Hey Sir! I'm JARVIS — tap the mic or say \"Hey JARVIS\" anytime.")
    val lastResponse: StateFlow<String> = _lastResponse.asStateFlow()

    private val _streamingResponse = MutableStateFlow("")
    val streamingResponse: StateFlow<String> = _streamingResponse.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _isBottomPopupVisible = MutableStateFlow(false)
    val isBottomPopupVisible: StateFlow<Boolean> = _isBottomPopupVisible.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

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

    fun setAppForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }

    fun openBottomPopup() {
        _isBottomPopupVisible.value = true
    }

    fun dismissBottomPopup() {
        _isBottomPopupVisible.value = false
        voiceEngine.cancelListening()
        _errorBanner.value = null
    }

    fun startVoiceCommand(openBottomSheet: Boolean = true) {
        _errorBanner.value = null
        if (openBottomSheet) {
            _isBottomPopupVisible.value = true
        }
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
                    _lastResponse.value = errMsg
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

    /**
     * Suspends until JARVIS finishes listening, thinking, and speaking so
     * background wake-word monitoring never interrupts or hears its own reply.
     */
    suspend fun awaitAssistantIdle() {
        delay(250L)
        while (voiceEngine.isListening.value || _isThinking.value || voiceEngine.isSpeaking.value) {
            delay(250L)
        }
        delay(600L)
    }

    fun triggerWakePhraseVerification(
        assistantName: String,
        onWakeConfirmed: () -> Unit,
        onFinished: () -> Unit
    ) {
        scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val online = deviceController.isNetworkConnected.value && settings.preferOnlineMode
            voiceEngine.startListening(
                languageMode = settings.preferredLanguage,
                preferOffline = !online,
                onResult = { transcript ->
                    if (wakeWordEngine.matchesWakePhrase(transcript, assistantName)) {
                        _errorBanner.value = null
                        _isBottomPopupVisible.value = true
                        onWakeConfirmed()
                        val remainder = commandInterpreter.stripWakePhrase(transcript, assistantName)
                        if (remainder.isNotBlank()) {
                            submitUserInput(transcript, fromVoice = true)
                            onFinished()
                        } else {
                            val greet = if (settings.preferredLanguage == LanguageMode.ROMAN_URDU) {
                                "Haan ji ${settings.userDisplayName}, main sun raha hoon! Batayein?"
                            } else {
                                "Hey ${settings.userDisplayName}! I'm listening — what's up?"
                            }
                            _lastTranscript.value = transcript
                            _lastResponse.value = greet

                            val startFollowUpListen = {
                                scope.launch {
                                    delay(160L)
                                    voiceEngine.startListening(
                                        languageMode = settings.preferredLanguage,
                                        preferOffline = !online,
                                        onResult = { followUp ->
                                            submitUserInput(followUp, fromVoice = true)
                                            onFinished()
                                        },
                                        onError = { errMsg ->
                                            _errorBanner.value = errMsg
                                            onFinished()
                                        }
                                    )
                                }
                                Unit
                            }

                            if (settings.ttsAutoSpeak) {
                                voiceEngine.speakAndThen(
                                    text = greet,
                                    languageMode = LanguageMode.ENGLISH,
                                    speechRate = settings.speechSpeed,
                                    speechPitch = settings.speechPitch,
                                    selectedVoiceName = settings.selectedVoiceName,
                                    onDone = startFollowUpListen
                                )
                            } else {
                                startFollowUpListen()
                            }
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
        _lastResponse.value = "No problem, cancelled that for you, Sir."
    }

    fun submitUserInput(rawText: String, fromVoice: Boolean = false) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return

        val now = System.currentTimeMillis()
        if (trimmed.equals(lastProcessedText, ignoreCase = true) && (now - lastProcessedAt) < 1200L) {
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
                                !settings.preferOnlineMode -> "Online Mode is switched off in Settings."
                                !hasNet -> "No internet connection right now."
                                else -> "Add your API key in Settings to enable cloud replies."
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
                                    val safeMsg = "Sorry ${settings.userDisplayName}, ${event.message}"
                                    _lastResponse.value = safeMsg
                                    if (settings.saveChatHistory) {
                                        chatRepository.addMessage("assistant", safeMsg, outcome.language.code, "error")
                                    }
                                    speakIfEnabled(safeMsg, outcome.language, settings)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _isThinking.value = false
                val msg = e.localizedMessage ?: "Command processing error."
                _errorBanner.value = msg
                _lastResponse.value = msg
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
