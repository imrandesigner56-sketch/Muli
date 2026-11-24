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
                append("ارے $userDisplayName، میں اس وقت آف لائن ہوں، اس لیے گہری گفتگو کے لیے انٹرنیٹ اور API چاہیے ہوگی۔ ")
                append("لیکن فکر نہ کریں — میں ابھی بھی آپ کے لیے ایپس کھول سکتا ہوں، حساب کتاب، نوٹس، ریمائنڈر، بیٹری اور فلیش لائٹ سب ہینڈل کر سکتا ہوں!")
                if (!reason.isNullOrBlank()) append("\n($reason)")
            }

            LanguageMode.ROMAN_URDU -> buildString {
                append("Yaar $userDisplayName, main abhi Offline Mode mein hoon is liye lambi AI baatein karne ke liye internet aur API chahiye hogi. ")
                append("Lekin tension na lein — apps kholna, flashlight, battery, alarm, timer, notes aur hisab kitab main abhi bhi kar sakta hoon!")
                if (!reason.isNullOrBlank()) append("\n($reason)")
            }

            else -> buildString {
                append("Hey $userDisplayName! I'm offline right now, so I can't reach the cloud brain for deep questions just yet. ")
                append("Still, I'm right here for you — I can open apps, do math, save notes and reminders, check your battery, set alarms, or flip on the flashlight anytime!")
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
                "جی $userDisplayName، میں $assistantName ہوں — آپ کا دوست اور اسسٹنٹ! آپ مجھ سے ایپس کھلوا سکتے ہیں، فلیش لائٹ یا والیوم کنٹرول کروا سکتے ہیں، بیٹری اور وقت پوچھ سکتے ہیں، الارم اور ٹائمر لگوا سکتے ہیں، نوٹس لکھوا سکتے ہیں، یا آن لائن AI سے کسی بھی موضوع پر بات کر سکتے ہیں۔"

            LanguageMode.ROMAN_URDU ->
                "Arre $userDisplayName, main $assistantName hoon — bilkul aapke dost ki tarah! Main YouTube ya WhatsApp khol sakta hoon, flashlight aur awaz control kar sakta hoon, alarm aur timer laga sakta hoon, notes yaad rakh sakta hoon, aur online hone par kisi bhi cheez par baat kar sakta hoon."

            else ->
                "Hey $userDisplayName! Think of me as your everyday companion. I can open your favorite apps, toggle your flashlight, check battery or time, set timers and alarms, crunch numbers, remember notes and reminders, and chat about anything when we're online!"
        }
    }
}
