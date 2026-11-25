package com.osaf.jarvis.wakeword

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import com.osaf.jarvis.utils.PerformanceMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Low-power acoustic Voice-Activity & Noise-Suppressed Wake Gate.
 *
 * Uses hardware NoiseSuppressor + AcousticEchoCanceler (when available) plus
 * adaptive noise-floor tracking and Zero-Crossing Rate (ZCR) speech band filtering
 * to detect quiet speech at varying distances while minimizing false triggers.
 * Releases AudioRecord hardware cleanly before handing off to SpeechRecognizer.
 */
class WakeWordEngine {

    private var monitorJob: Job? = null

    fun matchesWakePhrase(transcript: String, assistantName: String): Boolean {
        val rawTrimmed = transcript.trim()
        if (rawTrimmed.contains("جاروس")) return true

        val normalized = rawTrimmed.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isEmpty()) return false

        val name = assistantName.lowercase().trim().ifEmpty { "jarvis" }
        val candidates = listOf(
            "hey $name",
            "hi $name",
            "ok $name",
            "a $name",
            "hay $name",
            name,
            "hey jarvis",
            "hi jarvis",
            "ok jarvis",
            "a jarvis",
            "hay jarvis",
            "hey jarvish",
            "hey jarves",
            "hey jarvas",
            "hey charvis",
            "jarvis",
            "jarvish",
            "jarves",
            "jarvas",
            "service" // common quiet-speech STT phonetic confusion when paired with "hey service"
        ).filter { it != "service" || normalized.startsWith("hey service") }

        return candidates.any { phrase ->
            normalized == phrase ||
                normalized.startsWith("$phrase ") ||
                normalized.endsWith(" $phrase") ||
                normalized.contains(" $phrase ")
        }
    }

    @SuppressLint("MissingPermission")
    fun startEnergyMonitoring(
        scope: CoroutineScope,
        getSensitivity: () -> Float,
        onSpeechTrigger: () -> Unit,
        onError: (String) -> Unit
    ) {
        stop()
        monitorJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 16000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            var audioRecord: AudioRecord? = null
            var noiseSuppressor: NoiseSuppressor? = null
            var echoCanceler: AcousticEchoCanceler? = null

            try {
                audioRecord = createAudioRecord(sampleRate, minBuffer)
                if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    onError("Microphone hardware could not initialize for wake-word monitoring.")
                    return@launch
                }

                val activeRecord = audioRecord
                val sessionId = activeRecord.audioSessionId
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = runCatching {
                        NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                    }.getOrNull()
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = runCatching {
                        AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                    }.getOrNull()
                }

                activeRecord.startRecording()
                val buffer = ShortArray(1024)
                var consecutiveVoiceFrames = 0
                var ambientFloor = 110.0
                var frameCount = 0

                while (isActive) {
                    val read = activeRecord.read(buffer, 0, buffer.size)
                    if (read <= 0) continue

                    var sumSq = 0.0
                    var zeroCrossings = 0
                    var prevSample = buffer[0].toInt()
                    for (i in 0 until read) {
                        val s = buffer[i].toInt()
                        sumSq += s.toDouble() * s.toDouble()
                        if ((s >= 0 && prevSample < 0) || (s < 0 && prevSample >= 0)) {
                            zeroCrossings++
                        }
                        prevSample = s
                    }
                    val rms = sqrt(sumSq / read)
                    val zcrRatio = zeroCrossings.toDouble() / read
                    frameCount++

                    // Smoothly adapt to room noise floor during silence
                    if (frameCount <= 10 || rms < ambientFloor * 1.35) {
                        ambientFloor = (ambientFloor * 0.90) + (rms * 0.10)
                    }

                    // Quiet speech sensitivity curve: sensitivity 0.72 -> ~185 RMS above floor
                    val sensitivity = getSensitivity().coerceIn(0.2f, 0.95f)
                    val baseGate = 540.0 - (sensitivity * 430.0)
                    val threshold = maxOf(baseGate, ambientFloor * (2.35 - sensitivity * 0.85))
                    val looksLikeSpeechBand = zcrRatio in 0.015..0.42

                    if (rms > threshold && looksLikeSpeechBand) {
                        consecutiveVoiceFrames++
                        if (consecutiveVoiceFrames >= 2) {
                            val t0 = SystemClock.elapsedRealtime()
                            releaseEffects(noiseSuppressor, echoCanceler)
                            noiseSuppressor = null
                            echoCanceler = null
                            runCatching { activeRecord.stop() }
                            runCatching { activeRecord.release() }
                            audioRecord = null
                            delay(80L)
                            PerformanceMetrics.recordWakeToReady(SystemClock.elapsedRealtime() - t0)
                            onSpeechTrigger()
                            return@launch
                        }
                    } else {
                        consecutiveVoiceFrames = 0
                    }
                }
            } catch (_: SecurityException) {
                onError("Microphone permission denied for wake-word service.")
            } catch (e: Exception) {
                onError("Wake-word monitor stopped: ${e.localizedMessage ?: "audio error"}")
            } finally {
                releaseEffects(noiseSuppressor, echoCanceler)
                runCatching { audioRecord?.stop() }
                runCatching { audioRecord?.release() }
            }
        }
    }

    private fun releaseEffects(ns: NoiseSuppressor?, aec: AcousticEchoCanceler?) {
        runCatching { ns?.release() }
        runCatching { aec?.release() }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(sampleRate: Int, minBuffer: Int): AudioRecord? {
        val sources = listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        for (src in sources) {
            try {
                val rec = AudioRecord(
                    src,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer
                )
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    return rec
                }
                rec.release()
            } catch (_: Exception) {
                // Try next audio source
            }
        }
        return null
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }
}
