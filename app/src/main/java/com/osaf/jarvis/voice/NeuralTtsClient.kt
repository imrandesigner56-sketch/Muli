package com.osaf.jarvis.voice

import com.osaf.jarvis.database.JarvisSettings
import com.osaf.jarvis.database.NeuralTtsProvider
import com.osaf.jarvis.security.UrlValidationResult
import com.osaf.jarvis.security.UrlValidator
import com.osaf.jarvis.utils.LanguageMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID

sealed class NeuralTtsResult {
    data class AudioFile(val file: File) : NeuralTtsResult()
    data class Unavailable(val reason: String) : NeuralTtsResult()
}

class NeuralTtsClient(private val httpClient: OkHttpClient) {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun synthesizeToCacheFile(
        cacheDir: File,
        text: String,
        languageMode: LanguageMode,
        settings: JarvisSettings,
        apiKey: String
    ): NeuralTtsResult = withContext(Dispatchers.IO) {
        if (settings.neuralTtsProvider == NeuralTtsProvider.DEVICE_BEST) {
            return@withContext NeuralTtsResult.Unavailable("Device TTS selected.")
        }
        if (apiKey.isBlank()) {
            return@withContext NeuralTtsResult.Unavailable("No API key available for Neural TTS.")
        }

        val cleanText = SpeechSentenceChunker.cleanForSpeech(text)
        if (cleanText.isBlank()) {
            return@withContext NeuralTtsResult.Unavailable("Empty text.")
        }

        val rawEndpoint = settings.neuralTtsEndpoint.ifBlank { settings.neuralTtsProvider.defaultEndpoint }
        val targetUrl = if (settings.neuralTtsProvider == NeuralTtsProvider.ELEVENLABS) {
            val voiceId = settings.neuralTtsVoiceId.ifBlank { settings.neuralTtsProvider.defaultVoice }
            val trimmedBase = rawEndpoint.trimEnd('/')
            if (trimmedBase.contains(voiceId)) trimmedBase else "$trimmedBase/$voiceId/stream"
        } else {
            rawEndpoint
        }

        val urlValidation = UrlValidator.validateHttpsEndpoint(targetUrl)
        if (urlValidation is UrlValidationResult.Invalid) {
            return@withContext NeuralTtsResult.Unavailable(urlValidation.reason)
        }
        val validUrl = (urlValidation as UrlValidationResult.Valid).normalizedUrl

        val bodyJson = if (settings.neuralTtsProvider == NeuralTtsProvider.ELEVENLABS) {
            JSONObject().apply {
                put("text", cleanText)
                put("model_id", settings.neuralTtsModel.ifBlank { "eleven_multilingual_v2" })
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.45)
                    put("similarity_boost", 0.78)
                    put("style", 0.35)
                    put("use_speaker_boost", true)
                })
            }
        } else {
            val toneInstruction = when (languageMode) {
                LanguageMode.URDU, LanguageMode.ROMAN_URDU ->
                    "Speak warmly and naturally like a friendly Pakistani companion with clear Urdu and English pronunciation, natural conversational rhythm, and gentle pauses."
                else ->
                    "Speak warmly, calmly, and naturally like a caring, intelligent human friend with realistic conversational rhythm."
            }
            JSONObject().apply {
                put("model", settings.neuralTtsModel.ifBlank { "gpt-4o-mini-tts" })
                put("voice", settings.neuralTtsVoiceId.ifBlank { "onyx" })
                put("input", cleanText)
                put("response_format", "mp3")
                put("speed", settings.speechSpeed.coerceIn(0.5f, 2.0f).toDouble())
                if (settings.neuralTtsModel.contains("gpt-4o", ignoreCase = true)) {
                    put("instructions", toneInstruction)
                }
            }
        }

        val requestBuilder = Request.Builder()
            .url(validUrl)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(jsonMedia))

        if (settings.neuralTtsProvider == NeuralTtsProvider.ELEVENLABS) {
            requestBuilder.addHeader("xi-api-key", apiKey.trim())
            requestBuilder.addHeader("Accept", "audio/mpeg")
        } else {
            requestBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
        }

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext NeuralTtsResult.Unavailable("Neural TTS HTTP ${response.code}")
                }
                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    return@withContext NeuralTtsResult.Unavailable("Empty audio stream from Neural TTS.")
                }
                val outFile = File(cacheDir, "jarvis_tts_${UUID.randomUUID()}.mp3")
                outFile.writeBytes(bytes)
                NeuralTtsResult.AudioFile(outFile)
            }
        } catch (e: Exception) {
            NeuralTtsResult.Unavailable(e.localizedMessage ?: "Neural TTS network failure")
        }
    }
}
