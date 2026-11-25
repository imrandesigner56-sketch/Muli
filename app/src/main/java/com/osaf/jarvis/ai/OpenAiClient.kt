package com.osaf.jarvis.ai

import com.osaf.jarvis.database.ChatMessageEntity
import com.osaf.jarvis.database.JarvisSettings
import com.osaf.jarvis.database.MemoryFactEntity
import com.osaf.jarvis.database.NoteEntity
import com.osaf.jarvis.security.UrlValidationResult
import com.osaf.jarvis.security.UrlValidator
import com.osaf.jarvis.utils.LanguageMode
import com.osaf.jarvis.utils.PerformanceMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

sealed class AiStreamEvent {
    data class Delta(val chunk: String) : AiStreamEvent()
    data class Completed(val fullText: String) : AiStreamEvent()
    data class Error(val message: String) : AiStreamEvent()
}

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long
)

data class AiAttachmentContext(
    val imageBase64Jpeg: String? = null,
    val documentName: String? = null,
    val documentExcerpt: String? = null
)

private data class CachedReply(
    val text: String,
    val cachedAtMs: Long
)

class OpenAiClient(private val sharedClient: OkHttpClient = OkHttpClient()) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val responseCache = object : LinkedHashMap<String, CachedReply>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedReply>?): Boolean {
            return size > 24
        }
    }

    private fun clientWithTimeout(timeoutSeconds: Int): OkHttpClient {
        val t = timeoutSeconds.coerceIn(5, 120).toLong()
        return sharedClient.newBuilder()
            .connectTimeout(t, TimeUnit.SECONDS)
            .readTimeout(t, TimeUnit.SECONDS)
            .writeTimeout(t, TimeUnit.SECONDS)
            .build()
    }

    private fun buildSystemPrompt(
        settings: JarvisSettings,
        detectedLanguage: LanguageMode,
        mood: UserMood,
        memoryFacts: List<MemoryFactEntity>,
        sharedNotes: List<NoteEntity>,
        webContext: WebSearchResult?
    ): String {
        val langInstruction = when (detectedLanguage) {
            LanguageMode.URDU ->
                "Speak warmly, smoothly, and naturally in conversational Pakistani Urdu (اردو), addressing the user casually and respectfully as '${settings.userDisplayName}'. Keep sentences natural for spoken playback."
            LanguageMode.ROMAN_URDU ->
                "Speak warmly, naturally, and fluently in everyday Pakistani Roman Urdu (just like a supportive human friend), addressing the user as '${settings.userDisplayName}'. Keep sentences crisp and spoken-friendly."
            else ->
                "Speak warmly, naturally, and concisely like a genuinely friendly human companion (natural contractions, realistic rhythm, no stiff robotic phrasing), addressing the user as '${settings.userDisplayName}'."
        }

        val memoryBlock = if (settings.shareMemoryWithOnlineAi && memoryFacts.isNotEmpty()) {
            val facts = memoryFacts.take(12).joinToString("\n") { "- [${it.category}] ${it.factKey}: ${it.factValue}" }
            "\nKnown long-term facts about ${settings.userDisplayName}:\n$facts"
        } else {
            ""
        }

        val notesBlock = if (settings.shareNotesWithOnlineAi && sharedNotes.isNotEmpty()) {
            val topNotes = sharedNotes.take(5).joinToString("\n") { "- ${it.title}: ${it.content}" }
            "\nUser-approved local notes context:\n$topNotes"
        } else {
            ""
        }

        val webBlock = if (webContext != null) {
            "\nLive web lookup result for \"${webContext.query}\" (${webContext.sourceUrl}):\n${webContext.summary}"
        } else {
            ""
        }

        return """
            You are ${settings.assistantName} (OSAF JARVIS), an intelligent, warm, slightly witty, emotionally perceptive personal AI companion.
            Personality rules:
            - Sound like a real human companion, never a generic robotic chatbot.
            - ${mood.promptHint}
            - $langInstruction
            - Do not overuse greetings or repeat the exact same intro across turns.
            - When spoken aloud, avoid heavy markdown lists or symbols; use natural flowing sentences.
            - If asked for coding help, give clean, practical answers.
            - If a physical device capability isn't accessible over HTTPS, say so honestly.$memoryBlock$notesBlock$webBlock
        """.trimIndent()
    }

    private fun buildRequestBody(
        settings: JarvisSettings,
        userPrompt: String,
        history: List<ChatMessageEntity>,
        detectedLanguage: LanguageMode,
        mood: UserMood,
        memoryFacts: List<MemoryFactEntity>,
        sharedNotes: List<NoteEntity>,
        webContext: WebSearchResult?,
        attachment: AiAttachmentContext?,
        stream: Boolean
    ): JSONObject {
        val messages = JSONArray()
        messages.put(
            JSONObject().apply {
                put("role", "system")
                put("content", buildSystemPrompt(settings, detectedLanguage, mood, memoryFacts, sharedNotes, webContext))
            }
        )
        for (msg in history.takeLast(10)) {
            if (msg.role == "user" || msg.role == "assistant") {
                messages.put(
                    JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    }
                )
            }
        }

        val docPrefix = if (!attachment?.documentExcerpt.isNullOrBlank()) {
            "[Attached document: ${attachment?.documentName ?: "document"}]\n${attachment?.documentExcerpt}\n\nUser request: "
        } else {
            ""
        }
        val finalPrompt = "$docPrefix$userPrompt"

        if (!attachment?.imageBase64Jpeg.isNullOrBlank()) {
            val contentParts = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", finalPrompt.ifBlank { "Describe and analyze this image clearly." })
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,${attachment!!.imageBase64Jpeg}")
                    })
                })
            }
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", contentParts)
            })
        } else {
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", finalPrompt)
            })
        }

        return JSONObject().apply {
            put("model", settings.modelName)
            put("messages", messages)
            put("temperature", settings.temperature.toDouble())
            put("max_tokens", settings.maxTokens)
            put("stream", stream)
        }
    }

    fun streamChatCompletion(
        settings: JarvisSettings,
        apiKey: String,
        userPrompt: String,
        history: List<ChatMessageEntity>,
        detectedLanguage: LanguageMode,
        mood: UserMood = UserMood.NEUTRAL,
        memoryFacts: List<MemoryFactEntity> = emptyList(),
        sharedNotes: List<NoteEntity> = emptyList(),
        webContext: WebSearchResult? = null,
        attachment: AiAttachmentContext? = null
    ): Flow<AiStreamEvent> = flow {
        val urlResult = UrlValidator.validateHttpsEndpoint(settings.apiEndpoint)
        if (urlResult is UrlValidationResult.Invalid) {
            emit(AiStreamEvent.Error(urlResult.reason))
            return@flow
        }
        val endpoint = (urlResult as UrlValidationResult.Valid).normalizedUrl
        if (apiKey.isBlank()) {
            emit(AiStreamEvent.Error("API key is not configured. Add your key in Settings."))
            return@flow
        }

        // Fast LRU cache hit for identical stateless text queries (no attachments, short history)
        val cacheKey = "${settings.modelName}|${detectedLanguage.code}|${userPrompt.trim().lowercase()}"
        if (attachment == null && webContext == null && history.size <= 2) {
            val cached = synchronized(responseCache) { responseCache[cacheKey] }
            if (cached != null && (System.currentTimeMillis() - cached.cachedAtMs) < 5 * 60_000L) {
                PerformanceMetrics.recordCacheHit()
                PerformanceMetrics.recordAiFirstToken(1L)
                emit(AiStreamEvent.Delta(cached.text))
                emit(AiStreamEvent.Completed(cached.text))
                return@flow
            }
        }

        val requestStartedMs = System.currentTimeMillis()
        var firstTokenRecorded = false
        val useStreaming = settings.streamingEnabled
        val bodyJson = buildRequestBody(
            settings = settings,
            userPrompt = userPrompt,
            history = history,
            detectedLanguage = detectedLanguage,
            mood = mood,
            memoryFacts = memoryFacts,
            sharedNotes = sharedNotes,
            webContext = webContext,
            attachment = attachment,
            stream = useStreaming
        )

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .apply {
                if (useStreaming) addHeader("Accept", "text/event-stream")
            }
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        val client = clientWithTimeout(settings.timeoutSeconds)
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = mapHttpError(response.code, response.body?.string())
                    emit(AiStreamEvent.Error(errorMsg))
                    return@flow
                }

                val body = response.body
                if (body == null) {
                    emit(AiStreamEvent.Error("Empty response received from AI endpoint."))
                    return@flow
                }

                val contentType = response.header("Content-Type").orEmpty()
                if (useStreaming && contentType.contains("text/event-stream", ignoreCase = true)) {
                    val accumulated = StringBuilder()
                    val source = body.source()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload == "[DONE]") break
                        if (payload.isEmpty()) continue
                        try {
                            val obj = JSONObject(payload)
                            val choices = obj.optJSONArray("choices") ?: continue
                            if (choices.length() == 0) continue
                            val first = choices.getJSONObject(0)
                            val deltaText = first.optJSONObject("delta")?.optString("content").orEmpty()
                            if (deltaText.isNotEmpty()) {
                                if (!firstTokenRecorded) {
                                    firstTokenRecorded = true
                                    PerformanceMetrics.recordAiFirstToken(System.currentTimeMillis() - requestStartedMs)
                                }
                                accumulated.append(deltaText)
                                emit(AiStreamEvent.Delta(deltaText))
                            }
                        } catch (_: Exception) {
                            // Skip malformed SSE keepalive frame
                        }
                    }
                    val finalText = accumulated.toString().trim()
                    if (finalText.isEmpty()) {
                        emit(AiStreamEvent.Error("Stream completed without response text."))
                    } else {
                        if (attachment == null && webContext == null) {
                            synchronized(responseCache) {
                                responseCache[cacheKey] = CachedReply(finalText, System.currentTimeMillis())
                            }
                        }
                        emit(AiStreamEvent.Completed(finalText))
                    }
                } else {
                    val raw = body.string()
                    val text = extractCompletionText(raw)
                    if (text.isNullOrBlank()) {
                        emit(AiStreamEvent.Error("Could not parse completion content from endpoint response."))
                    } else {
                        PerformanceMetrics.recordAiFirstToken(System.currentTimeMillis() - requestStartedMs)
                        if (attachment == null && webContext == null) {
                            synchronized(responseCache) {
                                responseCache[cacheKey] = CachedReply(text, System.currentTimeMillis())
                            }
                        }
                        emit(AiStreamEvent.Completed(text))
                    }
                }
            }
        } catch (_: SocketTimeoutException) {
            emit(AiStreamEvent.Error("Request timed out after ${settings.timeoutSeconds}s."))
        } catch (e: IOException) {
            emit(AiStreamEvent.Error("Network error: ${e.localizedMessage ?: "connection failed"}"))
        } catch (e: Exception) {
            emit(AiStreamEvent.Error("Unexpected AI error: ${e.localizedMessage ?: "unknown"}"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun testConnection(settings: JarvisSettings, apiKey: String): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val urlResult = UrlValidator.validateHttpsEndpoint(settings.apiEndpoint)
            if (urlResult is UrlValidationResult.Invalid) {
                return@withContext ConnectionTestResult(false, urlResult.reason, 0L)
            }
            if (apiKey.isBlank()) {
                return@withContext ConnectionTestResult(false, "API key is empty.", 0L)
            }

            val payload = JSONObject().apply {
                put("model", settings.modelName)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Reply with OK.")
                    })
                })
                put("max_tokens", 8)
                put("temperature", 0.0)
                put("stream", false)
            }

            val request = Request.Builder()
                .url((urlResult as UrlValidationResult.Valid).normalizedUrl)
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            try {
                clientWithTimeout(settings.timeoutSeconds).newCall(request).execute().use { response ->
                    val elapsed = System.currentTimeMillis() - start
                    if (response.isSuccessful) {
                        val content = extractCompletionText(response.body?.string().orEmpty())
                        ConnectionTestResult(
                            success = true,
                            message = "Connected (${settings.modelName}) in ${elapsed}ms. Reply: ${content?.take(40) ?: "OK"}",
                            latencyMs = elapsed
                        )
                    } else {
                        ConnectionTestResult(
                            success = false,
                            message = mapHttpError(response.code, response.body?.string()),
                            latencyMs = elapsed
                        )
                    }
                }
            } catch (_: SocketTimeoutException) {
                ConnectionTestResult(false, "Connection timed out after ${settings.timeoutSeconds}s.", System.currentTimeMillis() - start)
            } catch (e: Exception) {
                ConnectionTestResult(false, "Connection failed: ${e.localizedMessage ?: "network error"}", System.currentTimeMillis() - start)
            }
        }

    private fun extractCompletionText(rawJson: String): String? {
        return try {
            val root = JSONObject(rawJson)
            val choices = root.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val first = choices.getJSONObject(0)
            first.optJSONObject("message")?.optString("content")?.trim()
                ?: first.optString("text").trim()
        } catch (_: Exception) {
            null
        }
    }

    private fun mapHttpError(code: Int, rawBody: String?): String {
        val apiErrorMsg = try {
            if (!rawBody.isNullOrBlank()) {
                JSONObject(rawBody).optJSONObject("error")?.optString("message")?.take(160)
            } else null
        } catch (_: Exception) {
            null
        }
        return when (code) {
            401 -> "Authentication failed (401): Invalid API key."
            403 -> "Access denied (403): Your key lacks permission for this model or endpoint."
            404 -> "Endpoint or model not found (404). Verify URL and model name."
            429 -> "Rate limit or quota exceeded (429). Try again shortly."
            in 500..599 -> "AI provider server error ($code). Please retry later."
            else -> "HTTP $code${if (!apiErrorMsg.isNullOrBlank()) ": $apiErrorMsg" else ""}"
        }
    }
}
