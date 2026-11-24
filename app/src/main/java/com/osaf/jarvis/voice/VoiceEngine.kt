package com.osaf.jarvis.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

data class TtsVoiceOption(
    val name: String,
    val localeLabel: String,
    val isOfflineCapable: Boolean
)

class VoiceEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

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
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            _rmsLevel.value = 0f
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            _rmsLevel.value = 0f
                        }
                    })
                    refreshVoices()
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
            stopSpeaking()
            destroyRecognizer()

            currentResultCallback = onResult
            currentErrorCallback = onError
            _partialTranscript.value = ""
            _isListening.value = true
            playTone(ToneGenerator.TONE_PROP_BEEP)

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _rmsLevel.value = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _rmsLevel.value = 0f
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                    val msg = mapRecognizerError(error)
                    currentErrorCallback?.invoke(msg)
                    currentResultCallback = null
                    currentErrorCallback = null
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()?.trim().orEmpty()
                    if (best.isNotEmpty()) {
                        playTone(ToneGenerator.TONE_PROP_ACK)
                        currentResultCallback?.invoke(best)
                    } else {
                        currentErrorCallback?.invoke("No speech detected.")
                    }
                    currentResultCallback = null
                    currentErrorCallback = null
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _partialTranscript.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val localeTag = when (languageMode) {
                LanguageMode.URDU -> "ur-PK"
                LanguageMode.ROMAN_URDU -> "en-PK"
                else -> "en-US"
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                if (preferOffline) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }
            try {
                recognizer.startListening(intent)
            } catch (e: Exception) {
                _isListening.value = false
                onError("Could not start speech recognition: ${e.localizedMessage ?: "error"}")
            }
        }
    }

    fun cancelListening() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) { }
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
        val engine = tts
        if (!ttsReady || engine == null || text.isBlank()) return

        mainHandler.post {
            try {
                engine.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
                engine.setPitch(speechPitch.coerceIn(0.5f, 2.0f))

                val targetLocale = when (languageMode) {
                    LanguageMode.URDU -> Locale("ur", "PK")
                    else -> Locale.US
                }
                val res = engine.setLanguage(targetLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.US)
                }

                if (selectedVoiceName.isNotBlank() && languageMode != LanguageMode.URDU) {
                    engine.voices?.firstOrNull { it.name == selectedVoiceName }?.let { v ->
                        engine.voice = v
                    }
                }

                val cleanSpeech = text.replace(Regex("[*_`#•]"), " ").trim()
                engine.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
            } catch (_: Exception) {
                _isSpeaking.value = false
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            try {
                tts?.stop()
            } catch (_: Exception) { }
            _isSpeaking.value = false
            _rmsLevel.value = 0f
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) { }
        speechRecognizer = null
    }

    private fun playTone(toneType: Int) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45)
            tg.startTone(toneType, 110)
            mainHandler.postDelayed({ tg.release() }, 180)
        } catch (_: Exception) {
            // Ignore on devices without audio output
        }
    }

    private fun mapRecognizerError(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Speech recognition network unavailable. Try Offline voice pack or type your command."
            SpeechRecognizer.ERROR_NO_MATCH -> "Did not catch that. Please try speaking again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech engine is busy."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out with no speech."
            else -> "Speech recognition unavailable (code $errorCode)."
        }
    }

    fun release() {
        cancelListening()
        mainHandler.post {
            destroyRecognizer()
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (_: Exception) { }
            tts = null
            ttsReady = false
        }
    }
}
