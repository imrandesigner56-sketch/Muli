package com.osaf.jarvis.wakeword

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Lightweight acoustic Voice-Activity & Energy Gate for wake-phrase detection.
 *
 * Properly releases AudioRecord hardware BEFORE invoking SpeechRecognizer so Android's
 * microphone lock never blocks wake-word speech verification, and resumes after completion.
 */
class WakeWordEngine {

    private var monitorJob: Job? = null

    fun matchesWakePhrase(transcript: String, assistantName: String): Boolean {
        val normalized = transcript.lowercase()
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
            "jarvas"
        )
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
            try {
                audioRecord = createAudioRecord(sampleRate, minBuffer)
                if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    onError("Microphone hardware could not initialize for wake-word monitoring.")
                    return@launch
                }
                val activeRecord = audioRecord
                activeRecord.startRecording()
                val buffer = ShortArray(1024)
                var consecutiveVoiceFrames = 0
                var ambientFloor = 140.0
                var frameCount = 0

                while (isActive) {
                    val read = activeRecord.read(buffer, 0, buffer.size)
                    if (read <= 0) continue

                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val s = buffer[i].toDouble()
                        sumSq += s * s
                    }
                    val rms = sqrt(sumSq / read)
                    frameCount++

                    // Track quiet ambient floor smoothly
                    if (frameCount <= 8 || rms < ambientFloor * 1.4) {
                        ambientFloor = (ambientFloor * 0.9) + (rms * 0.1)
                    }

                    // Responsive threshold: sensitivity 0.65 -> ~260 RMS above ambient floor
                    val sensitivity = getSensitivity().coerceIn(0.2f, 0.95f)
                    val baseGate = 650.0 - (sensitivity * 480.0)
                    val threshold = maxOf(baseGate, ambientFloor * (2.6 - sensitivity))

                    if (rms > threshold) {
                        consecutiveVoiceFrames++
                        if (consecutiveVoiceFrames >= 2) {
                            // Release AudioRecord BEFORE invoking SpeechRecognizer!
                            try {
                                activeRecord.stop()
                            } catch (_: Exception) { }
                            try {
                                activeRecord.release()
                            } catch (_: Exception) { }
                            audioRecord = null
                            delay(120L)
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
                try {
                    audioRecord?.stop()
                } catch (_: Exception) { }
                try {
                    audioRecord?.release()
                } catch (_: Exception) { }
            }
        }
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
