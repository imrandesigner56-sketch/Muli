package com.osaf.jarvis.ai

import com.osaf.jarvis.utils.LanguageMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class UserMood(val promptHint: String) {
    HAPPY("The user sounds happy or upbeat — match their warmth with genuine enthusiasm."),
    SAD("The user seems down, sad, or stressed — respond gently, empathetically, and caringly like a close friend."),
    FRUSTRATED("The user seems frustrated or annoyed — stay calm, reassuring, respectful, and helpful without sounding defensive."),
    CONFUSED("The user feels confused or unsure — be patient, clear, and reassuring, breaking things down simply."),
    TIRED("The user sounds tired or exhausted — keep replies brief, gentle, and easy to follow."),
    NEUTRAL("Respond naturally, warmly, and concisely like a real human companion.")
}

data class ExtractedMemoryFact(
    val category: String,
    val key: String,
    val value: String
)

class PersonalityEngine {

    private val lastVariantByKey = ConcurrentHashMap<String, Int>()

    fun detectMood(rawText: String): UserMood {
        val lower = rawText.lowercase()
        return when {
            containsAny(lower, listOf(
                "mood acha nahi", "mood off", "sad", "udass", "udaas", "dukhi", "depressed",
                "akela", "pareshan", "tension", "dil nahi lag", "ro raha", "bura lag", "not feeling good",
                "feeling down", "stressed", "unhappy", "upset", "موڈ اچھا نہیں", "اداس", "پریشان"
            )) -> UserMood.SAD

            containsAny(lower, listOf(
                "gussa", "bakwas", "frustrated", "annoying", "dimagh kharab", "kaam nahi kar raha",
                "hate this", "useless", "pagal", "irritating", "غصہ", "تنگ"
            )) -> UserMood.FRUSTRATED

            containsAny(lower, listOf(
                "samajh nahi", "confused", "kya karoon", "kya karun", "stuck", "help me decide",
                "how do i even", "سمجھ نہیں", "الجھن"
            )) -> UserMood.CONFUSED

            containsAny(lower, listOf(
                "thak gaya", "thak gya", "neend", "tired", "exhausted", "sleepy", "drained", "تھک گیا"
            )) -> UserMood.TIRED

            containsAny(lower, listOf(
                "maza aa gaya", "khush", "awesome", "zabardast", "great news", "alhamdulillah",
                "so happy", "excited", "yay", "love this", "amazing", "الحمدللہ", "خوش"
            )) -> UserMood.HAPPY

            else -> UserMood.NEUTRAL
        }
    }

    fun shouldGreetForInactivity(
        lastInteractionAt: Long,
        lastGreetingDay: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (lastInteractionAt <= 0L) return false
        val oneDayMs = 24L * 60L * 60L * 1000L
        if (nowMs - lastInteractionAt < oneDayMs) return false
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
        return today != lastGreetingDay
    }

    fun todayKey(nowMs: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
    }

    fun buildInactivityGreeting(user: String, lang: LanguageMode): String {
        return when (lang) {
            LanguageMode.URDU -> "السلام علیکم $user، کل آپ نظر نہیں آئے! سب خیریت ہے؟"
            LanguageMode.ROMAN_URDU, LanguageMode.AUTO -> "$user, kal nazar nahi aaye! Sab khairiyat?"
            LanguageMode.ENGLISH -> "Hey $user, didn't see you yesterday! Everything alright?"
        }
    }

    fun tryBuildConversationalReply(
        lower: String,
        user: String,
        assistant: String,
        lang: LanguageMode,
        mood: UserMood
    ): String? {
        // Explicit "how are you" / "kya haal hai"
        if (containsAny(lower, listOf("kya haal", "kya hal", "kaise ho", "kesay ho", "kese ho", "how are you", "how r u", "theek ho", "کیسے ہو", "کیا حال"))) {
            return when (lang) {
                LanguageMode.URDU -> pickVariant(
                    "how_ur",
                    listOf(
                        "الحمدللہ میں بالکل ٹھیک ہوں $user! آپ سنائیں، آج کیا چل رہا ہے؟",
                        "سب بڑھیا ہے $user! آپ بتائیں آج کا دن کیسا جا رہا ہے؟"
                    )
                )
                LanguageMode.ROMAN_URDU, LanguageMode.AUTO -> pickVariant(
                    "how_ru",
                    listOf(
                        "Main bilkul theek hoon $user! Tum sunao, aaj kya chal raha hai?",
                        "Sab fit hai $user! Tum batao aaj din kaisa guzar raha hai?",
                        "First class hoon $user! Tum sunao, kya haal chaal hain?"
                    )
                )
                LanguageMode.ENGLISH -> pickVariant(
                    "how_en",
                    listOf(
                        "I'm doing great, $user! What's going on with you today?",
                        "All good on my side, $user! How's your day going?",
                        "Doing awesome, $user! Tell me what you're up to."
                    )
                )
            }
        }

        // Sad / Low mood companion check-in
        if (mood == UserMood.SAD && lower.split(" ").size <= 14) {
            return when (lang) {
                LanguageMode.URDU -> "اوہ $user، کیا ہوا؟ اگر دل ہلکا کرنا چاہیں تو میں یہاں ہوں اور سن رہا ہوں۔"
                LanguageMode.ROMAN_URDU, LanguageMode.AUTO -> pickVariant(
                    "sad_ru",
                    listOf(
                        "Oh $user, kya hua? Agar share karna chaho to main sun raha hoon.",
                        "Fikar na karo $user, main yahin hoon. Batao kya baat pareshan kar rahi hai?"
                    )
                )
                LanguageMode.ENGLISH -> pickVariant(
                    "sad_en",
                    listOf(
                        "Oh $user, what happened? If you want to talk about it, I'm right here listening.",
                        "I'm sorry you're feeling that way, $user. Want to tell me what's on your mind?"
                    )
                )
            }
        }

        // "I am back" / returning
        if (containsAny(lower, listOf("i am back", "i'm back", "im back", "wapas aa gaya", "aa gaya", "back again", "واپس آ گیا"))) {
            return when (lang) {
                LanguageMode.URDU -> "خوش آمدید $user! کہاں مصروف تھے آج؟"
                LanguageMode.ROMAN_URDU -> pickVariant(
                    "back_ru",
                    listOf(
                        "Welcome back $user! Kahan busy thay?",
                        "Aakhir aa hi gaye $user! Kahan masroof thay aaj?"
                    )
                )
                else -> pickVariant(
                    "back_en",
                    listOf(
                        "Welcome back $user! Kahan busy thay?",
                        "Good to have you back, $user! What were you busy with?"
                    )
                )
            }
        }

        // Pure short greeting
        if (lower in setOf("hello", "hi", "hey", "salam", "assalam o alaikum", "assalamualaikum", "aoa", "who are you", "introduce yourself", "ہیلو", "السلام علیکم")) {
            return when (lang) {
                LanguageMode.URDU -> pickVariant(
                    "greet_ur",
                    listOf(
                        "وعلیکم السلام $user! میں $assistant ہوں، بتائیں آج کیا مدد کروں؟",
                        "جی $user، میں حاضر ہوں! کیا کام ہے آج؟"
                    )
                )
                LanguageMode.ROMAN_URDU -> pickVariant(
                    "greet_ru",
                    listOf(
                        "Haan ji $user, main sun raha hoon! Batayein kya help karoon?",
                        "Assalam o alaikum $user! Kaho aaj kya plan hai?"
                    )
                )
                else -> pickVariant(
                    "greet_en",
                    listOf(
                        "Hey $user! I'm $assistant — right here with you. What are we working on?",
                        "Hi $user! Ready whenever you are — how can I help?"
                    )
                )
            }
        }

        // Thanks
        if (containsAny(lower, listOf("shukriya", "thanks", "thank you", "thankyou", "meherbani", "jazakallah", "شکریہ"))) {
            return when (lang) {
                LanguageMode.URDU -> "کوئی بات نہیں $user! میں ہمیشہ آپ کے ساتھ ہوں۔"
                LanguageMode.ROMAN_URDU -> pickVariant(
                    "thx_ru",
                    listOf(
                        "Koi baat nahi $user! Hamesha hazir hoon.",
                        "Are ismein shukriya kaisa $user, dost kis liye hote hain!"
                    )
                )
                else -> pickVariant(
                    "thx_en",
                    listOf(
                        "Anytime, $user! Always glad to help.",
                        "You've got it, $user!"
                    )
                )
            }
        }

        // Good morning / Good night
        if (containsAny(lower, listOf("good morning", "subah bakhair", "صبح بخیر"))) {
            return if (lang == LanguageMode.URDU) "صبح بخیر $user! اللہ کرے آج کا دن بہترین گزرے۔"
            else if (lang == LanguageMode.ROMAN_URDU) "Subah bakhair $user! Umeed hai aaj ka din zabardast guzrega."
            else "Good morning, $user! Hope today brings good energy."
        }
        if (containsAny(lower, listOf("good night", "shab bakhair", "so ja", "شب بخیر"))) {
            return if (lang == LanguageMode.URDU) "شب بخیر $user! پرسکون نیند لیں، کل بات ہوگی۔"
            else if (lang == LanguageMode.ROMAN_URDU) "Shab bakhair $user! Aaram se so jayein, subah milte hain."
            else "Good night, $user! Get some solid rest."
        }

        // Bye / Stop conversation
        if (lower in setOf("bye", "goodbye", "khuda hafiz", "allah hafiz", "bas", "bass", "chalo bye", "stop talking", "خدا حافظ", "اللہ حافظ")) {
            return when (lang) {
                LanguageMode.URDU -> "اللہ حافظ $user! جب ضرورت ہو آواز دے دیجیے گا۔"
                LanguageMode.ROMAN_URDU -> "Allah hafiz $user! Jab bhi zaroorat ho, bas awaaz de dena."
                else -> "Catch you later, $user! Just say my name whenever you need me."
            }
        }

        return null
    }

    fun extractMemoryFacts(rawInput: String): List<ExtractedMemoryFact> {
        val cleaned = rawInput.trim()
        if (cleaned.length < 6) return emptyList()
        val found = mutableListOf<ExtractedMemoryFact>()

        // Explicit remember statements
        val rememberRegex = Regex(
            """(?i)^(?:please\s+)?(?:jarvis[,:\s]*)?(?:remember\s+that|remember|yaad\s+rakhna(?:\s+ke|\s+ki)?|yaad\s+rakho(?:\s+ke|\s+ki)?)\s+(.+)$"""
        )
        rememberRegex.find(cleaned)?.let { m ->
            val value = m.groupValues[1].trim()
            if (value.isNotEmpty() && !value.lowercase().contains("note")) {
                val category = when {
                    value.lowercase().contains("project") -> "Project"
                    value.lowercase().contains("always") || value.lowercase().contains("never") ||
                        value.lowercase().contains("hamesha") -> "Instruction"
                    value.lowercase().contains("like") || value.lowercase().contains("pasand") ||
                        value.lowercase().contains("favorite") || value.lowercase().contains("prefer") -> "Preference"
                    else -> "Personal"
                }
                val key = value.split(Regex("\\s+")).take(4).joinToString(" ")
                found.add(ExtractedMemoryFact(category = category, key = key, value = value))
            }
        }

        // Project declaration
        val projectRegex = Regex("""(?i)(?:my\s+project\s+is|i\s+am\s+working\s+on|mera\s+project)\s+(.+?)(?:\s+hai)?[.!]?$""")
        projectRegex.find(cleaned)?.let { m ->
            val proj = m.groupValues[1].trim()
            if (proj.length in 2..90) {
                found.add(ExtractedMemoryFact("Project", "Active Project", proj))
            }
        }

        // Name declaration
        val nameRegex = Regex("""(?i)^(?:my\s+name\s+is|call\s+me|mera\s+naam)\s+([a-zA-Z\u0600-\u06FF][a-zA-Z\u0600-\u06FF\s]{1,28})(?:\s+hai)?[.!]?$""")
        nameRegex.find(cleaned)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.length in 2..30) {
                found.add(ExtractedMemoryFact("Personal", "User Name", name))
            }
        }

        // Standing instructions ("always reply in short..." / "hamesha...")
        val instructionRegex = Regex("""(?i)^(?:always|never|hamesha|kabhi\s+nahi)\s+(.{6,100})$""")
        instructionRegex.find(cleaned)?.let { m ->
            found.add(ExtractedMemoryFact("Instruction", "Behavior Rule", m.value.trim()))
        }

        return found
    }

    private fun pickVariant(key: String, options: List<String>): String {
        if (options.isEmpty()) return ""
        if (options.size == 1) return options.first()
        val prev = lastVariantByKey[key] ?: -1
        val next = (prev + 1) % options.size
        lastVariantByKey[key] = next
        return options[next]
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }
}
