package com.osaf.jarvis.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.content.ContextCompat
import com.osaf.jarvis.utils.LanguageMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TtsVoiceOption(
    val name: String,
    val localeLabel: String,
    val isOfflineCapable: Boolean
)

private data class PendingSpeech(
    val text: String,
    val languageMode: LanguageMode,
    val speechRate: Float,
    val speechPitch: Float,
    val selectedVoiceName: String,
    val onDone: (() -> Unit)?
)

class VoiceEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: PendingSpeech? = null
    private val utteranceCallbacks = ConcurrentHashMap<String, () -> Unit>()
    private var activeListenSessionId = 0L

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceOption>> = _availableVoices.asStateFlow()

    private val _speechRecognitionAvailable = MutableStateFlow(
        SpeechRecognizer.isRecognitionAvailable(context)
    )
    val speechRecognitionAvailable: StateFlow<Boolean> = _speechRecognitionAvailable.asStateFlow()

    private var currentResultCallback: ((String) -> Unit)? = null
    private var currentErrorCallback: ((String) -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            _rmsLevel.value = 0f
                            utteranceId?.let { id ->
                                utteranceCallbacks.remove(id)?.let { cb ->
                                    mainHandler.post(cb)
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            _rmsLevel.value = 0f
                            utteranceId?.let { id ->
                                utteranceCallbacks.remove(id)?.let { cb ->
                                    mainHandler.post(cb)
                                }
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _isSpeaking.value = false
                            _rmsLevel.value = 0f
                            utteranceId?.let { id ->
                                utteranceCallbacks.remove(id)?.let { cb ->
                                    mainHandler.post(cb)
                                }
                            }
                        }
                    })
                    refreshVoices()
                    pendingSpeech?.let { pending ->
                        pendingSpeech = null
                        speakInternal(pending)
                    }
                } else {
                    ttsReady = false
                    pendingSpeech?.onDone?.let { cb -> mainHandler.post(cb) }
                    pendingSpeech = null
                }
            }
        } catch (_: Exception) {
            ttsReady = false
        }
    }

    fun refreshVoices() {
        val engine = tts ?: return
        try {
            val voices: Set<Voice> = engine.voices ?: emptySet()
            val mapped = voices
                .filter { it.locale.language in listOf("en", "ur") }
                .sortedWith(compareBy({ it.locale.language }, { it.name }))
                .take(24)
                .map { v ->
                    TtsVoiceOption(
                        name = v.name,
                        localeLabel = "${v.locale.displayLanguage} (${v.locale.country.ifEmpty { "Global" }})",
                        isOfflineCapable = !v.isNetworkConnectionRequired
                    )
                }
            _availableVoices.value = mapped
        } catch (_: Exception) {
            _availableVoices.value = emptyList()
        }
    }

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startListening(
        languageMode: LanguageMode,
        preferOffline: Boolean,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!hasRecordPermission()) {
            onError("Microphone permission is required for voice input.")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _speechRecognitionAvailable.value = false
            onError("Speech recognition service is not installed on this device. Use text input instead.")
            return
        }

        mainHandler.post {
            stopSpeakingInternal()
            destroyRecognizer()

            activeListenSessionId += 1L
            val sessionId = activeListenSessionId

            currentResultCallback = onResult
            currentErrorCallback = onError
            _partialTranscript.value = ""
            _isListening.value = true
            playTone(ToneGenerator.TONE_PROP_BEEP)

            val primaryLocale = when (languageMode) {
                LanguageMode.URDU -> "ur-PK"
                else -> "en-US"
            }

            // Small delay allows TTS & previous SpeechRecognizer audio session to release cleanly
            mainHandler.postDelayed({
                if (activeListenSessionId != sessionId) return@postDelayed
                launchRecognizerSession(
                    sessionId = sessionId,
                    localeTag = primaryLocale,
                    allowFallbackRetry = true
                )
            }, 90L)
        }
    }

    private fun launchRecognizerSession(
        sessionId: Long,
        localeTag: String,
        allowFallbackRetry: Boolean
    ) {
        if (activeListenSessionId != sessionId) return
        destroyRecognizer()

        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        } catch (e: Exception) {
            completeWithError(sessionId, "Could not initialize speech engine: ${e.localizedMessage ?: "error"}")
            return
        }
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (activeListenSessionId == sessionId) {
                    _isListening.value = true
                }
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                if (activeListenSessionId == sessionId) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _rmsLevel.value = normalized
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (activeListenSessionId == sessionId) {
                    _rmsLevel.value = 0f
                }
            }

            override fun onError(error: Int) {
                if (activeListenSessionId != sessionId) return

                val partial = _partialTranscript.value.trim()
                if (partial.isNotEmpty() && error in setOf(
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                    )
                ) {
                    completeWithResult(sessionId, partial)
                    return
                }

                // Retry once with universal English/default locale if OEM recognizer rejected locale/busy state
                val canRetry = allowFallbackRetry && error in setOf(
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT,
                    12, // ERROR_LANGUAGE_NOT_SUPPORTED (API 31+)
                    13  // ERROR_LANGUAGE_UNAVAILABLE (API 31+)
                )
                if (canRetry) {
                    destroyRecognizer()
                    mainHandler.postDelayed({
                        if (activeListenSessionId == sessionId) {
                            launchRecognizerSession(
                                sessionId = sessionId,
                                localeTag = Locale.getDefault().toLanguageTag().ifBlank { "en-US" },
                                allowFallbackRetry = false
                            )
                        }
                    }, 220L)
                    return
                }

                completeWithError(sessionId, mapRecognizerError(error))
            }

            override fun onResults(results: Bundle?) {
                if (activeListenSessionId != sessionId) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches
                    ?.firstOrNull { !it.isNullOrBlank() }
                    ?.trim()
                    ?.ifEmpty { _partialTranscript.value.trim() }
                    ?: _partialTranscript.value.trim()

                if (best.isNotEmpty()) {
                    completeWithResult(sessionId, best)
                } else {
                    completeWithError(sessionId, "Did not catch that — please tap the mic and try again.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (activeListenSessionId != sessionId) return
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotBlank()) {
                    _partialTranscript.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
        }

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            completeWithError(sessionId, "Could not start speech recognition: ${e.localizedMessage ?: "error"}")
        }
    }

    private fun completeWithResult(sessionId: Long, text: String) {
        if (activeListenSessionId != sessionId) return
        activeListenSessionId += 1L
        _isListening.value = false
        _rmsLevel.value = 0f
        _partialTranscript.value = ""
        val cb = currentResultCallback
        currentResultCallback = null
        currentErrorCallback = null
        destroyRecognizer()
        playTone(ToneGenerator.TONE_PROP_ACK)
        cb?.invoke(text)
    }

    private fun completeWithError(sessionId: Long, message: String) {
        if (activeListenSessionId != sessionId) return
        activeListenSessionId += 1L
        _isListening.value = false
        _rmsLevel.value = 0f
        _partialTranscript.value = ""
        val cb = currentErrorCallback
        currentResultCallback = null
        currentErrorCallback = null
        destroyRecognizer()
        cb?.invoke(message)
    }

    fun cancelListening() {
        mainHandler.post {
            activeListenSessionId += 1L
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) { }
            destroyRecognizer()
            _isListening.value = false
            _rmsLevel.value = 0f
            _partialTranscript.value = ""
            currentResultCallback = null
            currentErrorCallback = null
        }
    }

    fun speak(
        text: String,
        languageMode: LanguageMode,
        speechRate: Float,
        speechPitch: Float,
        selectedVoiceName: String
    ) {
        speakAndThen(
            text = text,
            languageMode = languageMode,
            speechRate = speechRate,
            speechPitch = speechPitch,
            selectedVoiceName = selectedVoiceName,
            onDone = null
        )
    }

    fun speakAndThen(
        text: String,
        languageMode: LanguageMode,
        speechRate: Float,
        speechPitch: Float,
        selectedVoiceName: String,
        onDone: (() -> Unit)?
    ) {
        if (text.isBlank()) {
            onDone?.let { mainHandler.post(it) }
            return
        }
        val request = PendingSpeech(
            text = text,
            languageMode = languageMode,
            speechRate = speechRate,
            speechPitch = speechPitch,
            selectedVoiceName = selectedVoiceName,
            onDone = onDone
        )
        mainHandler.post {
            if (!ttsReady || tts == null) {
                pendingSpeech = request
                if (tts == null) initTts()
                // Safety fallback so callers waiting on TTS completion never stall indefinitely
                if (onDone != null) {
                    mainHandler.postDelayed({
                        if (pendingSpeech === request) {
                            pendingSpeech = null
                            onDone()
                        }
                    }, 2000L)
                }
                return@post
            }
            speakInternal(request)
        }
    }

    private fun speakInternal(request: PendingSpeech) {
        val engine = tts
        if (!ttsReady || engine == null) {
            request.onDone?.invoke()
            return
        }
        try {
            engine.setSpeechRate(request.speechRate.coerceIn(0.5f, 2.0f))
            engine.setPitch(request.speechPitch.coerceIn(0.5f, 2.0f))

            val targetLocale = when (request.languageMode) {
                LanguageMode.URDU -> Locale("ur", "PK")
                else -> Locale.US
            }
            val res = engine.setLanguage(targetLocale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                val usRes = engine.setLanguage(Locale.US)
                if (usRes == TextToSpeech.LANG_MISSING_DATA || usRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.getDefault())
                }
            }

            if (request.selectedVoiceName.isNotBlank() && request.languageMode != LanguageMode.URDU) {
                engine.voices?.firstOrNull { it.name == request.selectedVoiceName }?.let { v ->
                    engine.voice = v
                }
            }

            val cleanSpeech = request.text
                .replace(Regex("[*_`#•]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleanSpeech.isEmpty()) {
                request.onDone?.invoke()
                return
            }

            val utteranceId = UUID.randomUUID().toString()
            if (request.onDone != null) {
                utteranceCallbacks[utteranceId] = request.onDone
                val estimatedMs = ((cleanSpeech.length / 10.0) * 1000.0 / request.speechRate.coerceIn(0.6f, 1.8f))
                    .toLong()
                    .coerceIn(1800L, 12000L)
                mainHandler.postDelayed({
                    utteranceCallbacks.remove(utteranceId)?.let { cb ->
                        _isSpeaking.value = false
                        cb()
                    }
                }, estimatedMs)
            }

            val status = engine.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (status == TextToSpeech.ERROR) {
                _isSpeaking.value = false
                utteranceCallbacks.remove(utteranceId)?.invoke()
            } else {
                _isSpeaking.value = true
            }
        } catch (_: Exception) {
            _isSpeaking.value = false
            request.onDone?.invoke()
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            stopSpeakingInternal()
        }
    }

    private fun stopSpeakingInternal() {
        pendingSpeech = null
        utteranceCallbacks.clear()
        try {
            tts?.stop()
        } catch (_: Exception) { }
        _isSpeaking.value = false
        _rmsLevel.value = 0f
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) { }
        speechRecognizer = null
    }

    private fun playTone(toneType: Int) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 40)
            tg.startTone(toneType, 100)
            mainHandler.postDelayed({ tg.release() }, 180)
        } catch (_: Exception) {
            // Ignore on devices without audio output
        }
    }

    private fun mapRecognizerError(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error. Please try again."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Voice recognition network unavailable right now. You can also type below!"
            SpeechRecognizer.ERROR_NO_MATCH -> "I heard you, but didn't catch the words clearly. Please try again!"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice engine was busy — tap the mic to try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't hear anything — tap the mic when you're ready."
            else -> "Voice input had a hiccup (code $errorCode). Please try again."
        }
    }

    fun release() {
        cancelListening()
        mainHandler.post {
            destroyRecognizer()
            utteranceCallbacks.clear()
            pendingSpeech = null
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (_: Exception) { }
            tts = null
            ttsReady = false
        }
    }
}
