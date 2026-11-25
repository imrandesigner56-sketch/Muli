package com.osaf.jarvis

import com.osaf.jarvis.ai.AiAttachmentContext
import com.osaf.jarvis.ai.AiStreamEvent
import com.osaf.jarvis.ai.OfflineAiEngine
import com.osaf.jarvis.ai.OpenAiClient
import com.osaf.jarvis.ai.PersonalityEngine
import com.osaf.jarvis.ai.WebSearchTool
import com.osaf.jarvis.commands.CommandInterpreter
import com.osaf.jarvis.commands.CommandOutcome
import com.osaf.jarvis.commands.PendingConfirmation
import com.osaf.jarvis.database.ChatRepository
import com.osaf.jarvis.database.JarvisSettings
import com.osaf.jarvis.database.MemoryRepository
import com.osaf.jarvis.database.NeuralTtsProvider
import com.osaf.jarvis.database.SettingsRepository
import com.osaf.jarvis.device.DeviceActionResult
import com.osaf.jarvis.device.DeviceController
import com.osaf.jarvis.utils.LanguageMode
import com.osaf.jarvis.utils.PreparedAttachment
import com.osaf.jarvis.voice.SpeechSentenceChunker
import com.osaf.jarvis.voice.VoiceEngine
import com.osaf.jarvis.wakeword.WakeWordEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val personalityEngine: PersonalityEngine,
    private val webSearchTool: WebSearchTool,
    private val voiceEngine: VoiceEngine,
    private val wakeWordEngine: WakeWordEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _isThinking = MutableStateFlow(false)
    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _lastTranscript = MutableStateFlow("")
    val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()

    private val _lastResponse = MutableStateFlow("Hey Osaf! I'm JARVIS — tap the mic or say \"Hey JARVIS\" anytime.")
    val lastResponse: StateFlow<String> = _lastResponse.asStateFlow()

    private val _streamingResponse = MutableStateFlow("")
    val streamingResponse: StateFlow<String> = _streamingResponse.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _isBottomPopupVisible = MutableStateFlow(false)
    val isBottomPopupVisible: StateFlow<Boolean> = _isBottomPopupVisible.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _isVoiceSessionActive = MutableStateFlow(false)
    val isVoiceSessionActive: StateFlow<Boolean> = _isVoiceSessionActive.asStateFlow()

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
    private var activeAiJob: Job? = null
    private var checkedInactivityOnLaunch = false

    init {
        voiceEngine.setBargeInHandler {
            handleBargeInInterrupt()
        }
        checkInactivityGreetingOnLaunch()
    }

    private fun checkInactivityGreetingOnLaunch() {
        if (checkedInactivityOnLaunch) return
        checkedInactivityOnLaunch = true
        scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (personalityEngine.shouldGreetForInactivity(settings.lastInteractionAt, settings.lastInactivityGreetingDay)) {
                val greeting = personalityEngine.buildInactivityGreeting(settings.userDisplayName, settings.preferredLanguage)
                _lastResponse.value = greeting
                settingsRepository.markInactivityGreetingShown(personalityEngine.todayKey())
            } else {
                val user = settings.userDisplayName
                if (_lastTranscript.value.isBlank()) {
                    _lastResponse.value = "Hey $user! I'm ${settings.assistantName} — tap the mic or say \"Hey ${settings.assistantName}\" anytime."
                }
            }
        }
    }

    fun setAppForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }

    fun openBottomPopup() {
        _isBottomPopupVisible.value = true
    }

    fun dismissBottomPopup() {
        _isBottomPopupVisible.value = false
        _isVoiceSessionActive.value = false
        activeAiJob?.cancel()
        _isThinking.value = false
        voiceEngine.cancelListening()
        voiceEngine.stopSpeaking()
        _errorBanner.value = null
    }

    /**
     * Interrupts current AI response / TTS immediately and opens microphone (Barge-In).
     */
    fun handleBargeInInterrupt() {
        activeAiJob?.cancel()
        _isThinking.value = false
        _streamingResponse.value = ""
        voiceEngine.stopSpeaking()
        startVoiceCommand(openBottomSheet = _isBottomPopupVisible.value)
    }

    fun onOrbOrMicTapped() {
        when {
            voiceEngine.isSpeaking.value -> handleBargeInInterrupt()
            voiceEngine.isListening.value -> {
                _isVoiceSessionActive.value = false
                cancelVoiceCommand()
            }
            else -> startVoiceCommand(openBottomSheet = true)
        }
    }

    fun startVoiceCommand(openBottomSheet: Boolean = true) {
        _errorBanner.value = null
        _isVoiceSessionActive.value = true
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
                    _isVoiceSessionActive.value = false
                    _errorBanner.value = errMsg
                    _lastResponse.value = errMsg
                }
            )
        }
    }

    fun cancelVoiceCommand() {
        _isVoiceSessionActive.value = false
        voiceEngine.cancelListening()
    }

    fun stopSpeaking() {
        _isVoiceSessionActive.value = false
        voiceEngine.stopSpeaking()
    }

    fun clearError() {
        _errorBanner.value = null
    }

    suspend fun awaitAssistantIdle() {
        delay(250L)
        while (voiceEngine.isListening.value || _isThinking.value || voiceEngine.isSpeaking.value || _isVoiceSessionActive.value) {
            delay(250L)
        }
        delay(500L)
    }

    fun triggerWakePhraseVerification(
        assistantName: String,
        onWakeConfirmed: () -> Unit,
        onFinished: () -> Unit
    ) {
        scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val online = deviceController.isNetworkConnected.value && settings.preferOnlineMode
            val ttsKey = resolveTtsKey(settings)
            voiceEngine.startListening(
                languageMode = settings.preferredLanguage,
                preferOffline = !online,
                onResult = { transcript ->
                    if (wakeWordEngine.matchesWakePhrase(transcript, assistantName)) {
                        _errorBanner.value = null
                        _isVoiceSessionActive.value = true
                        _isBottomPopupVisible.value = true
                        onWakeConfirmed()
                        val remainder = commandInterpreter.stripWakePhrase(transcript, assistantName)
                        if (remainder.isNotBlank()) {
                            submitUserInput(transcript, fromVoice = true)
                            onFinished()
                        } else {
                            val greet = if (settings.preferredLanguage == LanguageMode.URDU) {
                                "جی ${settings.userDisplayName}، میں سن رہا ہوں! بتائیے؟"
                            } else if (settings.preferredLanguage == LanguageMode.ROMAN_URDU || settings.preferredLanguage == LanguageMode.AUTO) {
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
                                            _isVoiceSessionActive.value = false
                                            _errorBanner.value = errMsg
                                            onFinished()
                                        }
                                    )
                                }
                                Unit
                            }

                            if (settings.ttsAutoSpeak) {
                                voiceEngine.speak(
                                    text = greet,
                                    languageMode = settings.preferredLanguage,
                                    settings = settings,
                                    ttsApiKey = ttsKey,
                                    allowNeural = online && ttsKey.isNotBlank() && settings.neuralTtsProvider != NeuralTtsProvider.DEVICE_BEST,
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
            speakSingleReply(reply, LanguageMode.ENGLISH, settings, continueSession = false)
        }
    }

    fun cancelPendingAction() {
        _pendingConfirmation.value = null
        _lastResponse.value = "No problem, cancelled that."
    }

    fun submitUserInput(
        rawText: String,
        fromVoice: Boolean = false,
        attachment: PreparedAttachment? = null
    ) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty() && attachment == null) return

        val effectivePrompt = if (trimmed.isNotEmpty()) trimmed else "Analyze the attached ${attachment?.displayName ?: "file"}."
        val now = System.currentTimeMillis()
        if (attachment == null && effectivePrompt.equals(lastProcessedText, ignoreCase = true) && (now - lastProcessedAt) < 1200L) {
            return
        }
        lastProcessedText = effectivePrompt
        lastProcessedAt = now

        _errorBanner.value = null
        _lastTranscript.value = if (attachment != null) "$effectivePrompt [${attachment.displayName}]" else effectivePrompt
        _isThinking.value = true
        _streamingResponse.value = ""

        activeAiJob?.cancel()
        activeAiJob = scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            settingsRepository.recordInteraction()

            try {
                // If user attached an image or document, go straight to Online AI Vision/Doc pipeline
                if (attachment != null) {
                    val lang = com.osaf.jarvis.utils.LanguageDetector.detect(effectivePrompt, settings.preferredLanguage)
                    val mood = personalityEngine.detectMood(effectivePrompt)
                    val aiAttachment = AiAttachmentContext(
                        imageBase64Jpeg = attachment.imageBase64Jpeg,
                        documentName = attachment.displayName,
                        documentExcerpt = attachment.documentExcerpt
                    )
                    runOnlineAiPipeline(
                        rawDisplayPrompt = _lastTranscript.value,
                        cleanedPrompt = effectivePrompt,
                        lang = lang,
                        mood = mood,
                        settings = settings,
                        fromVoice = fromVoice,
                        attachment = aiAttachment
                    )
                    return@launch
                }

                when (val outcome = commandInterpreter.interpretAndExecute(effectivePrompt)) {
                    is CommandOutcome.Handled -> {
                        if (settings.saveChatHistory) {
                            chatRepository.addMessage("user", effectivePrompt, outcome.language.code, if (fromVoice) "voice" else "text")
                            chatRepository.addMessage("assistant", outcome.responseText, outcome.language.code, outcome.source)
                        }
                        _pendingConfirmation.value = outcome.confirmation
                        _lastResponse.value = outcome.responseText
                        _isThinking.value = false
                        val shouldContinue = fromVoice && settings.continuousConversation &&
                            !outcome.shouldEndVoiceSession && outcome.confirmation == null
                        if (!shouldContinue) _isVoiceSessionActive.value = false
                        speakSingleReply(outcome.responseText, outcome.language, settings, continueSession = shouldContinue)
                    }

                    is CommandOutcome.NeedsOnlineAi -> {
                        runOnlineAiPipeline(
                            rawDisplayPrompt = effectivePrompt,
                            cleanedPrompt = outcome.cleanedQuery,
                            lang = outcome.language,
                            mood = outcome.mood,
                            settings = settings,
                            fromVoice = fromVoice,
                            attachment = null
                        )
                    }
                }
            } catch (e: Exception) {
                _isThinking.value = false
                _isVoiceSessionActive.value = false
                val msg = e.localizedMessage ?: "Command processing error."
                _errorBanner.value = msg
                _lastResponse.value = msg
            }
        }
    }

    private suspend fun runOnlineAiPipeline(
        rawDisplayPrompt: String,
        cleanedPrompt: String,
        lang: LanguageMode,
        mood: com.osaf.jarvis.ai.UserMood,
        settings: JarvisSettings,
        fromVoice: Boolean,
        attachment: AiAttachmentContext?
    ) {
        if (settings.saveChatHistory) {
            chatRepository.addMessage("user", rawDisplayPrompt, lang.code, if (fromVoice) "voice" else "text")
        }
        val hasNet = deviceController.checkCurrentConnectivity()
        val apiKey = settingsRepository.getDecryptedApiKey()

        val webResult = if (hasNet && settings.preferOnlineMode && attachment == null && webSearchTool.looksLikeWebLookupQuery(cleanedPrompt)) {
            webSearchTool.searchLiveKnowledge(cleanedPrompt)
        } else {
            null
        }

        if (!settings.preferOnlineMode || !hasNet || apiKey.isBlank()) {
            // If user specifically asked a web lookup while online without LLM key, return the real web summary!
            val replyText = if (webResult != null) {
                if (settings.saveChatHistory) {
                    chatRepository.addMessage("assistant", webResult.summary, lang.code, "web")
                }
                webResult.summary
            } else {
                val reason = when {
                    !settings.preferOnlineMode -> "Online Mode is switched off in Settings."
                    !hasNet -> "No internet connection right now."
                    else -> "Add your HTTPS API key in Settings to unlock full AI & Vision."
                }
                val fallback = offlineAiEngine.buildOfflineFallbackResponse(
                    userDisplayName = settings.userDisplayName,
                    assistantName = settings.assistantName,
                    language = lang,
                    reason = reason
                )
                if (settings.saveChatHistory) {
                    chatRepository.addMessage("assistant", fallback, lang.code, "offline")
                }
                fallback
            }
            _lastResponse.value = replyText
            _isThinking.value = false
            val shouldContinue = fromVoice && settings.continuousConversation
            if (!shouldContinue) _isVoiceSessionActive.value = false
            speakSingleReply(replyText, lang, settings, continueSession = shouldContinue)
            return
        }

        val history = if (settings.saveChatHistory) chatRepository.getRecentContext(10) else emptyList()
        val memoryFacts = if (settings.shareMemoryWithOnlineAi) memoryRepository.getFactsSnapshot() else emptyList()
        val notes = if (settings.shareNotesWithOnlineAi) memoryRepository.getNotesSnapshot() else emptyList()
        val buffer = StringBuilder()
        val sentenceChunker = SpeechSentenceChunker()
        val ttsKey = resolveTtsKey(settings, fallbackMainKey = apiKey)
        val allowNeural = hasNet && settings.neuralTtsProvider != NeuralTtsProvider.DEVICE_BEST && ttsKey.isNotBlank()
        var spokeStreamingChunks = false

        openAiClient.streamChatCompletion(
            settings = settings,
            apiKey = apiKey,
            userPrompt = cleanedPrompt,
            history = history,
            detectedLanguage = lang,
            mood = mood,
            memoryFacts = memoryFacts,
            sharedNotes = notes,
            webContext = webResult,
            attachment = attachment
        ).collect { event ->
            when (event) {
                is AiStreamEvent.Delta -> {
                    buffer.append(event.chunk)
                    _streamingResponse.value = buffer.toString()
                    _lastResponse.value = buffer.toString()

                    if (settings.ttsAutoSpeak) {
                        val readySentences = sentenceChunker.appendAndExtract(event.chunk)
                        for (sentence in readySentences) {
                            spokeStreamingChunks = true
                            voiceEngine.enqueueSpeech(
                                text = sentence,
                                languageMode = lang,
                                settings = settings,
                                ttsApiKey = ttsKey,
                                allowNeural = allowNeural
                            )
                        }
                    }
                }

                is AiStreamEvent.Completed -> {
                    _streamingResponse.value = ""
                    _lastResponse.value = event.fullText
                    _isThinking.value = false
                    if (settings.saveChatHistory) {
                        chatRepository.addMessage(
                            "assistant",
                            event.fullText,
                            lang.code,
                            if (webResult != null) "web+ai" else "online"
                        )
                    }

                    val shouldContinue = fromVoice && settings.continuousConversation
                    if (!shouldContinue) _isVoiceSessionActive.value = false

                    if (settings.ttsAutoSpeak) {
                        val tail = sentenceChunker.flushRemaining()
                        if (!spokeStreamingChunks) {
                            speakSingleReply(event.fullText, lang, settings, continueSession = shouldContinue)
                        } else if (!tail.isNullOrBlank()) {
                            voiceEngine.enqueueSpeech(
                                text = tail,
                                languageMode = lang,
                                settings = settings,
                                ttsApiKey = ttsKey,
                                allowNeural = allowNeural,
                                onDone = if (shouldContinue) ({ continueVoiceTurn(settings) }) else null
                            )
                        } else if (shouldContinue) {
                            // Append an empty sentinel callback when queue drains
                            voiceEngine.enqueueSpeech(
                                text = "",
                                languageMode = lang,
                                settings = settings,
                                onDone = { continueVoiceTurn(settings) }
                            )
                        }
                    } else if (shouldContinue) {
                        continueVoiceTurn(settings)
                    }
                }

                is AiStreamEvent.Error -> {
                    sentenceChunker.reset()
                    _streamingResponse.value = ""
                    _isThinking.value = false
                    _isVoiceSessionActive.value = false
                    _errorBanner.value = event.message
                    val safeMsg = "Sorry ${settings.userDisplayName}, ${event.message}"
                    _lastResponse.value = safeMsg
                    if (settings.saveChatHistory) {
                        chatRepository.addMessage("assistant", safeMsg, lang.code, "error")
                    }
                    speakSingleReply(safeMsg, lang, settings, continueSession = false)
                }
            }
        }
    }

    private suspend fun speakSingleReply(
        text: String,
        lang: LanguageMode,
        settings: JarvisSettings,
        continueSession: Boolean
    ) {
        if (!settings.ttsAutoSpeak) {
            if (continueSession) continueVoiceTurn(settings)
            return
        }
        val online = deviceController.checkCurrentConnectivity() && settings.preferOnlineMode
        val ttsKey = resolveTtsKey(settings)
        val allowNeural = online && ttsKey.isNotBlank() && settings.neuralTtsProvider != NeuralTtsProvider.DEVICE_BEST
        voiceEngine.speak(
            text = text,
            languageMode = lang,
            settings = settings,
            ttsApiKey = ttsKey,
            allowNeural = allowNeural,
            onDone = if (continueSession) ({ continueVoiceTurn(settings) }) else null
        )
    }

    private fun continueVoiceTurn(settings: JarvisSettings) {
        if (!_isVoiceSessionActive.value) return
        scope.launch {
            delay(220L)
            if (!_isVoiceSessionActive.value) return@launch
            val online = deviceController.isNetworkConnected.value && settings.preferOnlineMode
            voiceEngine.startListening(
                languageMode = settings.preferredLanguage,
                preferOffline = !online,
                onResult = { followUp ->
                    submitUserInput(followUp, fromVoice = true)
                },
                onError = {
                    _isVoiceSessionActive.value = false
                }
            )
        }
    }

    private suspend fun resolveTtsKey(settings: JarvisSettings, fallbackMainKey: String? = null): String {
        val dedicated = settingsRepository.getDecryptedTtsApiKey()
        if (dedicated.isNotBlank()) return dedicated
        if (settings.neuralTtsProvider == NeuralTtsProvider.OPENAI || settings.neuralTtsProvider == NeuralTtsProvider.CUSTOM_HTTPS) {
            return fallbackMainKey ?: settingsRepository.getDecryptedApiKey()
        }
        return ""
    }
}
