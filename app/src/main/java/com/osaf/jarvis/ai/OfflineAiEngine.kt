package com.osaf.jarvis.ai

import com.osaf.jarvis.utils.LanguageMode

class OfflineAiEngine {

    fun buildOfflineFallbackResponse(
        userDisplayName: String,
        assistantName: String,
        language: LanguageMode,
        reason: String? = null
    ): String {
        return when (language) {
            LanguageMode.URDU -> buildString {
                append("جناب $userDisplayName، میں اس وقت آف لائن موڈ میں ہوں۔ ")
                append("میں بنیادی کمانڈز، حساب کتاب، نوٹس، ریمائنڈرز، بیٹری، فلیش لائٹ اور ایپس کھولنے میں مدد کر سکتا ہوں۔ ")
                append("مکمل AI گفتگو کے لیے آن لائن موڈ اور API کی ترتیبات فعال کریں۔")
                if (!reason.isNullOrBlank()) append("\n($reason)")
            }

            LanguageMode.ROMAN_URDU -> buildString {
                append("$userDisplayName, main is waqt Offline Mode mein hoon. ")
                append("Main offline commands, hisab (calculations), notes, reminders, battery status, flashlight aur apps open kar sakta hoon. ")
                append("Full AI conversation ke liye internet aur API settings configure karein.")
                if (!reason.isNullOrBlank()) append("\n($reason)")
            }

            else -> buildString {
                append("$userDisplayName, I am currently operating in Offline Mode. ")
                append("No local LLM is installed, so full conversational AI requires an active internet connection and configured API key. ")
                append("Offline, I can still run calculations, save notes and reminders, check battery/time, toggle the flashlight, and launch apps.")
                if (!reason.isNullOrBlank()) append("\n($reason)")
            }
        }
    }

    fun buildCapabilitiesResponse(
        userDisplayName: String,
        assistantName: String,
        language: LanguageMode
    ): String {
        return when (language) {
            LanguageMode.URDU ->
                "جناب $userDisplayName، میں $assistantName ہوں۔ میں ایپس کھول سکتا ہوں، فلیش لائٹ کنٹرول کر سکتا ہوں، بیٹری اور وقت بتا سکتا ہوں، الارم اور ٹائمر سیٹ کر سکتا ہوں، حساب کر سکتا ہوں، نوٹس محفوظ کر سکتا ہوں، اور آن لائن AI سے سوالات کے جواب دے سکتا ہوں۔"

            LanguageMode.ROMAN_URDU ->
                "$userDisplayName, main $assistantName hoon. Main YouTube/WhatsApp ya koi bhi app khol sakta hoon, flashlight aur volume control kar sakta hoon, battery aur date/time bata sakta hoon, alarm aur timer laga sakta hoon, math solve kar sakta hoon, notes/reminders save kar sakta hoon, aur online AI se madad kar sakta hoon."

            else ->
                "$userDisplayName, I am OSAF $assistantName. I can launch installed apps, control the flashlight and media volume, check battery and date/time, set alarms and timers, solve calculations, manage local notes and reminders, prepare calls or SMS with your confirmation, and connect to your configured Online AI model."
        }
    }
}
