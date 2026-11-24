package com.osaf.jarvis

import com.osaf.jarvis.ai.OfflineAiEngine
import com.osaf.jarvis.security.KeystoreCryptoManager
import com.osaf.jarvis.security.UrlValidationResult
import com.osaf.jarvis.security.UrlValidator
import com.osaf.jarvis.utils.LanguageDetector
import com.osaf.jarvis.utils.LanguageMode
import com.osaf.jarvis.utils.MathEvaluator
import com.osaf.jarvis.utils.MathResult
import com.osaf.jarvis.wakeword.WakeWordEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisCoreTests {

    @Test
    fun `language detector identifies English, Urdu, and Roman Urdu`() {
        assertEquals(LanguageMode.ENGLISH, LanguageDetector.detect("What is the weather today?"))
        assertEquals(LanguageMode.URDU, LanguageDetector.detect("آج کی تاریخ بتاؤ"))
        assertEquals(LanguageMode.ROMAN_URDU, LanguageDetector.detect("JARVIS battery kitni hai"))
        assertEquals(LanguageMode.ROMAN_URDU, LanguageDetector.detect("mujhe 10 minute ka timer lagao"))
    }

    @Test
    fun `math evaluator handles natural language and operator precedence`() {
        val r1 = MathEvaluator.evaluate("250 multiply by 40") as MathResult.Success
        assertEquals("10000", r1.formatted)

        val r2 = MathEvaluator.evaluate("15% of 2400") as MathResult.Success
        assertEquals("360", r2.formatted)

        val r3 = MathEvaluator.evaluate("120 + 80 / 4") as MathResult.Success
        assertEquals("140", r3.formatted)

        val r4 = MathEvaluator.evaluate("10 / 0")
        assertTrue(r4 is MathResult.Error)
    }

    @Test
    fun `url validator enforces https endpoints`() {
        assertTrue(UrlValidator.validateHttpsEndpoint("https://api.openai.com/v1/chat/completions") is UrlValidationResult.Valid)
        assertTrue(UrlValidator.validateHttpsEndpoint("http://api.openai.com/v1/chat/completions") is UrlValidationResult.Invalid)
        assertTrue(UrlValidator.validateHttpsEndpoint("") is UrlValidationResult.Invalid)
    }

    @Test
    fun `crypto manager masks and round-trips keys safely`() {
        val crypto = KeystoreCryptoManager()
        assertEquals("sk-••••••••cdef", crypto.maskKey("sk-1234567890abcdef"))
        assertEquals("Not configured", crypto.maskKey(""))
        val encrypted = crypto.encrypt("sk-test-secret-key")
        assertEquals("sk-test-secret-key", crypto.decrypt(encrypted))
    }

    @Test
    fun `wake word matcher detects Hey JARVIS and phonetic variants`() {
        val engine = WakeWordEngine()
        assertTrue(engine.matchesWakePhrase("Hey JARVIS open YouTube", "JARVIS"))
        assertTrue(engine.matchesWakePhrase("jarvis battery kitni hai", "JARVIS"))
        assertTrue(engine.matchesWakePhrase("a jarvis", "JARVIS"))
        assertTrue(engine.matchesWakePhrase("hey jarvish", "JARVIS"))
        assertFalse(engine.matchesWakePhrase("open youtube please", "JARVIS"))
    }

    @Test
    fun `offline ai engine generates localized honest responses`() {
        val offline = OfflineAiEngine()
        val en = offline.buildOfflineFallbackResponse("Sir", "JARVIS", LanguageMode.ENGLISH)
        assertTrue(en.contains("Sir") && en.contains("offline", ignoreCase = true))

        val ru = offline.buildOfflineFallbackResponse("Sir", "JARVIS", LanguageMode.ROMAN_URDU)
        assertTrue(ru.contains("Sir") && ru.contains("Offline Mode", ignoreCase = true))

        val ur = offline.buildOfflineFallbackResponse("Sir", "JARVIS", LanguageMode.URDU)
        assertTrue(ur.contains("Sir") && ur.contains("آف لائن"))
    }
}
