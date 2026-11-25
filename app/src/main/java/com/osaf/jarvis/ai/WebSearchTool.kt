package com.osaf.jarvis.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class WebSearchResult(
    val query: String,
    val summary: String,
    val sourceUrl: String
)

class WebSearchTool(private val httpClient: OkHttpClient) {

    suspend fun searchLiveKnowledge(query: String): WebSearchResult? = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext null

        // 1. Try DuckDuckGo Instant Answer JSON API (fast, free, zero-auth HTTPS)
        try {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val ddgReq = Request.Builder()
                .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                .header("User-Agent", "OSAF-JARVIS-Android/2.0")
                .get()
                .build()

            httpClient.newCall(ddgReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val raw = resp.body?.string().orEmpty()
                    if (raw.isNotBlank()) {
                        val json = JSONObject(raw)
                        val abstractText = json.optString("AbstractText").trim()
                        val abstractUrl = json.optString("AbstractURL").trim()
                        if (abstractText.isNotEmpty()) {
                            return@withContext WebSearchResult(
                                query = clean,
                                summary = abstractText.take(700),
                                sourceUrl = abstractUrl.ifEmpty { "https://duckduckgo.com/?q=$encoded" }
                            )
                        }
                        val answer = json.optString("Answer").trim()
                        if (answer.isNotEmpty()) {
                            return@withContext WebSearchResult(
                                query = clean,
                                summary = answer.take(500),
                                sourceUrl = "https://duckduckgo.com/?q=$encoded"
                            )
                        }
                        val related = json.optJSONArray("RelatedTopics")
                        if (related != null && related.length() > 0) {
                            val firstTopic = related.optJSONObject(0)?.optString("Text")?.trim().orEmpty()
                            if (firstTopic.isNotEmpty()) {
                                return@withContext WebSearchResult(
                                    query = clean,
                                    summary = firstTopic.take(600),
                                    sourceUrl = related.optJSONObject(0)?.optString("FirstURL")
                                        ?.ifEmpty { "https://duckduckgo.com/?q=$encoded" }
                                        ?: "https://duckduckgo.com/?q=$encoded"
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to Wikipedia summary lookup
        }

        // 2. Wikipedia OpenSearch + Summary REST fallback
        try {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val wikiSearchReq = Request.Builder()
                .url("https://en.wikipedia.org/w/api.php?action=opensearch&search=$encoded&limit=1&namespace=0&format=json")
                .header("User-Agent", "OSAF-JARVIS-Android/2.0")
                .get()
                .build()

            var title: String? = null
            httpClient.newCall(wikiSearchReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val arr = org.json.JSONArray(resp.body?.string().orEmpty())
                    val titles = arr.optJSONArray(1)
                    if (titles != null && titles.length() > 0) {
                        title = titles.optString(0)
                    }
                }
            }

            if (!title.isNullOrBlank()) {
                val slug = URLEncoder.encode(title!!.replace(" ", "_"), "UTF-8")
                val sumReq = Request.Builder()
                    .url("https://en.wikipedia.org/api/rest_v1/page/summary/$slug")
                    .header("User-Agent", "OSAF-JARVIS-Android/2.0")
                    .get()
                    .build()
                httpClient.newCall(sumReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val obj = JSONObject(resp.body?.string().orEmpty())
                        val extract = obj.optString("extract").trim()
                        if (extract.isNotEmpty()) {
                            val pageUrl = obj.optJSONObject("content_urls")
                                ?.optJSONObject("desktop")
                                ?.optString("page")
                                .orEmpty()
                            return@withContext WebSearchResult(
                                query = clean,
                                summary = extract.take(700),
                                sourceUrl = pageUrl.ifEmpty { "https://en.wikipedia.org/wiki/$slug" }
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Return null if offline or unreachable
        }

        null
    }

    fun looksLikeWebLookupQuery(cleanedQuery: String): Boolean {
        val lower = cleanedQuery.lowercase()
        val explicitTriggers = listOf(
            "web search", "search web", "internet par", "online search", "latest news",
            "taza khabar", "who is ", "what is ", "kaun hai", "kya hai", "wiki ", "wikipedia"
        )
        return explicitTriggers.any { lower.contains(it) }
    }
}
