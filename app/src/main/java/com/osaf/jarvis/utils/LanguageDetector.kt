package com.osaf.jarvis.utils

enum class LanguageMode(val code: String, val displayName: String, val bcp47: String) {
    AUTO("auto", "Auto Detect", "en-US"),
    ENGLISH("en", "English", "en-US"),
    URDU("ur", "اردو (Urdu)", "ur-PK"),
    ROMAN_URDU("ru", "Roman Urdu", "ur-PK")
}

object LanguageDetector {

    private val romanUrduKeywords = setOf(
        "kholo", "karo", "krdo", "kardo", "karna", "kitni", "kitna", "hai", "hain",
        "batao", "btado", "mujhe", "mjhe", "aaj", "kal", "subah", "shaam", "raat",
        "baje", "lagao", "laga", "par", "pe", "mein", "main", "mera", "meri", "mere",
        "apna", "kya", "kaise", "kaisa", "kaisi", "shukriya", "meherbani", "band",
        "chalu", "likho", "yaad", "dilao", "dilana", "salam", "assalam", "alaikum",
        "waqt", "tareekh", "tarikh", "awaaz", "awaz", "kam", "zyada", "ziyada",
        "zarab", "taqseem", "jama", "nafi", "feesad", "haan", "nahi", "nahin", "theek",
        "acha", "achha", "sunao", "bhejo", "milao", "phone", "baad", "pehle", "abhi",
        "jaldi", "bhai", "dost", "khuda", "hafiz", "chalo", "dikhao", "mitao", "hatao",
        "rakho", "samjhao", "tarjuma", "madad", "suno", "bolo", "bolna", "likhna"
    )

    fun detect(text: String, preferred: LanguageMode = LanguageMode.AUTO): LanguageMode {
        if (preferred != LanguageMode.AUTO) {
            return preferred
        }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return LanguageMode.ENGLISH

        // Check for Urdu / Arabic script characters
        val hasUrduScript = trimmed.any { ch ->
            ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' || ch in '\uFB50'..'\uFDFF' || ch in '\uFE70'..'\uFEFF'
        }
        if (hasUrduScript) return LanguageMode.URDU

        // Tokenize words for Roman Urdu detection
        val tokens = trimmed.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val matchCount = tokens.count { it in romanUrduKeywords }
        return if (matchCount >= 2 || (tokens.size <= 4 && matchCount >= 1)) {
            LanguageMode.ROMAN_URDU
        } else {
            LanguageMode.ENGLISH
        }
    }
}
