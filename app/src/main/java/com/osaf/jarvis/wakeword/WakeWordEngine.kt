package com.osaf.jarvis.wakeword

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Lightweight acoustic Voice-Activity & Energy Gate for wake-phrase detection.
 *
 * Uses a minimal 16kHz PCM ring buffer (low RAM footprint for 2GB-3GB devices) to detect speech
 * bursts above the user's configured sensitivity threshold before invoking SpeechRecognizer
 * to verify "Hey JARVIS" / "JARVIS". Never runs unless explicitly enabled by the user.
 */
class WakeWordEngine {

    private var monitorJob: Job? = null
    @Volatile
    private var paused = false

    fun matchesWakePhrase(transcript: String, assistantName: String): Boolean {
        val lower = transcript.lowercase().trim()
        val name = assistantName.lowercase().trim().ifEmpty { "jarvis" }
        val candidates = listOf(
            "hey $name",
            "ok $name",
            name,
            "hey jarvis",
            "jarvis"
        )
        return candidates.any { lower.contains(it) }
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
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer
                )
                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    onError("Microphone hardware could not initialize for wake-word monitoring.")
                    return@launch
                }
                audioRecord.startRecording()
                val buffer = ShortArray(1024)
                var consecutiveVoiceFrames = 0
                var cooldownUntil = 0L

                while (isActive) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read <= 0 || paused) continue

                    val now = System.currentTimeMillis()
                    if (now < cooldownUntil) continue

                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val s = buffer[i].toDouble()
                        sumSq += s * s
                    }
                    val rms = sqrt(sumSq / read)
                    // Higher sensitivity -> lower threshold
                    val sensitivity = getSensitivity().coerceIn(0.2f, 0.95f)
                    val threshold = 2400.0 - (sensitivity * 1900.0)

                    if (rms > threshold) {
                        consecutiveVoiceFrames++
                        if (consecutiveVoiceFrames >= 3) {
                            consecutiveVoiceFrames = 0
                            cooldownUntil = now + 4500L
                            onSpeechTrigger()
                        }
                    } else {
                        consecutiveVoiceFrames = 0
                    }
                }
            } catch (e: SecurityException) {
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

    fun setPaused(isPaused: Boolean) {
        paused = isPaused
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }
}
