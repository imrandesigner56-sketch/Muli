package com.osaf.jarvis

import com.osaf.jarvis.ai.OfflineAiEngine
import com.osaf.jarvis.ai.PersonalityEngine
import com.osaf.jarvis.ai.UserMood
import com.osaf.jarvis.security.KeystoreCryptoManager
import com.osaf.jarvis.security.UrlValidationResult
import com.osaf.jarvis.security.UrlValidator
import com.osaf.jarvis.utils.LanguageDetector
import com.osaf.jarvis.utils.LanguageMode
import com.osaf.jarvis.utils.MathEvaluator
import com.osaf.jarvis.utils.MathResult
import com.osaf.jarvis.voice.SpeechSentenceChunker
import com.osaf.jarvis.wakeword.WakeWordEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `wake word matcher detects Hey JARVIS, Urdu script, and quiet phonetic variants`() {
        val engine = WakeWordEngine()
        assertTrue(engine.matchesWakePhrase("Hey JARVIS open YouTube", "JARVIS"))
        assertTrue(engine.matchesWakePhrase("jarvis battery kitni hai", "JARVIS"))
        assertTrue(engine.matchesWakePhrase("a jarvis", "JARVIS"))
        assertTrue(engine.matchesWakePhrase("hey jarvish", "JARVIS"))
        assertTrue(engine.matchesWakePhrase("ہیلو جاروس", "JARVIS"))
        assertFalse(engine.matchesWakePhrase("open youtube please", "JARVIS"))
    }

    @Test
    fun `offline ai engine generates localized honest responses`() {
        val offline = OfflineAiEngine()
        val en = offline.buildOfflineFallbackResponse("Osaf", "JARVIS", LanguageMode.ENGLISH)
        assertTrue(en.contains("Osaf") && en.contains("offline", ignoreCase = true))

        val ru = offline.buildOfflineFallbackResponse("Osaf", "JARVIS", LanguageMode.ROMAN_URDU)
        assertTrue(ru.contains("Osaf") && ru.contains("Offline Mode", ignoreCase = true))

        val ur = offline.buildOfflineFallbackResponse("Osaf", "JARVIS", LanguageMode.URDU)
        assertTrue(ur.contains("Osaf") && ur.contains("آف لائن"))
    }

    @Test
    fun `personality engine detects moods accurately across languages`() {
        val personality = PersonalityEngine()
        assertEquals(UserMood.SAD, personality.detectMood("Aaj mood acha nahi hai, bohot tension hai"))
        assertEquals(UserMood.FRUSTRATED, personality.detectMood("Ye kaam nahi kar raha, dimagh kharab ho gaya"))
        assertEquals(UserMood.CONFUSED, personality.detectMood("Mujhe samajh nahi aa raha kya karoon"))
        assertEquals(UserMood.TIRED, personality.detectMood("Main bohot thak gaya hoon, neend aa rahi hai"))
        assertEquals(UserMood.HAPPY, personality.detectMood("Alhamdulillah aaj bohot khush hoon, zabardast din tha!"))
        assertEquals(UserMood.NEUTRAL, personality.detectMood("Open settings"))
    }

    @Test
    fun `personality engine triggers inactivity greeting after 24 hours once per day`() {
        val personality = PersonalityEngine()
        val now = 1_700_000_000_000L
        val today = personality.todayKey(now)

        assertFalse(personality.shouldGreetForInactivity(0L, "", now))
        assertFalse(personality.shouldGreetForInactivity(now - 3_600_000L, "", now))
        assertTrue(personality.shouldGreetForInactivity(now - 90_000_000L, "", now))
        assertFalse(personality.shouldGreetForInactivity(now - 90_000_000L, today, now))

        val ruGreet = personality.buildInactivityGreeting("Osaf", LanguageMode.ROMAN_URDU)
        assertTrue(ruGreet.contains("Osaf") && ruGreet.contains("khairiyat"))
    }

    @Test
    fun `personality engine extracts long-term memory facts and rotates non-repetitive greetings`() {
        val personality = PersonalityEngine()

        val projectFacts = personality.extractMemoryFacts("My project is OSAF JARVIS 2.0")
        assertTrue(projectFacts.any { it.category == "Project" && it.value == "OSAF JARVIS 2.0" })

        val ruleFacts = personality.extractMemoryFacts("Remember that I prefer concise Roman Urdu answers")
        assertTrue(ruleFacts.any { it.value.contains("Roman Urdu") })

        val r1 = personality.tryBuildConversationalReply("kya haal hai", "Osaf", "JARVIS", LanguageMode.ROMAN_URDU, UserMood.NEUTRAL)
        val r2 = personality.tryBuildConversationalReply("kya haal hai", "Osaf", "JARVIS", LanguageMode.ROMAN_URDU, UserMood.NEUTRAL)
        assertNotNull(r1)
        assertNotNull(r2)
        assertNotEquals(r1, r2)
    }

    @Test
    fun `speech sentence chunker splits streaming deltas cleanly without breaking decimals`() {
        val chunker = SpeechSentenceChunker(minChunkChars = 12)
        val c1 = chunker.appendAndExtract("The value of pi is 3.14 approx. ")
        assertEquals(1, c1.size)
        assertEquals("The value of pi is 3.14 approx.", c1[0])

        val c2 = chunker.appendAndExtract("**Great** job! اردو جملہ مکمل۔ Next part")
        assertEquals(listOf("Great job!", "اردو جملہ مکمل۔"), c2)

        assertEquals("Next part", chunker.flushRemaining())
        assertNull(chunker.flushRemaining())
    }
}
