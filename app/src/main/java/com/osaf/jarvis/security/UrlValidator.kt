package com.osaf.jarvis.security

import java.net.URI

sealed class UrlValidationResult {
    data class Valid(val normalizedUrl: String) : UrlValidationResult()
    data class Invalid(val reason: String) : UrlValidationResult()
}

object UrlValidator {
    fun validateHttpsEndpoint(rawUrl: String): UrlValidationResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return UrlValidationResult.Invalid("API endpoint cannot be empty.")
        }
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "https") {
                return UrlValidationResult.Invalid("Only secure HTTPS endpoints are permitted.")
            }
            val host = uri.host
            if (host.isNullOrBlank()) {
                return UrlValidationResult.Invalid("Endpoint URL must include a valid host.")
            }
            UrlValidationResult.Valid(trimmed)
        } catch (e: Exception) {
            UrlValidationResult.Invalid("Malformed URL: ${e.message ?: "invalid syntax"}")
        }
    }
}
