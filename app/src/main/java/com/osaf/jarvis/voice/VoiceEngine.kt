package com.osaf.jarvis.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.content.ContextCompat
import com.osaf.jarvis.database.JarvisSettings
import com.osaf.jarvis.database.NeuralTtsProvider
import com.osaf.jarvis.utils.LanguageMode
import com.osaf.jarvis.utils.PerformanceMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.sqrt

data class TtsVoiceOption(
    val name: String,
    val localeLabel: String,
    val isOfflineCapable: Boolean
)

private data class QueuedSpeechItem(
    val text: String,
    val languageMode: LanguageMode,
    val settings: JarvisSettings,
    val ttsApiKey: String,
    val allowNeural: Boolean,
    val startedAtMs: Long,
    val onDone: (() -> Unit)?
)

class VoiceEngine(
    private val context: Context,
    httpClient: OkHttpClient = OkHttpClient()
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val neuralTtsClient = NeuralTtsClient(httpClient)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val speechQueue = ArrayDeque<QueuedSpeechItem>()
    private var activeSpeechItem: QueuedSpeechItem? = null
    private var activeDeviceUtteranceId: String? = null
    private var activeUtteranceJob: Job? = null
    private var bargeInJob: Job? = null
    private var activeListenSessionId = 0L

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _activeVoiceSource = MutableStateFlow("Device TTS")
    val activeVoiceSource: StateFlow<String> = _activeVoiceSource.asStateFlow()

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
    private var onBargeInInterruptCallback: (() -> Unit)? = null

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
                            mainHandler.post {
                                if (activeDeviceUtteranceId == utteranceId) {
                                    _isSpeaking.value = true
                                    activeSpeechItem?.let { item ->
                                        PerformanceMetrics.recordTtsFirstAudio(
                                            SystemClock.elapsedRealtime() - item.startedAtMs
                                        )
                                    }
                                }
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            mainHandler.post {
                                if (activeDeviceUtteranceId == utteranceId) {
                                    activeDeviceUtteranceId = null
                                    onCurrentUtteranceFinished()
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            mainHandler.post {
                                if (activeDeviceUtteranceId == utteranceId) {
                                    activeDeviceUtteranceId = null
                                    onCurrentUtteranceFinished()
                                }
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            mainHandler.post {
                                if (activeDeviceUtteranceId == utteranceId) {
                                    activeDeviceUtteranceId = null
                                    onCurrentUtteranceFinished()
                                }
                            }
                        }
                    })
                    engineScope.launch(Dispatchers.Default) { refreshVoices() }
                    mainHandler.post {
                        if (activeSpeechItem == null && speechQueue.isNotEmpty()) {
                            processNextQueuedSpeech()
                        }
                    }
                } else {
                    ttsReady = false
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
                .sortedWith(
                    compareByDescending<Voice> { it.locale.country.equals("PK", ignoreCase = true) }
                        .thenByDescending { it.quality }
                        .thenBy { it.isNetworkConnectionRequired }
                        .thenBy { it.name }
                )
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

    fun setBargeInHandler(handler: (() -> Unit)?) {
        onBargeInInterruptCallback = handler
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
            onError("Speech recognition service is not available on this device. Please type below.")
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

            mainHandler.postDelayed({
                if (activeListenSessionId != sessionId) return@postDelayed
                launchRecognizerSession(
                    sessionId = sessionId,
                    localeTag = primaryLocale,
                    preferOffline = preferOffline,
                    allowFallbackRetry = true
                )
            }, 85L)
        }
    }

    private fun launchRecognizerSession(
        sessionId: Long,
        localeTag: String,
        preferOffline: Boolean,
        allowFallbackRetry: Boolean
    ) {
        if (activeListenSessionId != sessionId) return
        destroyRecognizer()

        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        } catch (e: Exception) {
            completeWithError(sessionId, "Could not start speech engine: ${e.localizedMessage ?: "error"}")
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

                val canRetry = allowFallbackRetry && error in setOf(
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT,
                    12, // ERROR_LANGUAGE_NOT_SUPPORTED
                    13  // ERROR_LANGUAGE_UNAVAILABLE
                )
                if (canRetry) {
                    destroyRecognizer()
                    mainHandler.postDelayed({
                        if (activeListenSessionId == sessionId) {
                            launchRecognizerSession(
                                sessionId = sessionId,
                                localeTag = Locale.getDefault().toLanguageTag().ifBlank { "en-US" },
                                preferOffline = false,
                                allowFallbackRetry = false
                            )
                        }
                    }, 180L)
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
                    completeWithError(sessionId, "Did not catch that — tap the mic and try again.")
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
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1300L)
            if (preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
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
            runCatching { speechRecognizer?.cancel() }
            destroyRecognizer()
            _isListening.value = false
            _rmsLevel.value = 0f
            _partialTranscript.value = ""
            currentResultCallback = null
            currentErrorCallback = null
        }
    }

    /**
     * Flushes any existing speech queue and starts speaking immediately.
     */
    fun speak(
        text: String,
        languageMode: LanguageMode,
        settings: JarvisSettings,
        ttsApiKey: String = "",
        allowNeural: Boolean = false,
        onDone: (() -> Unit)? = null
    ) {
        mainHandler.post {
            stopSpeakingInternal()
            enqueueSpeechInternal(text, languageMode, settings, ttsApiKey, allowNeural, onDone)
        }
    }

    /**
     * Appends a sentence to the active speech queue (for streaming AI sentence-by-sentence speech).
     */
    fun enqueueSpeech(
        text: String,
        languageMode: LanguageMode,
        settings: JarvisSettings,
        ttsApiKey: String = "",
        allowNeural: Boolean = false,
        onDone: (() -> Unit)? = null
    ) {
        mainHandler.post {
            enqueueSpeechInternal(text, languageMode, settings, ttsApiKey, allowNeural, onDone)
        }
    }

    private fun enqueueSpeechInternal(
        text: String,
        languageMode: LanguageMode,
        settings: JarvisSettings,
        ttsApiKey: String,
        allowNeural: Boolean,
        onDone: (() -> Unit)?
    ) {
        val cleaned = SpeechSentenceChunker.cleanForSpeech(text)
        if (cleaned.isEmpty()) {
            onDone?.invoke()
            return
        }

        speechQueue.addLast(
            QueuedSpeechItem(
                text = cleaned,
                languageMode = languageMode,
                settings = settings,
                ttsApiKey = ttsApiKey,
                allowNeural = allowNeural,
                startedAtMs = SystemClock.elapsedRealtime(),
                onDone = onDone
            )
        )

        if (activeSpeechItem == null) {
            processNextQueuedSpeech()
        }
    }

    private fun processNextQueuedSpeech() {
        if (speechQueue.isEmpty()) {
            activeSpeechItem = null
            _isSpeaking.value = false
            _rmsLevel.value = 0f
            stopBargeInMonitor()
            abandonAudioFocus()
            return
        }

        val item = speechQueue.removeFirst()
        activeSpeechItem = item
        requestAudioFocus()
        startBargeInMonitorIfEligible(item.settings)

        val shouldTryNeural = item.allowNeural &&
            item.settings.neuralTtsProvider != NeuralTtsProvider.DEVICE_BEST &&
            item.ttsApiKey.isNotBlank()

        activeUtteranceJob?.cancel()
        activeUtteranceJob = engineScope.launch {
            if (shouldTryNeural) {
                when (
                    val res = neuralTtsClient.synthesizeToCacheFile(
                        cacheDir = context.cacheDir,
                        text = item.text,
                        languageMode = item.languageMode,
                        settings = item.settings,
                        apiKey = item.ttsApiKey
                    )
                ) {
                    is NeuralTtsResult.AudioFile -> {
                        if (activeSpeechItem !== item) {
                            res.file.delete()
                            return@launch
                        }
                        _activeVoiceSource.value = item.settings.neuralTtsProvider.displayName
                        playAudioFile(res.file, item)
                        return@launch
                    }

                    is NeuralTtsResult.Unavailable -> {
                        // Graceful fallback to best device TTS
                    }
                }
            }

            if (activeSpeechItem !== item) return@launch
            _activeVoiceSource.value = "Best Device TTS"
            speakWithDeviceTts(item)
        }
    }

    private fun playAudioFile(file: File, item: QueuedSpeechItem) {
        try {
            releaseMediaPlayer()
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                val vol = item.settings.voiceVolume.coerceIn(0.1f, 1.0f)
                setVolume(vol, vol)
                setOnPreparedListener { mp ->
                    if (activeSpeechItem !== item) {
                        runCatching { mp.release() }
                        file.delete()
                        return@setOnPreparedListener
                    }
                    _isSpeaking.value = true
                    PerformanceMetrics.recordTtsFirstAudio(
                        SystemClock.elapsedRealtime() - item.startedAtMs
                    )
                    mp.start()
                }
                setOnCompletionListener { mp ->
                    runCatching { mp.release() }
                    if (mediaPlayer === mp) mediaPlayer = null
                    file.delete()
                    if (activeSpeechItem === item) {
                        onCurrentUtteranceFinished()
                    }
                }
                setOnErrorListener { mp, _, _ ->
                    runCatching { mp.release() }
                    if (mediaPlayer === mp) mediaPlayer = null
                    file.delete()
                    if (activeSpeechItem === item) {
                        speakWithDeviceTts(item)
                    }
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (_: Exception) {
            file.delete()
            speakWithDeviceTts(item)
        }
    }

    private fun speakWithDeviceTts(item: QueuedSpeechItem) {
        val engine = tts
        if (!ttsReady || engine == null) {
            if (tts == null) initTts()
            mainHandler.postDelayed({
                if (activeSpeechItem === item) {
                    onCurrentUtteranceFinished()
                }
            }, 350L)
            return
        }

        try {
            engine.setSpeechRate(item.settings.speechSpeed.coerceIn(0.5f, 2.0f))
            engine.setPitch(item.settings.speechPitch.coerceIn(0.5f, 2.0f))

            val targetLocale = when (item.languageMode) {
                LanguageMode.URDU -> Locale("ur", "PK")
                else -> Locale.US
            }
            val langResult = engine.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                val usRes = engine.setLanguage(Locale.US)
                if (usRes == TextToSpeech.LANG_MISSING_DATA || usRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.getDefault())
                }
            }

            if (item.settings.selectedVoiceName.isNotBlank() && item.languageMode != LanguageMode.URDU) {
                engine.voices?.firstOrNull { it.name == item.settings.selectedVoiceName }?.let { v ->
                    engine.voice = v
                }
            } else {
                // Auto-pick highest quality offline/network voice for target language
                val desiredLang = if (item.languageMode == LanguageMode.URDU) "ur" else "en"
                val bestVoice = engine.voices
                    ?.filter { it.locale.language == desiredLang }
                    ?.maxByOrNull { v ->
                        val pkBonus = if (v.locale.country.equals("PK", true)) 400 else 0
                        val offlineBonus = if (!v.isNetworkConnectionRequired) 100 else 0
                        v.quality + pkBonus + offlineBonus
                    }
                if (bestVoice != null) {
                    engine.voice = bestVoice
                }
            }

            val utteranceId = UUID.randomUUID().toString()
            activeDeviceUtteranceId = utteranceId

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, item.settings.voiceVolume.coerceIn(0.1f, 1.0f))
            }

            val status = engine.speak(item.text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (status == TextToSpeech.ERROR) {
                activeDeviceUtteranceId = null
                onCurrentUtteranceFinished()
            } else {
                _isSpeaking.value = true
                // Safety guard in case an OEM TTS engine drops onDone callback
                val safetyMs = ((item.text.length / 8.0) * 1000.0 / item.settings.speechSpeed.coerceIn(0.6f, 1.8f))
                    .toLong()
                    .coerceIn(2500L, 18000L)
                mainHandler.postDelayed({
                    if (activeDeviceUtteranceId == utteranceId) {
                        activeDeviceUtteranceId = null
                        onCurrentUtteranceFinished()
                    }
                }, safetyMs)
            }
        } catch (_: Exception) {
            activeDeviceUtteranceId = null
            onCurrentUtteranceFinished()
        }
    }

    private fun onCurrentUtteranceFinished() {
        val finished = activeSpeechItem
        activeSpeechItem = null
        finished?.onDone?.invoke()
        processNextQueuedSpeech()
    }

    @SuppressLint("MissingPermission")
    private fun startBargeInMonitorIfEligible(settings: JarvisSettings) {
        if (bargeInJob != null) return
        if (!settings.bargeInEnabled || !hasRecordPermission() || onBargeInInterruptCallback == null) return
        if (!AcousticEchoCanceler.isAvailable()) return

        bargeInJob = engineScope.launch(Dispatchers.Default) {
            val sampleRate = 16000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            var rec: AudioRecord? = null
            var aec: AcousticEchoCanceler? = null
            var ns: NoiseSuppressor? = null

            try {
                rec = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer
                )
                val activeRec = rec
                if (activeRec.state != AudioRecord.STATE_INITIALIZED) {
                    activeRec.release()
                    rec = null
                    return@launch
                }
                val sid = activeRec.audioSessionId
                aec = runCatching { AcousticEchoCanceler.create(sid)?.apply { enabled = true } }.getOrNull()
                if (NoiseSuppressor.isAvailable()) {
                    ns = runCatching { NoiseSuppressor.create(sid)?.apply { enabled = true } }.getOrNull()
                }
                activeRec.startRecording()
                val buf = ShortArray(1024)
                var loudVoiceFrames = 0
                // Skip initial 350ms to ignore startup pop
                val ignoreUntil = SystemClock.elapsedRealtime() + 350L

                while (isActive && _isSpeaking.value) {
                    val read = activeRec.read(buf, 0, buf.size)
                    if (read <= 0) continue
                    if (SystemClock.elapsedRealtime() < ignoreUntil) continue

                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val s = buf[i].toDouble()
                        sumSq += s * s
                    }
                    val rms = sqrt(sumSq / read)
                    if (rms > 950.0) {
                        loudVoiceFrames++
                        if (loudVoiceFrames >= 3) {
                            runCatching { activeRec.stop() }
                            runCatching { activeRec.release() }
                            rec = null
                            mainHandler.post {
                                if (_isSpeaking.value) {
                                    stopSpeakingInternal()
                                    onBargeInInterruptCallback?.invoke()
                                }
                            }
                            return@launch
                        }
                    } else {
                        loudVoiceFrames = 0
                    }
                }
            } catch (_: Exception) {
                // Ignore if device denies simultaneous voice communication recording
            } finally {
                runCatching { aec?.release() }
                runCatching { ns?.release() }
                runCatching { rec?.stop() }
                runCatching { rec?.release() }
            }
        }
    }

    private fun stopBargeInMonitor() {
        bargeInJob?.cancel()
        bargeInJob = null
    }

    fun stopSpeaking() {
        mainHandler.post {
            stopSpeakingInternal()
        }
    }

    private fun stopSpeakingInternal() {
        speechQueue.clear()
        activeSpeechItem = null
        activeDeviceUtteranceId = null
        activeUtteranceJob?.cancel()
        activeUtteranceJob = null
        stopBargeInMonitor()
        releaseMediaPlayer()
        runCatching { tts?.stop() }
        abandonAudioFocus()
        _isSpeaking.value = false
        _rmsLevel.value = 0f
    }

    private fun releaseMediaPlayer() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (audioFocusRequest == null) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .build()
        }
        audioFocusRequest?.let { runCatching { am.requestAudioFocus(it) } }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        audioFocusRequest?.let { runCatching { am.abandonAudioFocusRequest(it) } }
    }

    private fun destroyRecognizer() {
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
    }

    private fun playTone(toneType: Int) {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 36)
            tg.startTone(toneType, 90)
            mainHandler.postDelayed({ runCatching { tg.release() } }, 160)
        }
    }

    private fun mapRecognizerError(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error. Please try again."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Voice recognition network unavailable right now. You can type below!"
            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't quite catch that — please tap the mic and speak again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice engine was busy — tap the mic to try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't hear anything — tap the mic whenever you're ready."
            else -> "Voice input hit a temporary issue (code $errorCode). Please try again."
        }
    }

    fun release() {
        cancelListening()
        mainHandler.post {
            stopSpeakingInternal()
            destroyRecognizer()
            runCatching { tts?.shutdown() }
            tts = null
            ttsReady = false
            engineScope.cancel()
        }
    }
}
